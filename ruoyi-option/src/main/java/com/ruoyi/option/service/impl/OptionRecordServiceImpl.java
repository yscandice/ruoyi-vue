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
import com.ruoyi.option.domain.OptionRecordItem;
import com.ruoyi.option.domain.OptionRecordSync;
import com.ruoyi.option.enums.OptionRecordKind;
import com.ruoyi.option.enums.OptionResource;
import com.ruoyi.option.mapper.OptionRecordMapper;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionRecordService;
import com.ruoyi.option.service.IOptionResourceService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionAccessContext;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「战绩」菜单 服务层实现
 * 
 * 定时同步 feed + decisions -> 按页面规则组装 战绩明细 / 每日结算净额 / 按标的 / 战绩统计 / 账户模拟 -> 存库
 * （option_record_{kind}，按 条目键+内容哈希 去重，last_sync_id 标记当前内容）-> Redis（option:record:{kind}）-> 前端只读
 * 
 * @author yscandice
 */
@Service
public class OptionRecordServiceImpl implements IOptionRecordService
{
    private static final Logger log = LoggerFactory.getLogger(OptionRecordServiceImpl.class);

    /** 类型数据 Redis 前缀（option:record:trade 等） */
    public static final String DATA_KEY_PREFIX = "option:record:";

    /** 汇总 Redis 键 */
    public static final String SUMMARY_KEY = "option:record:summary";

    /** 运行状态 Redis 键 */
    public static final String STATUS_KEY = "option:record:sync:status";

    /** 同步锁 Redis 键 */
    public static final String LOCK_KEY = "option:record:sync:lock";

    private static final long LOCK_SECONDS = 180;

    private static final int BATCH = 500;

    private static final String UPDATED = "UPDATED";

    private static final String UNCHANGED = "UNCHANGED";

    private static final String SKIPPED = "SKIPPED";

    private static final String PAUSED = "PAUSED";

    private static final String FAILED = "FAILED";

    private static final String EMPTY_DECISIONS = "{\"latest\":[],\"events\":[]}";

    private final ReentrantLock rebuildLock = new ReentrantLock();

    @Autowired
    private OptionDelayedReader delayedReader;

    @Autowired
    private IOptionFeedSyncService feedSyncService;

    @Autowired
    private IOptionFeedCacheService feedCacheService;

    @Autowired
    private IOptionResourceService resourceService;

    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private OptionRecordAssembler assembler;

    @Autowired
    private OptionRecordMapper recordMapper;

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
            result.put("message", "上一次战绩菜单同步尚未结束");
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
        OptionRecordSync sync = new OptionRecordSync();
        sync.setTriggerType(trigger);
        sync.setCreateBy(operator);
        // 1、拉取 feed（交易时段外返回 SKIPPED，继续使用已缓存的最新批次）
        try
        {
            Map<String, Object> feed = feedSyncService.syncFeed(force);
            sync.setFeedResult(String.valueOf(feed.get("result")));
            if (PAUSED.equals(sync.getFeedResult()))
            {
                return finish(result, sync, PAUSED, String.valueOf(feed.get("message")), start, force);
            }
        }
        catch (ServiceException e)
        {
            return finish(result, sync, FAILED, "feed 同步失败：" + e.getMessage(), start, force);
        }

        // 2、拉取 decisions（「我跟了的」口径；交易时段外 SKIPPED，使用已缓存版本）
        try
        {
            boolean missing = !force && resourceService.get(OptionResource.DECISIONS.getKey(), null) == null;
            Map<String, Object> res = resourceService.sync(OptionResource.DECISIONS.getKey(), null, force || missing,
                    trigger);
            sync.setDecisionResult(String.valueOf(res.get("result")));
        }
        catch (ServiceException e)
        {
            String state = sessionService.getCookie() == null ? PAUSED : FAILED;
            return finish(result, sync, state, "decisions 同步失败：" + e.getMessage(), start, force);
        }
        if (PAUSED.equals(sync.getDecisionResult()))
        {
            return finish(result, sync, PAUSED, "OSM 会话未登录或已过期，请重新登录后再同步", start, force);
        }

