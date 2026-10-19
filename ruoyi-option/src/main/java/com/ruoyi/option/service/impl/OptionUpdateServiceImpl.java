package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.util.ArrayList;
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
import com.ruoyi.option.client.OsmClient;
import com.ruoyi.option.domain.OptionUpdateItem;
import com.ruoyi.option.domain.OptionUpdateSync;
import com.ruoyi.option.enums.OptionUpdateKind;
import com.ruoyi.option.mapper.OptionUpdateMapper;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionResourceService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.service.IOptionUpdateService;
import com.ruoyi.option.utils.OptionHtml;
import com.ruoyi.option.utils.OptionHtml.Node;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「更新」菜单 服务层实现
 * 
 * 更新页面调用的会员 API 只有 /api/member/updates（阅读进度，已由 OptionResource 同步到 option_res_update），
 * 更新文章列表随 /member/updates 页面服务端渲染下发。定时抓取该页面 -> 解析为页头与更新条目
 * -> 存库（option_update_{kind}，按 条目键+内容哈希 去重，last_sync_id 标记当前内容）
 * -> Redis（option:update:{kind} 列表、option:update:item:{kind} 单条、option:update:summary 汇总）-> 前端只读
 * 
 * @author yscandice
 */
@Service
public class OptionUpdateServiceImpl implements IOptionUpdateService
{
    private static final Logger log = LoggerFactory.getLogger(OptionUpdateServiceImpl.class);

    /** 类型列表 Redis 前缀（option:update:entry 等） */
    public static final String DATA_KEY_PREFIX = "option:update:";

    /** 单条完整内容 Redis Hash 前缀（option:update:item:entry，field 为条目键） */
    public static final String ITEM_KEY_PREFIX = "option:update:item:";

    /** 汇总 Redis 键 */
    public static final String SUMMARY_KEY = "option:update:summary";

    /** 运行状态 Redis 键 */
    public static final String STATUS_KEY = "option:update:sync:status";

    /** 同步锁 Redis 键 */
    public static final String LOCK_KEY = "option:update:sync:lock";

    /** 阅读进度接口（option_res_update） */
    private static final String UPDATES_RESOURCE = "updates";

    private static final long LOCK_SECONDS = 120;

    /** 汇总中附带的最新条目数 */
    private static final int LATEST_SIZE = 5;

    private static final int BATCH = 500;

    private static final String UPDATED = "UPDATED";

    private static final String UNCHANGED = "UNCHANGED";

    private static final String SKIPPED = "SKIPPED";

    private static final String PAUSED = "PAUSED";

    private static final String FAILED = "FAILED";

    private final ReentrantLock rebuildLock = new ReentrantLock();

    @Autowired
    private OsmClient osmClient;

    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private IOptionResourceService resourceService;

    @Autowired
    private OptionUpdateAssembler assembler;

    @Autowired
    private OptionUpdateMapper updateMapper;

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
            result.put("message", "上一次更新菜单同步尚未结束");
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
        OptionUpdateSync sync = new OptionUpdateSync();
        sync.setTriggerType(trigger);
        sync.setCreateBy(operator);
        String cookie = sessionService.getCookie();
        if (cookie == null)
        {
            return finish(result, sync, PAUSED, "OSM 会话未登录或已过期，请重新登录后再同步", start, force);
        }

