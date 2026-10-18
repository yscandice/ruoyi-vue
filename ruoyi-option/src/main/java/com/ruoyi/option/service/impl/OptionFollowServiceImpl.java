package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.ruoyi.option.domain.OptionFollowItem;
import com.ruoyi.option.domain.OptionFollowSync;
import com.ruoyi.option.enums.OptionFollowKind;
import com.ruoyi.option.enums.OptionResource;
import com.ruoyi.option.mapper.OptionFollowMapper;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionFollowService;
import com.ruoyi.option.service.IOptionResourceService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「跟踪」菜单 服务层实现
 * 
 * 定时同步（feed + decisions + management-choices）-> 按页面规则合并标记与系统参考估值
 * -> 存库（option_follow_{kind}，按 条目键+内容哈希 去重，last_sync_id 标记当前内容）-> Redis（option:follow:{kind}）-> 前端只读
 * 
 * @author yscandice
 */
@Service
public class OptionFollowServiceImpl implements IOptionFollowService
{
    private static final Logger log = LoggerFactory.getLogger(OptionFollowServiceImpl.class);

    /** 标记数据 Redis 前缀（option:follow:signal 等） */
    public static final String DATA_KEY_PREFIX = "option:follow:";

    /** 汇总 Redis 键 */
    public static final String SUMMARY_KEY = "option:follow:summary";

    /** 运行状态 Redis 键 */
    public static final String STATUS_KEY = "option:follow:sync:status";

    /** 同步锁 Redis 键 */
    public static final String LOCK_KEY = "option:follow:sync:lock";

    /** 合并全部标记的虚拟类型 */
    public static final String ALL = "all";

    private static final long LOCK_SECONDS = 180;

    private static final int BATCH = 500;

    private static final String UPDATED = "UPDATED";

    private static final String UNCHANGED = "UNCHANGED";

    private static final String SKIPPED = "SKIPPED";

    private static final String PAUSED = "PAUSED";

    private static final String FAILED = "FAILED";

    private static final String EMPTY_EVENTS = "{\"latest\":[],\"events\":[]}";

    private final ReentrantLock rebuildLock = new ReentrantLock();

    @Autowired
    private IOptionFeedSyncService feedSyncService;

    @Autowired
    private IOptionFeedCacheService feedCacheService;

    @Autowired
    private IOptionResourceService resourceService;

    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private OptionFollowAssembler assembler;

    @Autowired
    private OptionFollowMapper followMapper;

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
            result.put("message", "上一次跟踪菜单同步尚未结束");
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
        OptionFollowSync sync = new OptionFollowSync();
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

        // 2、拉取 decisions、management-choices（交易时段外 SKIPPED，使用已缓存版本）
        try
        {
            sync.setDecisionResult(syncResource(OptionResource.DECISIONS, force, trigger));
            sync.setChoiceResult(syncResource(OptionResource.MANAGEMENT_CHOICES, force, trigger));
        }
        catch (ServiceException e)
        {
            String state = sessionService.getCookie() == null ? PAUSED : FAILED;
            return finish(result, sync, state, "标记接口同步失败：" + e.getMessage(), start, force);
        }
        if (PAUSED.equals(sync.getDecisionResult()) || PAUSED.equals(sync.getChoiceResult()))
        {
            return finish(result, sync, PAUSED, "OSM 会话未登录或已过期，请重新登录后再同步", start, force);
        }

        // 3、读取最新 feed 批次与标记接口内容
        FeedBody full = feedCacheService.get(OptionFeedConstants.GROUP_FULL);
        if (full == null)
        {
            return finish(result, sync, FAILED, "本地没有 feed 数据，请先登录 OSM 并执行一次强制同步", start, force);
        }
        FeedBody decisions = resourceService.get(OptionResource.DECISIONS.getKey(), null);
        if (decisions == null)
        {
            return finish(result, sync, FAILED, "本地没有 decisions 数据，请先登录 OSM 并执行一次强制同步", start, force);
        }
        FeedBody choices = resourceService.get(OptionResource.MANAGEMENT_CHOICES.getKey(), null);
        String choicesBody = choices == null ? EMPTY_EVENTS : choices.body();
        JsonNode root = OptionJson.read(full.body());
        String marketDay = OptionConductAssembler.marketDay(root);
        String sourceHash = OptionJson.sha256(full.syncId() + "|" + marketDay + "|" + decisions.body() + "|"
                + choicesBody);
        sync.setFeedSyncId(full.syncId());
        sync.setMarketDay(marketDay);
        sync.setSourceHash(sourceHash);
        OptionFollowSync latest = followMapper.selectLatestUpdatedSync();
        if (!force && latest != null && sourceHash.equals(latest.getSourceHash())
                && stringRedisTemplate.hasKey(SUMMARY_KEY))
        {
            result.put("result", UNCHANGED);
            result.put("syncId", latest.getSyncId());
            result.put("feedSyncId", full.syncId());
            updateStatus(UNCHANGED, "feed 批次、交易日与标记记录均未变化", latest.getSyncId());
            return result;
        }