        // 3、读取最新 feed 批次与 decisions
        FeedBody full = feedCacheService.get(OptionFeedConstants.GROUP_FULL);
        if (full == null)
        {
            return finish(result, sync, FAILED, "本地没有 feed 数据，请先登录 OSM 并执行一次强制同步", start, force);
        }
        FeedBody decisions = resourceService.get(OptionResource.DECISIONS.getKey(), null);
        String decisionsBody = decisions == null ? EMPTY_DECISIONS : decisions.body();
        JsonNode root = OptionJson.read(full.body());
        String marketDay = OptionConductAssembler.marketDay(root);
        String sourceHash = OptionJson.sha256(full.syncId() + "|" + marketDay + "|" + decisionsBody);
        sync.setFeedSyncId(full.syncId());
        sync.setMarketDay(marketDay);
        sync.setSourceHash(sourceHash);
        OptionRecordSync latest = recordMapper.selectLatestUpdatedSync();
        if (!force && latest != null && sourceHash.equals(latest.getSourceHash())
                && stringRedisTemplate.hasKey(SUMMARY_KEY))
        {
            result.put("result", UNCHANGED);
            result.put("syncId", latest.getSyncId());
            result.put("feedSyncId", full.syncId());
            result.put("feedResult", sync.getFeedResult());
            updateStatus(UNCHANGED, "feed 批次、交易日与 decisions 均未变化", latest.getSyncId());
            return result;
        }

