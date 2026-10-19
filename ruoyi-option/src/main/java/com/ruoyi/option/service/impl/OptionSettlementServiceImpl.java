package com.ruoyi.option.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionSettlementItem;
import com.ruoyi.option.domain.OptionSettlementSync;
import com.ruoyi.option.enums.OptionSettlementKind;
import com.ruoyi.option.mapper.OptionSettlementMapper;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionSettlementService;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「结算」菜单 服务层实现
 * 
 * 定时同步 feed -> 按页面规则组装 已结算 / 结算单 / 结算统计 -> 存库（option_settlement_{kind}，按 条目键+内容哈希 去重，
 * last_sync_id 标记当前内容）-> Redis（option:settlement:{kind}）-> 前端只读
 * 
 * @author yscandice
 */
@Service
public class OptionSettlementServiceImpl implements IOptionSettlementService
{
    private static final Logger log = LoggerFactory.getLogger(OptionSettlementServiceImpl.class);

    /** 类型数据 Redis 前缀（option:settlement:record 等） */
    public static final String DATA_KEY_PREFIX = "option:settlement:";

    /** 汇总 Redis 键 */
    public static final String SUMMARY_KEY = "option:settlement:summary";

    /** 运行状态 Redis 键 */
    public static final String STATUS_KEY = "option:settlement:sync:status";

    /** 同步锁 Redis 键 */
    public static final String LOCK_KEY = "option:settlement:sync:lock";

    private static final long LOCK_SECONDS = 180;

    private static final int BATCH = 500;

    private static final String UPDATED = "UPDATED";

    private static final String UNCHANGED = "UNCHANGED";

    private static final String SKIPPED = "SKIPPED";

    private static final String PAUSED = "PAUSED";

    private static final String FAILED = "FAILED";

    private final ReentrantLock rebuildLock = new ReentrantLock();

    @Autowired
    private IOptionFeedSyncService feedSyncService;

    @Autowired
    private IOptionFeedCacheService feedCacheService;

    @Autowired
    private OptionSettlementAssembler assembler;

