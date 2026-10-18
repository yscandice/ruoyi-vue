package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
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
import com.ruoyi.option.domain.OptionConductItem;
import com.ruoyi.option.domain.OptionConductSync;
import com.ruoyi.option.enums.OptionConductScope;
import com.ruoyi.option.mapper.OptionConductMapper;
import com.ruoyi.option.service.IOptionConductService;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「进行」菜单 服务层实现
 * 
 * 定时同步 -> 按页面规则分栏 -> 存库（option_conduct_{scope}，按 条目键+内容哈希 去重，last_sync_id 标记当前内容）
 * -> Redis（option:conduct:{scope}）-> 前端只读
 * 
 * @author yscandice
 */
@Service
public class OptionConductServiceImpl implements IOptionConductService
{
    private static final Logger log = LoggerFactory.getLogger(OptionConductServiceImpl.class);

    /** 栏目数据 Redis 前缀（option:conduct:active 等） */
    public static final String DATA_KEY_PREFIX = "option:conduct:";

    /** 汇总 Redis 键 */
    public static final String SUMMARY_KEY = "option:conduct:summary";

    /** 运行状态 Redis 键 */
    public static final String STATUS_KEY = "option:conduct:sync:status";

    /** 同步锁 Redis 键 */
    public static final String LOCK_KEY = "option:conduct:sync:lock";

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
    private OptionConductAssembler assembler;