        // 1、抓取并解析页面
        Map<OptionUpdateKind, List<ObjectNode>> kinds = new EnumMap<>(OptionUpdateKind.class);
        try
        {
            OsmClient.OsmResponse response = osmClient.getPage(OptionUpdateAssembler.PAGE_PATH, cookie);
            sync.setPageCount(1);
            sync.setTransferBytes(response.transferBytes());
            if (response.status() == 401 || response.status() == 403)
            {
                String message = "OSM 会话已失效（HTTP " + response.status() + "），同步已暂停，请重新登录";
                sessionService.expire(message);
                return finish(result, sync, PAUSED, message, start, force);
            }
            if (response.status() != 200 || StringUtils.isEmpty(response.body()))
            {
                return finish(result, sync, FAILED, "OSM 页面 " + OptionUpdateAssembler.PAGE_PATH + " 返回异常（HTTP "
                        + response.status() + "）"
                        + (response.retryAfter() == null ? "" : "，Retry-After " + response.retryAfter() + " 秒"),
                        start, force);
            }
            Node root = OptionHtml.parse(response.body());
            if (OptionUpdateAssembler.wrap(root) == null)
            {
                return finish(result, sync, FAILED, "OSM 页面 " + OptionUpdateAssembler.PAGE_PATH
                        + " 未找到更新列表（ol.pu-list），页面结构可能已调整或会话已失效", start, force);
            }
            String base = osmClient.getBaseUrl();
            List<ObjectNode> entries = assembler.entries(root, base);
            kinds.put(OptionUpdateKind.PAGE, List.of(assembler.page(root, entries, base)));
            kinds.put(OptionUpdateKind.ENTRY, entries);
        }
        catch (ServiceException e)
        {
            return finish(result, sync, FAILED, e.getMessage(), start, force);
        }
        catch (RuntimeException e)
        {
            log.error("更新页面解析失败", e);
            return finish(result, sync, FAILED, "更新页面解析失败：" + e.getMessage(), start, force);
        }

        // 2、内容未变化：不写库
        StringBuilder source = new StringBuilder();
        kinds.forEach((kind, items) -> items.forEach(item -> source.append(kind.getKey()).append('|')
                .append(OptionJson.write(item)).append('\n')));
        String sourceHash = OptionJson.sha256(source.toString());
        sync.setSourceHash(sourceHash);
        OptionUpdateSync latest = updateMapper.selectLatestUpdatedSync();
        if (!force && latest != null && sourceHash.equals(latest.getSourceHash())
                && stringRedisTemplate.hasKey(SUMMARY_KEY))
        {
            result.put("result", UNCHANGED);
            result.put("syncId", latest.getSyncId());
            result.put("entryCount", latest.getEntryCount());
            result.put("transferBytes", sync.getTransferBytes());
            updateStatus(UNCHANGED, "更新内容未变化", latest.getSyncId());
            return result;
        }