    @Autowired
    private OptionSettlementMapper settlementMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Object> sync(boolean force, String trigger, String operator)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, DateUtils.getTime(),
                Duration.ofSeconds(LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked))
        {
            result.put("result", SKIPPED);
            result.put("message", "上一次结算菜单同步尚未结束");
            return result;
        }
        try
        {
            return doSync(force, trigger, operator, result);
        }
        finally
        {
            stringRedisTemplate.delete(LOCK_KEY);
        }
    }

    private Map<String, Object> doSync(boolean force, String trigger, String operator, Map<String, Object> result)
    {
        long start = System.currentTimeMillis();
        // 1、拉取 feed（交易时段外返回 SKIPPED，继续使用已缓存的最新批次）
        String feedResult;
        try
        {
            Map<String, Object> feed = feedSyncService.syncFeed(force);
            feedResult = String.valueOf(feed.get("result"));
            if (PAUSED.equals(feedResult))
            {
                return finish(result, PAUSED, String.valueOf(feed.get("message")), null, null, trigger, operator, start,
                        force);
            }
        }
        catch (ServiceException e)
        {
            return finish(result, FAILED, "feed 同步失败：" + e.getMessage(), null, null, trigger, operator, start, force);
        }

        // 2、读取最新 feed 批次
        FeedBody full = feedCacheService.get(OptionFeedConstants.GROUP_FULL);
        if (full == null)
        {
            return finish(result, FAILED, "本地没有 feed 数据，请先登录 OSM 并执行一次强制同步", null, feedResult, trigger,
                    operator, start, force);
        }
        JsonNode root = OptionJson.read(full.body());
        String marketDay = OptionConductAssembler.marketDay(root);
        OptionSettlementSync latest = settlementMapper.selectLatestUpdatedSync();
        boolean same = latest != null && full.syncId() != null && full.syncId().equals(latest.getFeedSyncId())
                && marketDay.equals(latest.getMarketDay());
        if (!force && same && stringRedisTemplate.hasKey(SUMMARY_KEY))
        {
            result.put("result", UNCHANGED);
            result.put("syncId", latest.getSyncId());
            result.put("feedSyncId", full.syncId());
            result.put("feedResult", feedResult);
            updateStatus(UNCHANGED, "feed 批次与交易日未变化", latest.getSyncId());
            return result;
        }

        // 3、组装并存库
        OptionSettlementSync sync = new OptionSettlementSync();
        sync.setFeedSyncId(full.syncId());
        sync.setFeedResult(feedResult);
        sync.setMarketDay(marketDay);
        sync.setResult(FAILED);
        sync.setMessage("处理中");
        sync.setTriggerType(trigger);
        sync.setCreateBy(operator);
        settlementMapper.insertSettlementSync(sync);
        try
        {
            Map<OptionSettlementKind, List<ObjectNode>> kinds = assembler.assemble(root);
            Map<String, Integer> previous = latest == null ? new HashMap<>() : counts(latest);
            int newVersions = 0;
            List<String> changed = new ArrayList<>();
            for (Map.Entry<OptionSettlementKind, List<ObjectNode>> entry : kinds.entrySet())
            {
                int created = store(entry.getKey(), entry.getValue(), sync.getSyncId(), operator);
                newVersions += created;
                Integer before = previous.get(entry.getKey().getKey());
                if (created > 0 || before == null || before != entry.getValue().size())
                {
                    changed.add(entry.getKey().getKey());
                }
            }
            List<ObjectNode> records = kinds.get(OptionSettlementKind.RECORD);
            JsonNode stat = kinds.get(OptionSettlementKind.STAT).get(0);
            sync.setResult(UPDATED);
            sync.setRecordCount(records.size());
            sync.setMidtermCount(stat.path("midtermRecordCount").asInt());
            sync.setCardCount(kinds.get(OptionSettlementKind.CARD).size());
            sync.setStatCount(kinds.get(OptionSettlementKind.STAT).size());
            sync.setSettledCount(stat.path("settledCount").asInt());
            sync.setSettledPnlUsd(decimal(stat.get("settledPnlUsd")));
            sync.setRawPnlUsd(decimal(stat.get("rawPnlUsd")));
            sync.setProtectionCostUsd(decimal(stat.get("protectionCostUsd")));
            sync.setManageCostUsd(decimal(stat.get("manageCostUsd")));
            sync.setManageCreditUsd(decimal(stat.get("manageCreditUsd")));
            sync.setNewVersionCount(newVersions);
            sync.setChangedKinds(String.join(",", changed));
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage("同步成功");
            settlementMapper.updateSettlementSync(sync);

            // 4、写入 Redis
            publish(sync, full.generatedAt(), kinds);
            updateStatus(UPDATED, "同步成功", sync.getSyncId());
            log.info("结算菜单同步完成：批次 {}，feed 批次 {}，交易日 {}，新增版本 {}，变化类型 [{}]，耗时 {}ms",
                    sync.getSyncId(), full.syncId(), marketDay, newVersions, sync.getChangedKinds(), sync.getCostMs());

            result.put("result", UPDATED);
            result.put("syncId", sync.getSyncId());
            result.put("feedSyncId", full.syncId());
            result.put("feedResult", feedResult);
            result.put("marketDay", marketDay);
            result.put("counts", counts(sync));
            result.put("newVersionCount", newVersions);
            result.put("changedKinds", sync.getChangedKinds());
            result.put("costMs", sync.getCostMs());
            return result;
        }
        catch (RuntimeException e)
        {
            log.error("结算菜单同步失败", e);
            sync.setResult(FAILED);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage(StringUtils.substring("组装/存库失败：" + e.getMessage(), 0, 500));
            settlementMapper.updateSettlementSync(sync);
            updateStatus(FAILED, sync.getMessage(), sync.getSyncId());
            result.put("result", FAILED);
            result.put("syncId", sync.getSyncId());
            result.put("message", sync.getMessage());
            return result;
        }
    }

    /**
     * 未写入数据时结束：强制同步或状态变化时记一条流水，定时任务重复的失败/暂停只更新 Redis 状态，避免每分钟刷流水
     */
    private Map<String, Object> finish(Map<String, Object> result, String state, String message, Long feedSyncId,
            String feedResult, String trigger, String operator, long start, boolean force)
    {
        Object lastState = redisCache.getCacheMapValue(STATUS_KEY, "state");
        Long syncId = null;
        if (force || !state.equals(lastState))
        {
            OptionSettlementSync sync = new OptionSettlementSync();
            sync.setFeedSyncId(feedSyncId);
            sync.setFeedResult(feedResult);
            sync.setResult(state);
            sync.setMessage(StringUtils.substring(message, 0, 500));
            sync.setTriggerType(trigger);
            sync.setCreateBy(operator);
            settlementMapper.insertSettlementSync(sync);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            settlementMapper.updateSettlementSync(sync);
            syncId = sync.getSyncId();
        }
        updateStatus(state, message, syncId);
        log.warn("结算菜单同步{}：{}", state, message);
        result.put("result", state);
        result.put("message", message);
        if (syncId != null)
        {
            result.put("syncId", syncId);
        }
        return result;
    }

    /**
     * 写入一个类型：内容未变的条目只更新 last_sync_id 与排序号，新内容插入新版本
     * 
     * @return 新增版本数
     */
    private int store(OptionSettlementKind kind, List<ObjectNode> items, Long syncId, String operator)
    {
        if (items.isEmpty())
        {
            return 0;
        }
        String table = kind.getTableName();
        List<OptionSettlementItem> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
        {
            OptionSettlementItem row = toRow(kind, items.get(i), i);
            rows.add(row);
            keys.add(row.getItemKey());
        }
        Map<String, Long> existing = new HashMap<>();
        for (List<String> chunk : chunks(keys.stream().distinct().toList()))
        {
            for (OptionSettlementItem v : settlementMapper.selectVersionKeys(table, chunk))
            {
                existing.put(v.getItemKey() + "|" + v.getContentHash(), v.getVersionId());
            }
        }
        int created = 0;
        List<OptionSettlementItem> touched = new ArrayList<>();
        for (OptionSettlementItem row : rows)
        {
            String key = row.getItemKey() + "|" + row.getContentHash();
            Long versionId = existing.get(key);
            if (versionId != null)
            {
                OptionSettlementItem touch = new OptionSettlementItem();
                touch.setVersionId(versionId);
                touch.setSortNo(row.getSortNo());
                touched.add(touch);
                continue;
            }
            row.setFirstSyncId(syncId);
            row.setLastSyncId(syncId);
            row.setCreateBy(operator);
            settlementMapper.insertItem(table, row);
            existing.put(key, row.getVersionId());
            created++;
        }
        for (List<OptionSettlementItem> chunk : chunks(touched))
        {
            settlementMapper.touchItems(table, syncId, chunk, operator);
        }
        return created;
    }

    private OptionSettlementItem toRow(OptionSettlementKind kind, ObjectNode item, int sortNo)
    {
        String payload = OptionJson.write(item);
        String hash = OptionJson.sha256(OptionJson.write(OptionSettlementAssembler.stable(item)));
        OptionSettlementItem row = new OptionSettlementItem();
        row.setContentHash(hash);
        row.setSortNo(sortNo);
        row.setPayload(payload);
        String itemKey;
        if (kind == OptionSettlementKind.CARD)
        {
            // 结算单：signal_id 如 roll:mc_xxx|settle|expiry|2026-10-12
            itemKey = text(item, "signal_id");
            String signalId = text(item, "signal_id");
            row.setRecordType(text(item, "event"));
            row.setSymbol(cut(item.path("underlying"), 32, "symbol"));
            row.setSignalId(StringUtils.substring(signalId, 0, 128));
            row.setParentSignalId(cut(item, 128, "parent_signal_id"));
            row.setStructure(cut(item, 128, "structure"));
            String expiry = signalId == null ? null : signalId.substring(signalId.lastIndexOf('|') + 1);
            row.setExpiryDate(expiry != null && expiry.matches("^\\d{4}-\\d{2}-\\d{2}$") ? expiry : null);
            row.setStatusCode(cut(item.path("settlement"), 64, "stage"));
            row.setResultKind(cut(item.path("settlement"), 32, "reason"));
            row.setIssuedDay(OptionSettlementAssembler.monthDay(text(item, "sentAtUtc")));
            row.setSettledDay(OptionSettlementAssembler.monthDay(text(item, "emitted_at")));
            row.setEmittedTime(time(text(item, "sentAtUtc")));
            row.setSettledTime(time(text(item, "emitted_at")));
        }
        else if (kind == OptionSettlementKind.STAT)
        {
            itemKey = text(item, "id");
            row.setRecordType(OptionSettlementKind.STAT.getKey());
            row.setPnlUsd(decimal(item.get("settledPnlUsd")));
        }
        else
        {
            itemKey = OptionConductAssembler.itemKey(item);
            row.setRecordType(text(item, "recordType"));
            row.setSymbol(cut(item, 32, "symbol"));
            row.setSignalId(cut(item, 128, "signalId", "id"));
            row.setRecommendationId(cut(item, 128, "recommendationId"));
            row.setIntent(cut(item, 32, "intent"));
            row.setStructure(cut(item, 128, "structure"));
            String expiry = text(item, "expiryDate");
            row.setExpiryDate(expiry == null ? null : StringUtils.substring(expiry, 0, 10));
            row.setStatusCode(cut(item, 64, "statusCode"));
            row.setResultKind(cut(item, 32, "resultKind"));
            row.setSettledDay(StringUtils.substring(StringUtils.nvl(text(item, "settledDay"), ""), 0, 5));
            row.setIssuedDay(StringUtils.substring(StringUtils.nvl(text(item, "issuedDay"), ""), 0, 5));
            JsonNode pnl = item.get("finalNetPnlUsd");
            row.setPnlUsd(decimal(pnl != null && pnl.isNumber() ? pnl : item.get("researchPnlUsd")));
            long emitted = OptionConductAssembler.emittedMs(item);
            row.setEmittedTime(emitted > 0 ? new Date(emitted) : null);
            row.setSettledTime(time(text(item, "settledAtUtc")));
        }
        row.setItemKey(StringUtils.substring(StringUtils.isEmpty(itemKey) ? "h:" + hash : itemKey, 0, 160));
        return row;
    }

    /**
     * 写入 Redis：每个类型一份完整响应体 + 汇总（含结算统计）
     */
    private void publish(OptionSettlementSync sync, String generatedAt, Map<OptionSettlementKind, List<ObjectNode>> kinds)
    {
        String syncTime = DateUtils.getTime();
        ObjectNode summary = OptionJson.MAPPER.createObjectNode();
        summary.put("marketDay", sync.getMarketDay());
        summary.put("syncId", sync.getSyncId());
        summary.put("feedSyncId", sync.getFeedSyncId());
        summary.put("generatedAt", generatedAt);
        summary.put("syncTime", syncTime);
        ArrayNode list = summary.putArray("kinds");
        for (Map.Entry<OptionSettlementKind, List<ObjectNode>> entry : kinds.entrySet())
        {
            OptionSettlementKind kind = entry.getKey();
            ObjectNode body = header(kind, sync, generatedAt, syncTime);
            body.put("count", entry.getValue().size());
            body.putArray("items").addAll(entry.getValue());
            stringRedisTemplate.opsForValue().set(DATA_KEY_PREFIX + kind.getKey(), OptionJson.write(body));
            list.addObject().put("kind", kind.getKey()).put("label", kind.getLabel()).put("count",
                    entry.getValue().size());
        }
        List<ObjectNode> stats = kinds.get(OptionSettlementKind.STAT);
        summary.set("stats", stats == null || stats.isEmpty() ? null : stats.get(0));
        stringRedisTemplate.opsForValue().set(SUMMARY_KEY, OptionJson.write(summary));
    }

    private static ObjectNode header(OptionSettlementKind kind, OptionSettlementSync sync, String generatedAt,
            String syncTime)
    {
        ObjectNode body = OptionJson.MAPPER.createObjectNode();
        body.put("kind", kind.getKey());
        body.put("label", kind.getLabel());
        body.put("marketDay", sync.getMarketDay());
        body.put("syncId", sync.getSyncId());
        body.put("feedSyncId", sync.getFeedSyncId());
        body.put("generatedAt", generatedAt);
        body.put("syncTime", syncTime);
        return body;
    }

    @Override
    public FeedBody getKind(String kind, SettlementQuery query)
    {
        OptionSettlementKind spec = requireKind(kind);
        String body = stringRedisTemplate.opsForValue().get(DATA_KEY_PREFIX + spec.getKey());
        if (body == null)
        {
            body = rebuild() ? stringRedisTemplate.opsForValue().get(DATA_KEY_PREFIX + spec.getKey()) : null;
            if (body == null)
            {
                return null;
            }
        }
        if (query != null && !query.isEmpty())
        {
            body = filter(body, spec, query);
        }
        return new FeedBody(body, OptionJson.etag(body), null, null);
    }

    @Override
    public FeedBody getSummary()
    {
        String body = stringRedisTemplate.opsForValue().get(SUMMARY_KEY);
        if (body == null)
        {
            body = rebuild() ? stringRedisTemplate.opsForValue().get(SUMMARY_KEY) : null;
            if (body == null)
            {
                return null;
            }
        }
        ObjectNode summary = (ObjectNode) OptionJson.read(body);
        summary.set("status", OptionJson.MAPPER.valueToTree(redisCache.getCacheMap(STATUS_KEY)));
        String text = OptionJson.write(summary);
        return new FeedBody(text, OptionJson.etag(text), null, null);
    }

    /**
     * Redis 未命中：按最近一次成功批次从 3 张表回填
     * 
     * @return 是否回填成功
     */
    private boolean rebuild()
    {
        rebuildLock.lock();
        try
        {
            if (stringRedisTemplate.hasKey(SUMMARY_KEY)
                    && stringRedisTemplate.hasKey(DATA_KEY_PREFIX + OptionSettlementKind.RECORD.getKey()))
            {
                return true;
            }
            OptionSettlementSync latest = settlementMapper.selectLatestUpdatedSync();
            if (latest == null)
            {
                return false;
            }
            log.info("Redis 未命中结算菜单数据，从表回填批次 {}", latest.getSyncId());
            Map<OptionSettlementKind, List<ObjectNode>> kinds = new EnumMap<>(OptionSettlementKind.class);
            for (OptionSettlementKind kind : OptionSettlementKind.values())
            {
                List<ObjectNode> items = new ArrayList<>();
                for (OptionSettlementItem row : settlementMapper.selectItemsBySync(kind.getTableName(),
                        latest.getSyncId()))
                {
                    items.add((ObjectNode) OptionJson.read(row.getPayload()));
                }
                kinds.put(kind, items);
            }
            publish(latest, null, kinds);
            return true;
        }
        finally
        {
            rebuildLock.unlock();
        }
    }

    /**
     * 读接口过滤：symbol 作用于 record / card；range、sort 作用于 record（按结算日或发报日分组，日期倒序，同日按发报时间倒序）
     */
    private static String filter(String body, OptionSettlementKind kind, SettlementQuery query)
    {
        ObjectNode root = (ObjectNode) OptionJson.read(body);
        if (kind == OptionSettlementKind.STAT)
        {
            return body;
        }
        String keyword = StringUtils.isEmpty(query.symbol()) ? null : query.symbol().trim().toLowerCase(Locale.ROOT);
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : root.path("items"))
        {
            if (keyword == null || matches(item, kind, keyword))
            {
                items.add(item);
            }
        }
        if (kind == OptionSettlementKind.RECORD)
        {
            String sort = "issued".equals(query.sort()) ? "issued" : "settled";
            String range = StringUtils.isEmpty(query.range()) ? "all" : query.range().trim();
            String[] window = OptionSettlementAssembler.range(range, OptionConductAssembler.text(root, "marketDay"),
                    query.from(), query.to());
            List<JsonNode> kept = new ArrayList<>();
            for (JsonNode item : items)
            {
                String day = OptionSettlementAssembler.groupDay(item, sort);
                if (window == null || (!day.isEmpty() && day.compareTo(window[0]) >= 0 && day.compareTo(window[1]) <= 0))
                {
                    ((ObjectNode) item).put("groupDay", day);
                    kept.add(item);
                }
            }
            kept.sort((a, b) -> {
                int cmp = b.path("groupDay").asText().compareTo(a.path("groupDay").asText());
                return cmp != 0 ? cmp : Long.compare(b.path("sortAtMs").asLong(), a.path("sortAtMs").asLong());
            });
            items = kept;
            root.put("sort", sort);
            root.put("range", range);
            if (window == null)
            {
                root.putNull("from");
                root.putNull("to");
            }
            else
            {
                root.put("from", window[0]);
                root.put("to", window[1]);
            }
            ArrayNode days = root.putArray("days");
            for (JsonNode item : items)
            {
                String day = item.path("groupDay").asText();
                JsonNode last = days.size() == 0 ? null : days.get(days.size() - 1);
                if (last != null && day.equals(last.path("day").asText()))
                {
                    ((ObjectNode) last).put("count", last.path("count").asInt() + 1);
                }
                else
                {
                    days.addObject().put("day", day).put("count", 1);
                }
            }
        }
        root.put("count", items.size());
        root.putArray("items").addAll(items);
        return OptionJson.write(root);
    }

    private static boolean matches(JsonNode item, OptionSettlementKind kind, String keyword)
    {
        String symbol = kind == OptionSettlementKind.CARD ? OptionConductAssembler.text(item.path("underlying"), "symbol")
                : OptionConductAssembler.text(item, "symbol");
        String company = OptionConductAssembler.text(item, "company");
        return StringUtils.nvl(symbol, "").toLowerCase(Locale.ROOT).contains(keyword)
                || StringUtils.nvl(company, "").toLowerCase(Locale.ROOT).contains(keyword);
    }

    @Override
    public List<OptionSettlementSync> selectSyncList(OptionSettlementSync sync)
    {
        return settlementMapper.selectSettlementSyncList(sync);
    }

    @Override
    public List<OptionSettlementItem> selectItemList(String kind, OptionSettlementItem item)
    {
        return settlementMapper.selectItemList(requireKind(kind).getTableName(),
                item == null ? new OptionSettlementItem() : item);
    }

    @Override
    public OptionSettlementItem selectItem(String kind, Long versionId)
    {
        return settlementMapper.selectItemById(requireKind(kind).getTableName(), versionId);
    }

    private void updateStatus(String state, String message, Long syncId)
    {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", state);
        status.put("message", message);
        status.put("syncId", syncId);
        status.put("updatedAt", DateUtils.getTime());
        if (UPDATED.equals(state))
        {
            status.put("lastSuccessAt", DateUtils.getTime());
        }
        redisCache.setCacheMap(STATUS_KEY, status);
    }

    private static Map<String, Integer> counts(OptionSettlementSync sync)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(OptionSettlementKind.RECORD.getKey(), sync.getRecordCount());
        counts.put(OptionSettlementKind.CARD.getKey(), sync.getCardCount());
        counts.put(OptionSettlementKind.STAT.getKey(), sync.getStatCount());
        return counts;
    }

    private static Date time(String iso)
    {
        // 连接时区为 GMT+8，写入即为东八区时间；UTC 列在 SQL 中减 8 小时
        long ms = OptionConductAssembler.parseMs(iso, 0);
        return ms > 0 ? new Date(ms) : null;
    }

    private static BigDecimal decimal(JsonNode value)
    {
        Double number = OptionSettlementAssembler.number(value);
        return number == null ? null : BigDecimal.valueOf(number);
    }

    private static String text(JsonNode item, String field)
    {
        return OptionConductAssembler.text(item, field);
    }

    private static String cut(JsonNode item, int max, String... fields)
    {
        String value = OptionConductAssembler.firstText(item, fields);
        return value == null ? null : StringUtils.substring(value, 0, max);
    }

    private static <T> List<List<T>> chunks(List<T> list)
    {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += BATCH)
        {
            chunks.add(list.subList(i, Math.min(list.size(), i + BATCH)));
        }
        return chunks;
    }

    private static OptionSettlementKind requireKind(String kind)
    {
        OptionSettlementKind spec = OptionSettlementKind.of(kind);
        if (spec == null)
        {
            throw new ServiceException("类型无效，可选：record、card、stat");
        }
        return spec;
    }
}