        // 4、组装并存库
        sync.setResult(FAILED);
        sync.setMessage("处理中");
        recordMapper.insertRecordSync(sync);
        try
        {
            Map<OptionRecordKind, List<ObjectNode>> kinds = assembler.assemble(root, OptionJson.read(decisionsBody));
            Map<String, Integer> previous = latest == null ? new HashMap<>() : counts(latest);
            int newVersions = 0;
            List<String> changed = new ArrayList<>();
            for (Map.Entry<OptionRecordKind, List<ObjectNode>> entry : kinds.entrySet())
            {
                int created = store(entry.getKey(), entry.getValue(), sync.getSyncId(), operator);
                newVersions += created;
                Integer before = previous.get(entry.getKey().getKey());
                if (created > 0 || before == null || before != entry.getValue().size())
                {
                    changed.add(entry.getKey().getKey());
                }
            }
            JsonNode all = kinds.get(OptionRecordKind.STAT).get(0);
            JsonNode tracking = all.path("tracking");
            sync.setResult(UPDATED);
            sync.setTradeCount(kinds.get(OptionRecordKind.TRADE).size());
            sync.setDailyCount(kinds.get(OptionRecordKind.DAILY).size());
            sync.setSymbolCount(kinds.get(OptionRecordKind.SYMBOL).size());
            sync.setStatCount(kinds.get(OptionRecordKind.STAT).size());
            sync.setAccountCount(kinds.get(OptionRecordKind.ACCOUNT).size());
            sync.setSettledCount(tracking.path("settledTotal").asInt());
            sync.setSettledPnlUsd(decimal(tracking.get("settledPnlUsd")));
            sync.setManagedPnlUsd(decimal(tracking.get("managedPnlUsd")));
            sync.setRawPnlUsd(decimal(tracking.get("rawPnlUsd")));
            sync.setWinRate(decimal(tracking.get("winRate")));
            sync.setAdoptedCount(all.path("compare").path("adoptedCount").asInt());
            sync.setNewVersionCount(newVersions);
            sync.setChangedKinds(String.join(",", changed));
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage("同步成功");
            recordMapper.updateRecordSync(sync);

            // 5、写入 Redis
            publish(sync, full.generatedAt(), kinds);
            updateStatus(UPDATED, "同步成功", sync.getSyncId());
            log.info("战绩菜单同步完成：批次 {}，feed 批次 {}，交易日 {}，新增版本 {}，变化类型 [{}]，耗时 {}ms",
                    sync.getSyncId(), full.syncId(), marketDay, newVersions, sync.getChangedKinds(), sync.getCostMs());

            result.put("result", UPDATED);
            result.put("syncId", sync.getSyncId());
            result.put("feedSyncId", full.syncId());
            result.put("feedResult", sync.getFeedResult());
            result.put("decisionResult", sync.getDecisionResult());
            result.put("marketDay", marketDay);
            result.put("counts", counts(sync));
            result.put("newVersionCount", newVersions);
            result.put("changedKinds", sync.getChangedKinds());
            result.put("costMs", sync.getCostMs());
            return result;
        }
        catch (RuntimeException e)
        {
            log.error("战绩菜单同步失败", e);
            sync.setResult(FAILED);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage(StringUtils.substring("组装/存库失败：" + e.getMessage(), 0, 500));
            recordMapper.updateRecordSync(sync);
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
    private Map<String, Object> finish(Map<String, Object> result, OptionRecordSync sync, String state,
            String message, long start, boolean force)
    {
        Object lastState = redisCache.getCacheMapValue(STATUS_KEY, "state");
        Long syncId = null;
        if (force || !state.equals(lastState))
        {
            sync.setResult(state);
            sync.setMessage(StringUtils.substring(message, 0, 500));
            recordMapper.insertRecordSync(sync);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            recordMapper.updateRecordSync(sync);
            syncId = sync.getSyncId();
        }
        updateStatus(state, message, syncId);
        log.warn("战绩菜单同步{}：{}", state, message);
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
    private int store(OptionRecordKind kind, List<ObjectNode> items, Long syncId, String operator)
    {
        if (items.isEmpty())
        {
            return 0;
        }
        String table = kind.getTableName();
        List<OptionRecordItem> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
        {
            OptionRecordItem row = toRow(kind, items.get(i), i);
            rows.add(row);
            keys.add(row.getItemKey());
        }
        Map<String, Long> existing = new HashMap<>();
        for (List<String> chunk : chunks(keys.stream().distinct().toList()))
        {
            for (OptionRecordItem v : recordMapper.selectVersionKeys(table, chunk))
            {
                existing.put(v.getItemKey() + "|" + v.getContentHash(), v.getVersionId());
            }
        }
        int created = 0;
        List<OptionRecordItem> touched = new ArrayList<>();
        for (OptionRecordItem row : rows)
        {
            String key = row.getItemKey() + "|" + row.getContentHash();
            Long versionId = existing.get(key);
            if (versionId != null)
            {
                OptionRecordItem touch = new OptionRecordItem();
                touch.setVersionId(versionId);
                touch.setSortNo(row.getSortNo());
                touched.add(touch);
                continue;
            }
            row.setFirstSyncId(syncId);
            row.setLastSyncId(syncId);
            row.setCreateBy(operator);
            recordMapper.insertItem(table, row);
            existing.put(key, row.getVersionId());
            created++;
        }
        for (List<OptionRecordItem> chunk : chunks(touched))
        {
            recordMapper.touchItems(table, syncId, chunk, operator);
        }
        return created;
    }

    private OptionRecordItem toRow(OptionRecordKind kind, ObjectNode item, int sortNo)
    {
        String payload = OptionJson.write(item);
        String hash = OptionJson.sha256(OptionJson.write(OptionSettlementAssembler.stable(item)));
        OptionRecordItem row = new OptionRecordItem();
        row.setContentHash(hash);
        row.setSortNo(sortNo);
        row.setPayload(payload);
        String itemKey = text(item, "id");
        switch (kind)
        {
            case TRADE:
                row.setRecordType("signal");
                row.setRangeScope(OptionRecordAssembler.ALL);
                row.setSymbol(cut(item, 32, "symbol"));
                row.setRecommendationId(cut(item, 128, "recommendationId"));
                row.setIntent(cut(item, 32, "intent"));
                row.setStructure(cut(item, 128, "structure"));
                String expiry = text(item, "expiryDate");
                row.setExpiryDate(expiry != null && expiry.length() >= 10 ? expiry.substring(0, 10) : null);
                row.setStatDay(cut(item, 10, "day"));
                row.setResultKind(cut(item, 32, "outcome"));
                row.setPnlUsd(decimal(item.get("pnl")));
                row.setRawPnlUsd(decimal(item.get("rawPnl")));
                row.setItemCount(1);
                JsonNode lots = item.get("adoptedLots");
                row.setAdoptedLots(lots != null && lots.isNumber() ? lots.asInt() : null);
                long settled = OptionConductAssembler.parseMs(text(item, "settledAtUtc"), 0);
                row.setSettledTime(settled > 0 ? new Date(settled) : null);
                break;
            case DAILY:
                itemKey = text(item, "day");
                row.setRecordType("day");
                row.setRangeScope(OptionRecordAssembler.ALL);
                row.setStatDay(itemKey);
                row.setPnlUsd(decimal(item.get("settledPnlUsd")));
                row.setRawPnlUsd(decimal(item.get("floatPnlUsd")));
                row.setItemCount(item.path("settledCount").asInt());
                row.setAdoptedLots(item.path("adopted").path("settledCount").asInt());
                break;
            case SYMBOL:
                row.setRecordType("symbol");
                row.setRangeScope(text(item, "scope"));
                row.setSymbol(cut(item, 32, "symbol"));
                row.setPnlUsd(decimal(item.get("pnlUsd")));
                row.setItemCount(item.path("settledGroups").asInt() + item.path("liveGroups").asInt());
                break;
            case STAT:
                row.setRecordType(text(item, "id"));
                row.setRangeScope(text(item, "scope"));
                row.setStatDay(cut(item, 10, "marketDay"));
                JsonNode tracking = item.path("tracking");
                row.setPnlUsd(decimal(tracking.get("settledPnlUsd")));
                row.setRawPnlUsd(decimal(tracking.get("rawPnlUsd")));
                row.setItemCount(tracking.isMissingNode() ? null : tracking.path("total").asInt());
                JsonNode adopted = item.path("compare").path("adoptedCount");
                row.setAdoptedLots(adopted.isNumber() ? adopted.asInt() : null);
                break;
            default:
                row.setRecordType("account");
                row.setRangeScope(OptionRecordAssembler.ALL);
                row.setPnlUsd(decimal(item.get("pnlUsd")));
                row.setItemCount(item.path("taken").asInt());
                break;
        }
        row.setItemKey(StringUtils.substring(StringUtils.isEmpty(itemKey) ? "h:" + hash : itemKey, 0, 160));
        return row;
    }

    /**
     * 写入 Redis：每个类型一份完整响应体 + 汇总（含全部/本月统计）
     */
    private void publish(OptionRecordSync sync, String generatedAt, Map<OptionRecordKind, List<ObjectNode>> kinds)
    {
        String syncTime = DateUtils.getTime();
        ObjectNode summary = OptionJson.MAPPER.createObjectNode();
        summary.put("marketDay", sync.getMarketDay());
        summary.put("syncId", sync.getSyncId());
        summary.put("feedSyncId", sync.getFeedSyncId());
        summary.put("generatedAt", generatedAt);
        summary.put("syncTime", syncTime);
        ArrayNode list = summary.putArray("kinds");
        for (Map.Entry<OptionRecordKind, List<ObjectNode>> entry : kinds.entrySet())
        {
            OptionRecordKind kind = entry.getKey();
            ObjectNode body = header(kind, sync, generatedAt, syncTime);
            body.put("count", entry.getValue().size());
            body.putArray("items").addAll(entry.getValue());
            stringRedisTemplate.opsForValue().set(DATA_KEY_PREFIX + kind.getKey(), OptionJson.write(body));
            list.addObject().put("kind", kind.getKey()).put("label", kind.getLabel()).put("count",
                    entry.getValue().size());
        }
        ObjectNode stats = summary.putObject("stats");
        for (ObjectNode stat : kinds.getOrDefault(OptionRecordKind.STAT, List.of()))
        {
            String id = text(stat, "id");
            if (OptionRecordAssembler.ALL.equals(id) || OptionRecordAssembler.MONTH.equals(id)
                    || OptionRecordAssembler.WEEK.equals(id))
            {
                ObjectNode brief = stats.putObject(id);
                brief.put("label", text(stat, "label"));
                brief.set("tracking", stat.get("tracking"));
                brief.set("summary", stat.get("summary"));
            }
        }
        stringRedisTemplate.opsForValue().set(SUMMARY_KEY, OptionJson.write(summary));
    }

    private static ObjectNode header(OptionRecordKind kind, OptionRecordSync sync, String generatedAt, String syncTime)
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
    public FeedBody getKind(String kind, RecordQuery query)
    {
        OptionRecordKind spec = requireKind(kind);
        String body = read(DATA_KEY_PREFIX + spec.getKey());
        if (body == null)
        {
            return null;
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
        String body = read(SUMMARY_KEY);
        if (body == null)
        {
            return null;
        }
        ObjectNode summary = (ObjectNode) OptionJson.read(body);
        // 盘后视图不带实时同步状态
        summary.set("status", OptionJson.MAPPER.valueToTree(
                OptionAccessContext.isDelayed() ? delayedReader.status() : redisCache.getCacheMap(STATUS_KEY)));
        String text = OptionJson.write(summary);
        return new FeedBody(text, OptionJson.etag(text), null, null);
    }

    /**
     * 读取会员数据：盘后视图只读 option:delayed:*（缺失不回源）；实时视图读 Redis，未命中时按最近一次成功批次从表回填
     */
    private String read(String key)
    {
        if (OptionAccessContext.isDelayed())
        {
            return delayedReader.read(key, () -> read(key));
        }
        String body = stringRedisTemplate.opsForValue().get(key);
        if (body == null && rebuild())
        {
            body = stringRedisTemplate.opsForValue().get(key);
        }
        return body;
    }

    /**
     * Redis 未命中：按最近一次成功批次从 5 张表回填
     * 
     * @return 是否回填成功
     */
    private boolean rebuild()
    {
        rebuildLock.lock();
        try
        {
            if (stringRedisTemplate.hasKey(SUMMARY_KEY)
                    && stringRedisTemplate.hasKey(DATA_KEY_PREFIX + OptionRecordKind.TRADE.getKey()))
            {
                return true;
            }
            OptionRecordSync latest = recordMapper.selectLatestUpdatedSync();
            if (latest == null)
            {
                return false;
            }
            log.info("Redis 未命中战绩菜单数据，从表回填批次 {}", latest.getSyncId());
            Map<OptionRecordKind, List<ObjectNode>> kinds = new EnumMap<>(OptionRecordKind.class);
            for (OptionRecordKind kind : OptionRecordKind.values())
            {
                List<ObjectNode> items = new ArrayList<>();
                for (OptionRecordItem row : recordMapper.selectItemsBySync(kind.getTableName(), latest.getSyncId()))
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
     * 读接口过滤：
     * trade  range（all/month）、scope（adopted 只留我跟了的并按组数放大）、symbol、outcome
     * daily  range、scope、granularity（day/week/month 归集，附累计；浮动盈亏仅 day 粒度）
     * symbol range（all/month 对应 symbols / monthSymbols）、symbol
     */
    private static String filter(String body, OptionRecordKind kind, RecordQuery query)
    {
        if (kind == OptionRecordKind.STAT || kind == OptionRecordKind.ACCOUNT)
        {
            return body;
        }
        ObjectNode root = (ObjectNode) OptionJson.read(body);
        String marketDay = OptionConductAssembler.text(root, "marketDay");
        String range = OptionRecordAssembler.MONTH.equals(trim(query.range())) ? OptionRecordAssembler.MONTH
                : OptionRecordAssembler.ALL;
        boolean adopted = "adopted".equals(trim(query.scope()));
        String keyword = StringUtils.isEmpty(trim(query.symbol())) ? null : trim(query.symbol()).toLowerCase(Locale.ROOT);
        List<JsonNode> items = new ArrayList<>();
        if (kind == OptionRecordKind.SYMBOL)
        {
            for (JsonNode item : root.path("items"))
            {
                if (range.equals(item.path("scope").asText()) && (keyword == null || matches(item, keyword)))
                {
                    items.add(item);
                }
            }
            root.put("range", range);
        }
        else if (kind == OptionRecordKind.TRADE)
        {
            String outcome = trim(query.outcome());
            for (JsonNode item : root.path("items"))
            {
                if (!OptionRecordAssembler.inRange(item.path("day").asText(), range, marketDay)
                        || (keyword != null && !matches(item, keyword))
                        || (!StringUtils.isEmpty(outcome) && !outcome.equalsIgnoreCase(item.path("outcome").asText())))
                {
                    continue;
                }
                JsonNode lots = item.get("adoptedLots");
                if (adopted)
                {
                    if (lots != null && lots.isNumber())
                    {
                        items.add(OptionRecordAssembler.scale(item, lots.asInt()));
                    }
                    continue;
                }
                items.add(item);
            }
            root.put("range", range);
            root.put("scope", adopted ? "adopted" : "all");
        }
        else
        {
            items = daily(root, range, adopted, trim(query.granularity()), marketDay);
            root.put("range", range);
            root.put("scope", adopted ? "adopted" : "all");
        }
        root.put("count", items.size());
        root.putArray("items").addAll(items);
        return OptionJson.write(root);
    }

    private static List<JsonNode> daily(ObjectNode root, String range, boolean adopted, String granularity,
            String marketDay)
    {
        String grain = "week".equals(granularity) || "month".equals(granularity) ? granularity : "day";
        root.put("granularity", grain);
        Map<String, ObjectNode> buckets = new LinkedHashMap<>();
        for (JsonNode day : root.path("items"))
        {
            String date = day.path("day").asText();
            if (!OptionRecordAssembler.inRange(date, range, marketDay))
            {
                continue;
            }
            JsonNode source = adopted ? day.path("adopted") : day;
            String key = OptionRecordAssembler.period(date, grain);
            ObjectNode bucket = buckets.computeIfAbsent(key, k -> {
                ObjectNode node = OptionJson.MAPPER.createObjectNode();
                node.put("key", k);
                node.put("pnl", 0);
                node.put("count", 0);
                node.put("missing", 0);
                return node;
            });
            Double pnl = OptionRecordAssembler.number(source.get("settledPnlUsd"));
            bucket.put("pnl", bucket.path("pnl").asDouble() + (pnl == null ? 0 : pnl));
            bucket.put("count", bucket.path("count").asInt() + source.path("settledCount").asInt());
            bucket.put("missing", bucket.path("missing").asInt() + source.path("missingCount").asInt());
            if ("day".equals(grain))
            {
                bucket.set("floatPnl", source.get("floatPnlUsd"));
                bucket.put("openCount", source.path("openCount").asInt());
                bucket.put("intraday", source.path("intraday").asBoolean());
            }
        }
        List<JsonNode> list = new ArrayList<>();
        double cumulative = 0;
        for (ObjectNode bucket : buckets.values())
        {
            double pnl = bucket.path("pnl").asDouble();
            cumulative += pnl;
            OptionRecordAssembler.putNumber(bucket, "pnl", bucket.path("count").asInt() == 0 ? null : pnl);
            OptionRecordAssembler.putNumber(bucket, "cumulativePnl", cumulative);
            bucket.put("settled", bucket.path("count").asInt() > 0);
            list.add(bucket);
        }
        return list;
    }

    private static boolean matches(JsonNode item, String keyword)
    {
        String symbol = OptionConductAssembler.text(item, "symbol");
        String company = OptionConductAssembler.text(item, "company");
        return StringUtils.nvl(symbol, "").toLowerCase(Locale.ROOT).contains(keyword)
                || StringUtils.nvl(company, "").toLowerCase(Locale.ROOT).contains(keyword);
    }

    @Override
    public List<OptionRecordSync> selectSyncList(OptionRecordSync sync)
    {
        return recordMapper.selectRecordSyncList(sync);
    }

    @Override
    public List<OptionRecordItem> selectItemList(String kind, OptionRecordItem item)
    {
        return recordMapper.selectItemList(requireKind(kind).getTableName(),
                item == null ? new OptionRecordItem() : item);
    }

    @Override
    public OptionRecordItem selectItem(String kind, Long versionId)
    {
        return recordMapper.selectItemById(requireKind(kind).getTableName(), versionId);
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

    private static Map<String, Integer> counts(OptionRecordSync sync)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(OptionRecordKind.TRADE.getKey(), sync.getTradeCount());
        counts.put(OptionRecordKind.DAILY.getKey(), sync.getDailyCount());
        counts.put(OptionRecordKind.SYMBOL.getKey(), sync.getSymbolCount());
        counts.put(OptionRecordKind.STAT.getKey(), sync.getStatCount());
        counts.put(OptionRecordKind.ACCOUNT.getKey(), sync.getAccountCount());
        return counts;
    }

    private static BigDecimal decimal(JsonNode value)
    {
        Double number = OptionRecordAssembler.number(value);
        return number == null ? null : BigDecimal.valueOf(number);
    }

    private static String text(JsonNode item, String field)
    {
        return OptionConductAssembler.text(item, field);
    }

    private static String trim(String text)
    {
        return text == null ? null : text.trim();
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

    private static OptionRecordKind requireKind(String kind)
    {
        OptionRecordKind spec = OptionRecordKind.of(kind);
        if (spec == null)
        {
            throw new ServiceException("类型无效，可选：trade、daily、symbol、stat、account");
        }
        return spec;
    }
}