    @Autowired
    private OptionConductMapper conductMapper;

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
            result.put("message", "上一次进行菜单同步尚未结束");
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
                return finish(result, PAUSED, String.valueOf(feed.get("message")), null, null, null, trigger, operator,
                        start, force);
            }
        }
        catch (ServiceException e)
        {
            return finish(result, FAILED, "feed 同步失败：" + e.getMessage(), null, null, null, trigger, operator, start,
                    force);
        }

        // 2、读取最新 feed 批次
        FeedBody full = feedCacheService.get(OptionFeedConstants.GROUP_FULL);
        if (full == null)
        {
            return finish(result, FAILED, "本地没有 feed 数据，请先登录 OSM 并执行一次强制同步", null, feedResult, null,
                    trigger, operator, start, force);
        }
        JsonNode root = OptionJson.read(full.body());
        String marketDay = OptionConductAssembler.marketDay(root);
        OptionConductSync latest = conductMapper.selectLatestUpdatedSync();
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

        // 3、分栏并存库
        OptionConductSync sync = new OptionConductSync();
        sync.setFeedSyncId(full.syncId());
        sync.setFeedResult(feedResult);
        sync.setMarketDay(marketDay);
        sync.setResult(FAILED);
        sync.setMessage("处理中");
        sync.setTriggerType(trigger);
        sync.setCreateBy(operator);
        conductMapper.insertConductSync(sync);
        try
        {
            Map<OptionConductScope, List<ObjectNode>> scopes = assembler.assemble(root);
            Map<String, Integer> previous = latest == null ? new HashMap<>() : counts(latest);
            int newVersions = 0;
            List<String> changed = new ArrayList<>();
            for (Map.Entry<OptionConductScope, List<ObjectNode>> entry : scopes.entrySet())
            {
                int created = store(entry.getKey(), entry.getValue(), sync.getSyncId(), operator);
                newVersions += created;
                Integer before = previous.get(entry.getKey().getKey());
                if (created > 0 || before == null || before != entry.getValue().size())
                {
                    changed.add(entry.getKey().getKey());
                }
            }
            sync.setResult(UPDATED);
            sync.setActiveCount(scopes.get(OptionConductScope.ACTIVE).size());
            sync.setProtectionCount(scopes.get(OptionConductScope.PROTECTION).size());
            sync.setManageCount(scopes.get(OptionConductScope.MANAGE).size());
            sync.setExpiredCount(scopes.get(OptionConductScope.EXPIRED).size());
            sync.setHistoryCount(scopes.get(OptionConductScope.HISTORY).size());
            sync.setNewVersionCount(newVersions);
            sync.setChangedScopes(String.join(",", changed));
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage("同步成功");
            conductMapper.updateConductSync(sync);

            // 4、写入 Redis
            publish(sync, full.generatedAt(), scopes);
            updateStatus(UPDATED, "同步成功", sync.getSyncId());
            log.info("进行菜单同步完成：批次 {}，feed 批次 {}，交易日 {}，新增版本 {}，变化栏目 [{}]，耗时 {}ms",
                    sync.getSyncId(), full.syncId(), marketDay, newVersions, sync.getChangedScopes(), sync.getCostMs());

            result.put("result", UPDATED);
            result.put("syncId", sync.getSyncId());
            result.put("feedSyncId", full.syncId());
            result.put("feedResult", feedResult);
            result.put("marketDay", marketDay);
            result.put("counts", counts(sync));
            result.put("newVersionCount", newVersions);
            result.put("changedScopes", sync.getChangedScopes());
            result.put("costMs", sync.getCostMs());
            return result;
        }
        catch (RuntimeException e)
        {
            log.error("进行菜单同步失败", e);
            sync.setResult(FAILED);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage(StringUtils.substring("分栏/存库失败：" + e.getMessage(), 0, 500));
            conductMapper.updateConductSync(sync);
            updateStatus(FAILED, sync.getMessage(), sync.getSyncId());
            result.put("result", FAILED);
            result.put("syncId", sync.getSyncId());
            result.put("message", sync.getMessage());
            return result;
        }
    }

    /**
     * 未写入栏目时结束：强制同步或状态变化时记一条流水，定时任务重复的失败/暂停只更新 Redis 状态，避免每分钟刷流水
     */
    private Map<String, Object> finish(Map<String, Object> result, String state, String message, Long feedSyncId,
            String feedResult, String marketDay, String trigger, String operator, long start, boolean force)
    {
        Object lastState = redisCache.getCacheMapValue(STATUS_KEY, "state");
        Long syncId = null;
        if (force || !state.equals(lastState))
        {
            OptionConductSync sync = new OptionConductSync();
            sync.setFeedSyncId(feedSyncId);
            sync.setFeedResult(feedResult);
            sync.setMarketDay(marketDay);
            sync.setResult(state);
            sync.setMessage(StringUtils.substring(message, 0, 500));
            sync.setTriggerType(trigger);
            sync.setCreateBy(operator);
            conductMapper.insertConductSync(sync);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            conductMapper.updateConductSync(sync);
            syncId = sync.getSyncId();
        }
        updateStatus(state, message, syncId);
        log.warn("进行菜单同步{}：{}", state, message);
        result.put("result", state);
        result.put("message", message);
        if (syncId != null)
        {
            result.put("syncId", syncId);
        }
        return result;
    }

    /**
     * 写入一个栏目：内容未变的条目只更新 last_sync_id 与排序号，新内容插入新版本
     * 
     * @return 新增版本数
     */
    private int store(OptionConductScope scope, List<ObjectNode> items, Long syncId, String operator)
    {
        if (items.isEmpty())
        {
            return 0;
        }
        String table = scope.getTableName();
        List<OptionConductItem> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
        {
            OptionConductItem row = toRow(scope, items.get(i), i);
            rows.add(row);
            keys.add(row.getItemKey());
        }
        Map<String, Long> existing = new HashMap<>();
        for (List<String> chunk : chunks(keys.stream().distinct().toList()))
        {
            for (OptionConductItem v : conductMapper.selectVersionKeys(table, chunk))
            {
                existing.put(v.getItemKey() + "|" + v.getContentHash(), v.getVersionId());
            }
        }
        int created = 0;
        List<OptionConductItem> touched = new ArrayList<>();
        for (OptionConductItem row : rows)
        {
            String key = row.getItemKey() + "|" + row.getContentHash();
            Long versionId = existing.get(key);
            if (versionId != null)
            {
                OptionConductItem touch = new OptionConductItem();
                touch.setVersionId(versionId);
                touch.setSortNo(row.getSortNo());
                touched.add(touch);
                continue;
            }
            row.setFirstSyncId(syncId);
            row.setLastSyncId(syncId);
            row.setCreateBy(operator);
            conductMapper.insertItem(table, row);
            existing.put(key, row.getVersionId());
            created++;
        }
        for (List<OptionConductItem> chunk : chunks(touched))
        {
            conductMapper.touchItems(table, syncId, chunk, operator);
        }
        return created;
    }

    private OptionConductItem toRow(OptionConductScope scope, ObjectNode item, int sortNo)
    {
        ObjectNode stable = item.deepCopy();
        for (Iterator<String> it = stable.fieldNames(); it.hasNext();)
        {
            if (OptionFeedConstants.isVolatileItemField(it.next()))
            {
                it.remove();
            }
        }
        String payload = OptionJson.write(item);
        String hash = OptionJson.sha256(OptionJson.write(stable));
        boolean signal = scope != OptionConductScope.PROTECTION && scope != OptionConductScope.MANAGE;
        String itemKey = signal ? OptionConductAssembler.itemKey(item)
                : OptionConductAssembler.firstText(item, "id", "signalId");
        OptionConductItem row = new OptionConductItem();
        row.setItemKey(StringUtils.substring(StringUtils.isEmpty(itemKey) ? "h:" + hash : itemKey, 0, 160));
        row.setContentHash(hash);
        row.setThreadKey(StringUtils.substring(signal ? OptionConductAssembler.threadKey(item) : null, 0, 200));
        row.setSymbol(cut(item, 32, "symbol"));
        row.setSignalId(cut(item, 128, "signalId", "id"));
        row.setRecommendationId(cut(item, 128, "recommendationId"));
        row.setIntent(cut(item, 32, "intent"));
        row.setStructure(cut(item, 128, "structure", "sourceStructure"));
        String expiry = OptionConductAssembler.firstText(item, "expiryDate", "sourceExpiry");
        row.setExpiryDate(expiry == null ? null : StringUtils.substring(expiry, 0, 10));
        row.setItemState(cut(item, 64, "state", "stage", "eventKind"));
        row.setStatusCode(cut(item, 64, "statusCode", "kind", "openEventKind"));
        row.setResultKind(cut(item, 32, "resultKind"));
        long emitted = signal ? OptionConductAssembler.emittedMs(item)
                : OptionConductAssembler.parseMs(OptionConductAssembler.firstText(item, "issuedAtUtc", "openedAtUtc"), 0);
        if (emitted > 0)
        {
            // 连接时区为 GMT+8，写入即为东八区时间；UTC 列在 SQL 中减 8 小时
            row.setEmittedTime(new Date(emitted));
        }
        row.setSortNo(sortNo);
        row.setPayload(payload);
        return row;
    }

    /**
     * 写入 Redis：每个栏目一份完整响应体 + 汇总
     */
    private void publish(OptionConductSync sync, String generatedAt, Map<OptionConductScope, List<ObjectNode>> scopes)
    {
        String syncTime = DateUtils.getTime();
        ObjectNode summary = OptionJson.MAPPER.createObjectNode();
        summary.put("marketDay", sync.getMarketDay());
        summary.put("syncId", sync.getSyncId());
        summary.put("feedSyncId", sync.getFeedSyncId());
        summary.put("generatedAt", generatedAt);
        summary.put("syncTime", syncTime);
        ArrayNode list = summary.putArray("scopes");
        for (Map.Entry<OptionConductScope, List<ObjectNode>> entry : scopes.entrySet())
        {
            OptionConductScope scope = entry.getKey();
            ObjectNode body = header(scope, sync, generatedAt, syncTime);
            body.put("count", entry.getValue().size());
            body.putArray("items").addAll(entry.getValue());
            stringRedisTemplate.opsForValue().set(DATA_KEY_PREFIX + scope.getKey(), OptionJson.write(body));
            list.addObject().put("scope", scope.getKey()).put("label", scope.getLabel()).put("count",
                    entry.getValue().size());
        }
        stringRedisTemplate.opsForValue().set(SUMMARY_KEY, OptionJson.write(summary));
    }

    private static ObjectNode header(OptionConductScope scope, OptionConductSync sync, String generatedAt,
            String syncTime)
    {
        ObjectNode body = OptionJson.MAPPER.createObjectNode();
        body.put("scope", scope.getKey());
        body.put("label", scope.getLabel());
        body.put("marketDay", sync.getMarketDay());
        body.put("syncId", sync.getSyncId());
        body.put("feedSyncId", sync.getFeedSyncId());
        body.put("generatedAt", generatedAt);
        body.put("syncTime", syncTime);
        return body;
    }

    @Override
    public FeedBody getScope(String scope, String symbol)
    {
        OptionConductScope spec = requireScope(scope);
        String body = stringRedisTemplate.opsForValue().get(DATA_KEY_PREFIX + spec.getKey());
        if (body == null)
        {
            body = rebuild() ? stringRedisTemplate.opsForValue().get(DATA_KEY_PREFIX + spec.getKey()) : null;
            if (body == null)
            {
                return null;
            }
        }
        if (StringUtils.isNotEmpty(symbol))
        {
            body = filterSymbol(body, symbol.trim().toLowerCase(Locale.ROOT));
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
     * Redis 未命中：按最近一次成功批次从 5 张表回填
     * 
     * @return 是否回填成功
     */
    private boolean rebuild()
    {
        rebuildLock.lock();
        try
        {
            if (stringRedisTemplate.hasKey(SUMMARY_KEY) && stringRedisTemplate.hasKey(DATA_KEY_PREFIX + "active"))
            {
                return true;
            }
            OptionConductSync latest = conductMapper.selectLatestUpdatedSync();
            if (latest == null)
            {
                return false;
            }
            log.info("Redis 未命中进行菜单数据，从表回填批次 {}", latest.getSyncId());
            Map<OptionConductScope, List<ObjectNode>> scopes = new EnumMap<>(OptionConductScope.class);
            for (OptionConductScope scope : OptionConductScope.values())
            {
                List<ObjectNode> items = new ArrayList<>();
                for (OptionConductItem row : conductMapper.selectItemsBySync(scope.getTableName(), latest.getSyncId()))
                {
                    items.add((ObjectNode) OptionJson.read(row.getPayload()));
                }
                scopes.put(scope, items);
            }
            publish(latest, null, scopes);
            return true;
        }
        finally
        {
            rebuildLock.unlock();
        }
    }

    private static String filterSymbol(String body, String keyword)
    {
        ObjectNode root = (ObjectNode) OptionJson.read(body);
        ArrayNode filtered = OptionJson.MAPPER.createArrayNode();
        for (JsonNode item : root.path("items"))
        {
            String symbol = StringUtils.nvl(OptionConductAssembler.text(item, "symbol"), "").toLowerCase(Locale.ROOT);
            String company = StringUtils.nvl(OptionConductAssembler.text(item, "company"), "").toLowerCase(Locale.ROOT);
            if (symbol.contains(keyword) || company.contains(keyword))
            {
                filtered.add(item);
            }
        }
        root.put("count", filtered.size());
        root.set("items", filtered);
        return OptionJson.write(root);
    }

    @Override
    public List<OptionConductSync> selectSyncList(OptionConductSync sync)
    {
        return conductMapper.selectConductSyncList(sync);
    }

    @Override
    public List<OptionConductItem> selectItemList(String scope, OptionConductItem item)
    {
        return conductMapper.selectItemList(requireScope(scope).getTableName(),
                item == null ? new OptionConductItem() : item);
    }

    @Override
    public OptionConductItem selectItem(String scope, Long versionId)
    {
        return conductMapper.selectItemById(requireScope(scope).getTableName(), versionId);
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

    private static Map<String, Integer> counts(OptionConductSync sync)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(OptionConductScope.ACTIVE.getKey(), sync.getActiveCount());
        counts.put(OptionConductScope.PROTECTION.getKey(), sync.getProtectionCount());
        counts.put(OptionConductScope.MANAGE.getKey(), sync.getManageCount());
        counts.put(OptionConductScope.EXPIRED.getKey(), sync.getExpiredCount());
        counts.put(OptionConductScope.HISTORY.getKey(), sync.getHistoryCount());
        return counts;
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

    private static OptionConductScope requireScope(String scope)
    {
        OptionConductScope spec = OptionConductScope.of(scope);
        if (spec == null)
        {
            throw new ServiceException("栏目无效，可选：active、protection、manage、expired、history");
        }
        return spec;
    }
}