        // 4、组装标记并存库
        sync.setResult(FAILED);
        sync.setMessage("处理中");
        followMapper.insertFollowSync(sync);
        try
        {
            Map<OptionFollowKind, List<ObjectNode>> kinds = assembler.assemble(root, OptionJson.read(decisions.body()),
                    OptionJson.read(choicesBody));
            Map<String, Integer> previous = latest == null ? new HashMap<>() : counts(latest);
            int newVersions = 0;
            List<String> changed = new ArrayList<>();
            for (Map.Entry<OptionFollowKind, List<ObjectNode>> entry : kinds.entrySet())
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
            sync.setSignalCount(kinds.get(OptionFollowKind.SIGNAL).size());
            sync.setMidtermCount(kinds.get(OptionFollowKind.MIDTERM).size());
            sync.setBuyerCount(kinds.get(OptionFollowKind.BUYER).size());
            sync.setManageCount(kinds.get(OptionFollowKind.MANAGE).size());
            sync.setNewVersionCount(newVersions);
            sync.setChangedKinds(String.join(",", changed));
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage("同步成功");
            followMapper.updateFollowSync(sync);

            // 5、写入 Redis
            publish(sync, full.generatedAt(), kinds);
            updateStatus(UPDATED, "同步成功", sync.getSyncId());
            log.info("跟踪菜单同步完成：批次 {}，feed 批次 {}，交易日 {}，标记 {}，新增版本 {}，耗时 {}ms", sync.getSyncId(),
                    full.syncId(), marketDay, counts(sync), newVersions, sync.getCostMs());

            result.put("result", UPDATED);
            result.put("syncId", sync.getSyncId());
            result.put("feedSyncId", full.syncId());
            result.put("feedResult", sync.getFeedResult());
            result.put("decisionResult", sync.getDecisionResult());
            result.put("choiceResult", sync.getChoiceResult());
            result.put("marketDay", marketDay);
            result.put("counts", counts(sync));
            result.put("newVersionCount", newVersions);
            result.put("changedKinds", sync.getChangedKinds());
            result.put("costMs", sync.getCostMs());
            return result;
        }
        catch (RuntimeException e)
        {
            log.error("跟踪菜单同步失败", e);
            sync.setResult(FAILED);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage(StringUtils.substring("组装/存库失败：" + e.getMessage(), 0, 500));
            followMapper.updateFollowSync(sync);
            updateStatus(FAILED, sync.getMessage(), sync.getSyncId());
            result.put("result", FAILED);
            result.put("syncId", sync.getSyncId());
            result.put("message", sync.getMessage());
            return result;
        }
    }

    /**
     * 同步一个标记接口（复用 option_res_* 版本表与 Redis 缓存）
     * 
     * @return 同步结果 UPDATED/UNCHANGED/SKIPPED/PAUSED
     */
    private String syncResource(OptionResource resource, boolean force, String trigger)
    {
        // 本地尚无该接口数据时（首次部署）忽略交易时段拉取一次，避免定时任务因缺数据持续失败
        boolean missing = !force && resourceService.get(resource.getKey(), null) == null;
        Map<String, Object> res = resourceService.sync(resource.getKey(), null, force || missing, trigger);
        return String.valueOf(res.get("result"));
    }

    /**
     * 未写入标记时结束：强制同步或状态变化时记一条流水，定时任务重复的失败/暂停只更新 Redis 状态，避免每分钟刷流水
     */
    private Map<String, Object> finish(Map<String, Object> result, OptionFollowSync sync, String state,
            String message, long start, boolean force)
    {
        Object lastState = redisCache.getCacheMapValue(STATUS_KEY, "state");
        Long syncId = null;
        if (force || !state.equals(lastState))
        {
            sync.setResult(state);
            sync.setMessage(StringUtils.substring(message, 0, 500));
            followMapper.insertFollowSync(sync);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            followMapper.updateFollowSync(sync);
            syncId = sync.getSyncId();
        }
        updateStatus(state, message, syncId);
        log.warn("跟踪菜单同步{}：{}", state, message);
        result.put("result", state);
        result.put("message", message);
        if (syncId != null)
        {
            result.put("syncId", syncId);
        }
        return result;
    }

    /**
     * 写入一类标记：内容未变的条目只更新 last_sync_id 与排序号，新内容插入新版本
     * 
     * @return 新增版本数
     */
    private int store(OptionFollowKind kind, List<ObjectNode> items, Long syncId, String operator)
    {
        if (items.isEmpty())
        {
            return 0;
        }
        String table = kind.getTableName();
        List<OptionFollowItem> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
        {
            OptionFollowItem row = toRow(kind, items.get(i), i);
            rows.add(row);
            keys.add(row.getItemKey());
        }
        Map<String, Long> existing = new HashMap<>();
        for (List<String> chunk : chunks(keys.stream().distinct().toList()))
        {
            for (OptionFollowItem v : followMapper.selectVersionKeys(table, chunk))
            {
                existing.put(v.getItemKey() + "|" + v.getContentHash(), v.getVersionId());
            }
        }
        int created = 0;
        List<OptionFollowItem> touched = new ArrayList<>();
        for (OptionFollowItem row : rows)
        {
            String key = row.getItemKey() + "|" + row.getContentHash();
            Long versionId = existing.get(key);
            if (versionId != null)
            {
                OptionFollowItem touch = new OptionFollowItem();
                touch.setVersionId(versionId);
                touch.setSortNo(row.getSortNo());
                touched.add(touch);
                continue;
            }
            row.setFirstSyncId(syncId);
            row.setLastSyncId(syncId);
            row.setCreateBy(operator);
            followMapper.insertItem(table, row);
            existing.put(key, row.getVersionId());
            created++;
        }
        for (List<OptionFollowItem> chunk : chunks(touched))
        {
            followMapper.touchItems(table, syncId, chunk, operator);
        }
        return created;
    }

    private OptionFollowItem toRow(OptionFollowKind kind, ObjectNode item, int sortNo)
    {
        String payload = OptionJson.write(item);
        String hash = OptionJson.sha256(OptionJson.write(stable(item)));
        String itemKey = kind == OptionFollowKind.SIGNAL ? OptionFollowAssembler.str(item, "eventId")
                : OptionFollowAssembler.str(item, "cardId");
        OptionFollowItem row = new OptionFollowItem();
        row.setItemKey(StringUtils.substring(StringUtils.isEmpty(itemKey) ? "h:" + hash : itemKey, 0, 160));
        row.setContentHash(hash);
        row.setCardKind(kind.getCardKind());
        row.setCardId(cut(item, "cardId", 128));
        row.setEventId(cut(item, "eventId", 128));
        row.setSymbol(cut(item, "symbol", 32));
        row.setSignalId(cut(item, "signalId", 128));
        row.setRecommendationId(cut(item, "recommendationId", 128));
        row.setDecisionType(cut(item, "decisionType", 32));
        row.setRawChoice(cut(item, kind == OptionFollowKind.SIGNAL ? "decisionType" : "rawChoice", 32));
        row.setAdoptMode(cut(item, "adoptMode", 16));
        JsonNode quantity = item.get("quantity");
        row.setQuantity(quantity != null && quantity.isNumber() ? quantity.asInt() : null);
        row.setSkipReason(cut(item, "skipReason", 64));
        row.setStructure(cut(item, "structure", 128));
        row.setExpiryDate(cut(item, "expiryDate", 10));
        row.setCategory(cut(item, "category", 16));
        row.setItemState(cut(item, "state", 64));
        row.setFloatingPnl(cut(item, "floatingPnl", 32));
        row.setSettledDay(cut(item, "settledDay", 5));
        long marked = OptionConductAssembler.parseMs(OptionFollowAssembler.str(item, "occurredAt"), 0);
        if (marked > 0)
        {
            // 连接时区为 GMT+8，写入即为东八区时间；UTC 列在 SQL 中减 8 小时
            row.setMarkedTime(new Date(marked));
        }
        row.setSortNo(sortNo);
        row.setPayload(payload);
        return row;
    }

    /**
     * 去掉易变时间戳后的内容（顶层与来源卡片 signal / card 一层），用于判断内容是否变化
     */
    private static ObjectNode stable(ObjectNode item)
    {
        ObjectNode copy = item.deepCopy();
        strip(copy);
        for (String child : new String[] { "signal", "card" })
        {
            JsonNode node = copy.get(child);
            if (node instanceof ObjectNode obj)
            {
                strip(obj);
            }
        }
        return copy;
    }

    private static void strip(ObjectNode node)
    {
        for (Iterator<String> it = node.fieldNames(); it.hasNext();)
        {
            if (OptionFeedConstants.isVolatileItemField(it.next()))
            {
                it.remove();
            }
        }
    }

    /**
     * 写入 Redis：每类标记一份完整响应体 + 汇总
     */
    private void publish(OptionFollowSync sync, String generatedAt, Map<OptionFollowKind, List<ObjectNode>> kinds)
    {
        String syncTime = DateUtils.getTime();
        ObjectNode summary = OptionJson.MAPPER.createObjectNode();
        summary.put("marketDay", sync.getMarketDay());
        summary.put("syncId", sync.getSyncId());
        summary.put("feedSyncId", sync.getFeedSyncId());
        summary.put("generatedAt", generatedAt);
        summary.put("syncTime", syncTime);
        ArrayNode list = summary.putArray("kinds");
        for (Map.Entry<OptionFollowKind, List<ObjectNode>> entry : kinds.entrySet())
        {
            OptionFollowKind kind = entry.getKey();
            ObjectNode body = header(kind.getKey(), kind.getLabel(), sync, generatedAt, syncTime);
            body.put("count", entry.getValue().size());
            body.putArray("items").addAll(entry.getValue());
            stringRedisTemplate.opsForValue().set(DATA_KEY_PREFIX + kind.getKey(), OptionJson.write(body));
            list.addObject().put("kind", kind.getKey()).put("label", kind.getLabel()).put("count",
                    entry.getValue().size());
        }
        summary.setAll(OptionFollowAssembler.stats(kinds));
        stringRedisTemplate.opsForValue().set(SUMMARY_KEY, OptionJson.write(summary));
    }

    private static ObjectNode header(String kind, String label, OptionFollowSync sync, String generatedAt,
            String syncTime)
    {
        ObjectNode body = OptionJson.MAPPER.createObjectNode();
        body.put("kind", kind);
        body.put("label", label);
        body.put("marketDay", sync.getMarketDay());
        body.put("syncId", sync.getSyncId());
        body.put("feedSyncId", sync.getFeedSyncId());
        body.put("generatedAt", generatedAt);
        body.put("syncTime", syncTime);
        return body;
    }

    @Override
    public FeedBody getKind(String kind, String symbol, String mark)
    {
        boolean all = ALL.equals(kind);
        OptionFollowKind spec = all ? null : requireKind(kind);
        ObjectNode root;
        if (all)
        {
            root = mergeAll();
            if (root == null)
            {
                return null;
            }
        }
        else
        {
            String body = read(DATA_KEY_PREFIX + spec.getKey());
            if (body == null)
            {
                return null;
            }
            root = (ObjectNode) OptionJson.read(body);
        }
        String keyword = StringUtils.isEmpty(symbol) ? null : symbol.trim().toLowerCase(Locale.ROOT);
        String type = StringUtils.isEmpty(mark) ? null : mark.trim().toUpperCase(Locale.ROOT);
        if (keyword != null || type != null)
        {
            ArrayNode filtered = OptionJson.MAPPER.createArrayNode();
            for (JsonNode item : root.path("items"))
            {
                String s = OptionFollowAssembler.str(item, "symbol").toLowerCase(Locale.ROOT);
                if ((keyword == null || s.contains(keyword))
                        && (type == null || type.equals(OptionFollowAssembler.str(item, "decisionType"))))
                {
                    filtered.add(item);
                }
            }
            root.put("count", filtered.size());
            root.set("items", filtered);
        }
        String text = OptionJson.write(root);
        return new FeedBody(text, OptionJson.etag(text), null, null);
    }

    /**
     * 合并四类标记（页面列表顺序：按发报/标记时间倒序）
     */
    private ObjectNode mergeAll()
    {
        String summaryBody = read(SUMMARY_KEY);
        if (summaryBody == null)
        {
            return null;
        }
        JsonNode summary = OptionJson.read(summaryBody);
        List<JsonNode> items = new ArrayList<>();
        for (OptionFollowKind kind : OptionFollowKind.values())
        {
            String body = read(DATA_KEY_PREFIX + kind.getKey());
            if (body != null)
            {
                OptionJson.read(body).path("items").forEach(items::add);
            }
        }
        items.sort(Comparator.comparingLong((JsonNode n) -> n.path("sortAt").asLong(0)).reversed());
        ObjectNode root = OptionJson.MAPPER.createObjectNode();
        root.put("kind", ALL);
        root.put("label", "全部");
        for (String field : new String[] { "marketDay", "syncId", "feedSyncId", "generatedAt", "syncTime" })
        {
            root.set(field, summary.get(field));
        }
        root.put("count", items.size());
        root.putArray("items").addAll(items);
        return root;
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
        summary.set("status", OptionJson.MAPPER.valueToTree(redisCache.getCacheMap(STATUS_KEY)));
        String text = OptionJson.write(summary);
        return new FeedBody(text, OptionJson.etag(text), null, null);
    }

    /**
     * 读取 Redis，未命中时按最近一次成功批次从表回填
     */
    private String read(String key)
    {
        String body = stringRedisTemplate.opsForValue().get(key);
        if (body == null && rebuild())
        {
            body = stringRedisTemplate.opsForValue().get(key);
        }
        return body;
    }

    /**
     * Redis 未命中：按最近一次成功批次从 4 张表回填
     * 
     * @return 是否回填成功
     */
    private boolean rebuild()
    {
        rebuildLock.lock();
        try
        {
            if (stringRedisTemplate.hasKey(SUMMARY_KEY) && stringRedisTemplate.hasKey(DATA_KEY_PREFIX + "signal"))
            {
                return true;
            }
            OptionFollowSync latest = followMapper.selectLatestUpdatedSync();
            if (latest == null)
            {
                return false;
            }
            log.info("Redis 未命中跟踪菜单数据，从表回填批次 {}", latest.getSyncId());
            Map<OptionFollowKind, List<ObjectNode>> kinds = new EnumMap<>(OptionFollowKind.class);
            for (OptionFollowKind kind : OptionFollowKind.values())
            {
                List<ObjectNode> items = new ArrayList<>();
                for (OptionFollowItem row : followMapper.selectItemsBySync(kind.getTableName(), latest.getSyncId()))
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

    @Override
    public List<OptionFollowSync> selectSyncList(OptionFollowSync sync)
    {
        return followMapper.selectFollowSyncList(sync);
    }

    @Override
    public List<OptionFollowItem> selectItemList(String kind, OptionFollowItem item)
    {
        return followMapper.selectItemList(requireKind(kind).getTableName(),
                item == null ? new OptionFollowItem() : item);
    }

    @Override
    public OptionFollowItem selectItem(String kind, Long versionId)
    {
        return followMapper.selectItemById(requireKind(kind).getTableName(), versionId);
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

    private static Map<String, Integer> counts(OptionFollowSync sync)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(OptionFollowKind.SIGNAL.getKey(), sync.getSignalCount());
        counts.put(OptionFollowKind.MIDTERM.getKey(), sync.getMidtermCount());
        counts.put(OptionFollowKind.BUYER.getKey(), sync.getBuyerCount());
        counts.put(OptionFollowKind.MANAGE.getKey(), sync.getManageCount());
        return counts;
    }

    private static String cut(JsonNode item, String field, int max)
    {
        JsonNode value = item.get(field);
        if (value == null || value.isNull() || value.isContainerNode() || value.asText().isEmpty())
        {
            return null;
        }
        return StringUtils.substring(value.asText(), 0, max);
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

    private static OptionFollowKind requireKind(String kind)
    {
        OptionFollowKind spec = OptionFollowKind.of(kind);
        if (spec == null)
        {
            throw new ServiceException("标记类型无效，可选：signal、midterm、buyer、manage、all");
        }
        return spec;
    }
}