        // 3、存库
        sync.setResult(FAILED);
        sync.setMessage("处理中");
        updateMapper.insertUpdateSync(sync);
        try
        {
            int newVersions = 0;
            List<String> changed = new ArrayList<>();
            for (Map.Entry<OptionUpdateKind, List<ObjectNode>> entry : kinds.entrySet())
            {
                int created = store(entry.getKey(), entry.getValue(), sync.getSyncId(), operator);
                newVersions += created;
                if (created > 0 || latest == null
                        || (entry.getKey() == OptionUpdateKind.ENTRY && !Integer.valueOf(entry.getValue().size())
                                .equals(latest.getEntryCount())))
                {
                    changed.add(entry.getKey().getKey());
                }
            }
            ObjectNode page = kinds.get(OptionUpdateKind.PAGE).get(0);
            JsonNode status = readStatus();
            sync.setResult(UPDATED);
            sync.setEntryCount(kinds.get(OptionUpdateKind.ENTRY).size());
            sync.setLatestDate(page.path("latestDate").asText(null));
            sync.setCurrentSeq(status != null && status.has("currentSeq") ? status.path("currentSeq").asInt() : null);
            sync.setNewVersionCount(newVersions);
            sync.setChangedKinds(String.join(",", changed));
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage("同步成功");
            updateMapper.updateUpdateSync(sync);

            // 4、写入 Redis
            publish(sync.getSyncId(), DateUtils.getTime(), kinds);
            updateStatus(UPDATED, "同步成功", sync.getSyncId());
            log.info("更新菜单同步完成：批次 {}，条目 {}，最新 {}，currentSeq {}，新增版本 {}，变化类型 [{}]，耗时 {}ms",
                    sync.getSyncId(), sync.getEntryCount(), sync.getLatestDate(), sync.getCurrentSeq(), newVersions,
                    sync.getChangedKinds(), sync.getCostMs());

            result.put("result", UPDATED);
            result.put("syncId", sync.getSyncId());
            result.put("entryCount", sync.getEntryCount());
            result.put("latestDate", sync.getLatestDate());
            result.put("currentSeq", sync.getCurrentSeq());
            result.put("transferBytes", sync.getTransferBytes());
            result.put("newVersionCount", newVersions);
            result.put("changedKinds", sync.getChangedKinds());
            result.put("costMs", sync.getCostMs());
            return result;
        }
        catch (RuntimeException e)
        {
            log.error("更新菜单同步失败", e);
            sync.setResult(FAILED);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage(StringUtils.substring("存库失败：" + e.getMessage(), 0, 500));
            updateMapper.updateUpdateSync(sync);
            updateStatus(FAILED, sync.getMessage(), sync.getSyncId());
            result.put("result", FAILED);
            result.put("syncId", sync.getSyncId());
            result.put("message", sync.getMessage());
            return result;
        }
    }

    /**
     * 未写入数据时结束：强制同步或状态变化时记一条流水，定时任务重复的失败/暂停只更新 Redis 状态
     */
    private Map<String, Object> finish(Map<String, Object> result, OptionUpdateSync sync, String state,
            String message, long start, boolean force)
    {
        Object lastState = redisCache.getCacheMapValue(STATUS_KEY, "state");
        Long syncId = null;
        if (force || !state.equals(lastState))
        {
            sync.setResult(state);
            sync.setMessage(StringUtils.substring(message, 0, 500));
            updateMapper.insertUpdateSync(sync);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            updateMapper.updateUpdateSync(sync);
            syncId = sync.getSyncId();
        }
        updateStatus(state, message, syncId);
        log.warn("更新菜单同步{}：{}", state, message);
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
    private int store(OptionUpdateKind kind, List<ObjectNode> items, Long syncId, String operator)
    {
        if (items.isEmpty())
        {
            return 0;
        }
        String table = kind.getTableName();
        List<OptionUpdateItem> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
        {
            OptionUpdateItem row = toRow(kind, items.get(i), i);
            rows.add(row);
            keys.add(row.getItemKey());
        }
        Map<String, Long> existing = new HashMap<>();
        for (List<String> chunk : chunks(keys.stream().distinct().toList()))
        {
            for (OptionUpdateItem v : updateMapper.selectVersionKeys(table, chunk))
            {
                existing.put(v.getItemKey() + "|" + v.getContentHash(), v.getVersionId());
            }
        }
        int created = 0;
        List<OptionUpdateItem> touched = new ArrayList<>();
        for (OptionUpdateItem row : rows)
        {
            String key = row.getItemKey() + "|" + row.getContentHash();
            Long versionId = existing.get(key);
            if (versionId != null)
            {
                OptionUpdateItem touch = new OptionUpdateItem();
                touch.setVersionId(versionId);
                touch.setSortNo(row.getSortNo());
                touched.add(touch);
                continue;
            }
            row.setFirstSyncId(syncId);
            row.setLastSyncId(syncId);
            row.setCreateBy(operator);
            updateMapper.insertItem(table, row);
            existing.put(key, row.getVersionId());
            created++;
        }
        for (List<OptionUpdateItem> chunk : chunks(touched))
        {
            updateMapper.touchItems(table, syncId, chunk, operator);
        }
        return created;
    }

    private OptionUpdateItem toRow(OptionUpdateKind kind, ObjectNode item, int sortNo)
    {
        String payload = OptionJson.write(item);
        OptionUpdateItem row = new OptionUpdateItem();
        row.setContentHash(OptionJson.sha256(payload));
        row.setSortNo(sortNo);
        row.setPayload(payload);
        row.setPagePath(cut(item, 255, "path"));
        row.setTitle(cut(item, 200, "title"));
        row.setTextLength(item.has("textLength") ? item.path("textLength").asInt() : null);
        if (kind == OptionUpdateKind.PAGE)
        {
            row.setSubTitle(cut(item, 500, "sub", "foot"));
            row.setPublishDate(cut(item, 10, "latestDate"));
            row.setTargetPath(cut(item, 255, "settingsPath"));
            row.setVisualType(item.hasNonNull("mascot") ? "image" : "none");
            row.setMediaUrl(cut(item, 500, "mascot"));
            row.setItemCount(item.path("itemCount").asInt());
        }
        else
        {
            row.setSubTitle(cut(item, 500, "what", "how"));
            row.setPublishDate(cut(item, 10, "date"));
            row.setTargetPath(StringUtils.substring(item.path("target").path("path").asText(null), 0, 255));
            JsonNode visual = item.path("visual");
            row.setVisualType(StringUtils.substring(visual.path("type").asText(null), 0, 16));
            row.setMediaUrl(StringUtils.substring(visual.path("src").asText(null), 0, 500));
            row.setItemCount(1);
        }
        String itemKey = text(item, "key");
        row.setItemKey(StringUtils.substring(StringUtils.isEmpty(itemKey) ? "h:" + row.getContentHash() : itemKey,
                0, 160));
        return row;
    }

    /**
     * 写入 Redis：每个类型一份列表 + 单条完整内容 Hash + 汇总
     */
    private void publish(Long syncId, String syncTime, Map<OptionUpdateKind, List<ObjectNode>> kinds)
    {
        for (Map.Entry<OptionUpdateKind, List<ObjectNode>> entry : kinds.entrySet())
        {
            OptionUpdateKind kind = entry.getKey();
            ObjectNode body = OptionJson.MAPPER.createObjectNode();
            body.put("kind", kind.getKey());
            body.put("label", kind.getLabel());
            body.put("syncId", syncId);
            body.put("syncTime", syncTime);
            body.put("count", entry.getValue().size());
            ArrayNode items = body.putArray("items");
            Map<String, String> full = new LinkedHashMap<>();
            for (ObjectNode item : entry.getValue())
            {
                items.add(item);
                full.put(item.path("key").asText(), OptionJson.write(item));
            }
            String itemKey = ITEM_KEY_PREFIX + kind.getKey();
            stringRedisTemplate.delete(itemKey);
            if (!full.isEmpty())
            {
                stringRedisTemplate.opsForHash().putAll(itemKey, full);
            }
            stringRedisTemplate.opsForValue().set(DATA_KEY_PREFIX + kind.getKey(), OptionJson.write(body));
        }
        List<ObjectNode> pages = kinds.getOrDefault(OptionUpdateKind.PAGE, List.of());
        ObjectNode summary = pages.isEmpty() ? OptionJson.MAPPER.createObjectNode() : pages.get(0).deepCopy();
        summary.put("syncId", syncId);
        summary.put("syncTime", syncTime);
        ArrayNode latest = summary.putArray("latest");
        List<ObjectNode> entries = kinds.getOrDefault(OptionUpdateKind.ENTRY, List.of());
        for (int i = 0; i < Math.min(LATEST_SIZE, entries.size()); i++)
        {
            ObjectNode brief = latest.addObject();
            ObjectNode entry = entries.get(i);
            for (String field : List.of("key", "path", "date", "title", "what", "target"))
            {
                brief.set(field, entry.get(field));
            }
            brief.put("cover", entry.path("visual").path("poster").asText(entry.path("visual").path("src")
                    .asText(null)));
        }
        stringRedisTemplate.opsForValue().set(SUMMARY_KEY, OptionJson.write(summary));
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
        summary.set("progress", readStatus());
        summary.set("status", OptionJson.MAPPER.valueToTree(redisCache.getCacheMap(STATUS_KEY)));
        String text = OptionJson.write(summary);
        return new FeedBody(text, OptionJson.etag(text), null, null);
    }

    @Override
    public FeedBody getKind(String kind, String keyword, String month, String target)
    {
        OptionUpdateKind spec = requireKind(kind);
        String body = stringRedisTemplate.opsForValue().get(DATA_KEY_PREFIX + spec.getKey());
        if (body == null)
        {
            body = rebuild() ? stringRedisTemplate.opsForValue().get(DATA_KEY_PREFIX + spec.getKey()) : null;
            if (body == null)
            {
                return null;
            }
        }
        String word = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String m = StringUtils.trim(month);
        String t = StringUtils.trim(target);
        if (StringUtils.isNotEmpty(word) || StringUtils.isNotEmpty(m) || StringUtils.isNotEmpty(t))
        {
            ObjectNode root = (ObjectNode) OptionJson.read(body);
            ArrayNode items = OptionJson.MAPPER.createArrayNode();
            for (JsonNode item : root.path("items"))
            {
                if (StringUtils.isNotEmpty(word) && !(StringUtils.nvl(item.path("title").asText(null), "") + "\n"
                        + StringUtils.nvl(item.path("what").asText(null), "") + "\n"
                        + StringUtils.nvl(item.path("how").asText(null), "")).toLowerCase(Locale.ROOT).contains(word))
                {
                    continue;
                }
                if (StringUtils.isNotEmpty(m) && !m.equals(item.path("month").asText(null)))
                {
                    continue;
                }
                if (StringUtils.isNotEmpty(t) && !item.path("target").path("path").asText("").startsWith(t))
                {
                    continue;
                }
                items.add(item);
            }
            if (StringUtils.isNotEmpty(word))
            {
                root.put("keyword", keyword.trim());
            }
            if (StringUtils.isNotEmpty(m))
            {
                root.put("month", m);
            }
            if (StringUtils.isNotEmpty(t))
            {
                root.put("target", t);
            }
            root.put("count", items.size());
            root.set("items", items);
            body = OptionJson.write(root);
        }
        return new FeedBody(body, OptionJson.etag(body), null, null);
    }

    @Override
    public FeedBody getItem(String kind, String key)
    {
        OptionUpdateKind spec = requireKind(kind);
        String hashKey = ITEM_KEY_PREFIX + spec.getKey();
        if (!stringRedisTemplate.hasKey(hashKey) && !rebuild())
        {
            return null;
        }
        Object value = stringRedisTemplate.opsForHash().get(hashKey, key);
        if (value == null)
        {
            throw new ServiceException(spec.getLabel() + "不存在：" + key);
        }
        String body = value.toString();
        return new FeedBody(body, OptionJson.etag(body), null, null);
    }

    /**
     * 阅读进度：读 /api/member/updates 的同步数据（Redis，未命中时由资源服务从 option_res_update 回填）
     */
    private JsonNode readStatus()
    {
        try
        {
            FeedBody body = resourceService.get(UPDATES_RESOURCE, null);
            return body == null || body.body() == null ? null : OptionJson.read(body.body());
        }
        catch (RuntimeException e)
        {
            log.debug("读取更新阅读进度失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * Redis 未命中：按最近一次成功批次从表回填
     * 
     * @return 是否回填成功
     */
    private boolean rebuild()
    {
        rebuildLock.lock();
        try
        {
            if (stringRedisTemplate.hasKey(SUMMARY_KEY)
                    && stringRedisTemplate.hasKey(DATA_KEY_PREFIX + OptionUpdateKind.ENTRY.getKey())
                    && stringRedisTemplate.hasKey(ITEM_KEY_PREFIX + OptionUpdateKind.ENTRY.getKey()))
            {
                return true;
            }
            OptionUpdateSync latest = updateMapper.selectLatestUpdatedSync();
            if (latest == null)
            {
                return false;
            }
            log.info("Redis 未命中更新菜单数据，从表回填批次 {}", latest.getSyncId());
            Map<OptionUpdateKind, List<ObjectNode>> kinds = new EnumMap<>(OptionUpdateKind.class);
            for (OptionUpdateKind kind : OptionUpdateKind.values())
            {
                List<ObjectNode> items = new ArrayList<>();
                for (OptionUpdateItem row : updateMapper.selectItemsBySync(kind.getTableName(), latest.getSyncId()))
                {
                    items.add((ObjectNode) OptionJson.read(row.getPayload()));
                }
                kinds.put(kind, items);
            }
            publish(latest.getSyncId(), DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, latest.getSyncTime()),
                    kinds);
            return true;
        }
        finally
        {
            rebuildLock.unlock();
        }
    }

    @Override
    public List<OptionUpdateSync> selectSyncList(OptionUpdateSync sync)
    {
        return updateMapper.selectUpdateSyncList(sync);
    }

    @Override
    public List<OptionUpdateItem> selectItemList(String kind, OptionUpdateItem item)
    {
        return updateMapper.selectItemList(requireKind(kind).getTableName(),
                item == null ? new OptionUpdateItem() : item);
    }

    @Override
    public OptionUpdateItem selectItem(String kind, Long versionId)
    {
        return updateMapper.selectItemById(requireKind(kind).getTableName(), versionId);
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

    private static OptionUpdateKind requireKind(String kind)
    {
        OptionUpdateKind spec = OptionUpdateKind.of(kind);
        if (spec == null)
        {
            throw new ServiceException("类型无效，可选：page、entry");
        }
        return spec;
    }
}
