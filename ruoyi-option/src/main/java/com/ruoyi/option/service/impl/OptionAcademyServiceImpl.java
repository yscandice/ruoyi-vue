package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import com.ruoyi.option.domain.OptionAcademyItem;
import com.ruoyi.option.domain.OptionAcademySync;
import com.ruoyi.option.enums.OptionAcademyKind;
import com.ruoyi.option.mapper.OptionAcademyMapper;
import com.ruoyi.option.service.IOptionAcademyService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionHtml;
import com.ruoyi.option.utils.OptionHtml.Node;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「学堂」菜单 服务层实现
 * 
 * 学堂没有专属 JSON 接口（页面只调用公共的 feed / seller-guide / updates / support，已由 OptionResource 同步），
 * 课程正文随页面服务端渲染下发。定时抓取 /academy、各课程页、/academy/weekly-qa 及各期、/academy/glossary
 * -> 解析为结构化数据 -> 存库（option_academy_{kind}，按 条目键+内容哈希 去重，last_sync_id 标记当前内容）
 * -> Redis（option:academy:{kind} 列表、option:academy:item:{kind} 单条完整内容）-> 前端只读
 * 
 * @author yscandice
 */
@Service
public class OptionAcademyServiceImpl implements IOptionAcademyService
{
    private static final Logger log = LoggerFactory.getLogger(OptionAcademyServiceImpl.class);

    /** 类型列表 Redis 前缀（option:academy:lesson 等） */
    public static final String DATA_KEY_PREFIX = "option:academy:";

    /** 单条完整内容 Redis Hash 前缀（option:academy:item:lesson，field 为条目键） */
    public static final String ITEM_KEY_PREFIX = "option:academy:item:";

    /** 汇总 Redis 键 */
    public static final String SUMMARY_KEY = "option:academy:summary";

    /** 运行状态 Redis 键 */
    public static final String STATUS_KEY = "option:academy:sync:status";

    /** 同步锁 Redis 键 */
    public static final String LOCK_KEY = "option:academy:sync:lock";

    private static final long LOCK_SECONDS = 300;

    /** 页面请求间隔，避免对 OSM 造成突发压力 */
    private static final long REQUEST_GAP_MILLIS = 800;

    /** 一周问答最多抓取期数（书架按最新在前） */
    private static final int MAX_QA_ISSUES = 60;

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
    private OptionAcademyAssembler assembler;

    @Autowired
    private OptionAcademyMapper academyMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 同步中止（state 为 PAUSED / FAILED）
     */
    private static final class Abort extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final String state;

        Abort(String state, String message)
        {
            super(message);
            this.state = state;
        }
    }

    /**
     * 一次同步的抓取上下文
     */
    private static final class Fetch
    {
        private final String cookie;

        private int pages;

        private int bytes;

        Fetch(String cookie)
        {
            this.cookie = cookie;
        }
    }

    @Override
    public Map<String, Object> sync(boolean force, String trigger, String operator)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, DateUtils.getTime(),
                Duration.ofSeconds(LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked))
        {
            result.put("result", SKIPPED);
            result.put("message", "上一次学堂菜单同步尚未结束");
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
        OptionAcademySync sync = new OptionAcademySync();
        sync.setTriggerType(trigger);
        sync.setCreateBy(operator);
        String cookie = sessionService.getCookie();
        if (cookie == null)
        {
            return finish(result, sync, PAUSED, "OSM 会话未登录或已过期，请重新登录后再同步", start, force);
        }

        // 1、抓取并解析页面
        Fetch fetch = new Fetch(cookie);
        Map<OptionAcademyKind, List<ObjectNode>> kinds;
        try
        {
            kinds = crawl(fetch);
        }
        catch (Abort e)
        {
            sync.setPageCount(fetch.pages);
            sync.setTransferBytes(fetch.bytes);
            return finish(result, sync, e.state, e.getMessage(), start, force);
        }
        catch (ServiceException e)
        {
            sync.setPageCount(fetch.pages);
            sync.setTransferBytes(fetch.bytes);
            return finish(result, sync, FAILED, e.getMessage(), start, force);
        }
        catch (RuntimeException e)
        {
            log.error("学堂页面解析失败", e);
            sync.setPageCount(fetch.pages);
            sync.setTransferBytes(fetch.bytes);
            return finish(result, sync, FAILED, "学堂页面解析失败：" + e.getMessage(), start, force);
        }

        // 2、内容未变化：不写库
        StringBuilder source = new StringBuilder();
        kinds.forEach((kind, items) -> items.forEach(item -> source.append(kind.getKey()).append('|')
                .append(OptionJson.write(item)).append('\n')));
        String sourceHash = OptionJson.sha256(source.toString());
        sync.setSourceHash(sourceHash);
        sync.setPageCount(fetch.pages);
        sync.setTransferBytes(fetch.bytes);
        OptionAcademySync latest = academyMapper.selectLatestUpdatedSync();
        if (!force && latest != null && sourceHash.equals(latest.getSourceHash())
                && stringRedisTemplate.hasKey(SUMMARY_KEY))
        {
            result.put("result", UNCHANGED);
            result.put("syncId", latest.getSyncId());
            result.put("pageCount", fetch.pages);
            result.put("transferBytes", fetch.bytes);
            updateStatus(UNCHANGED, "学堂内容未变化", latest.getSyncId());
            return result;
        }

        // 3、存库
        sync.setResult(FAILED);
        sync.setMessage("处理中");
        academyMapper.insertAcademySync(sync);
        try
        {
            Map<String, Integer> previous = latest == null ? new HashMap<>() : counts(latest);
            int newVersions = 0;
            List<String> changed = new ArrayList<>();
            for (Map.Entry<OptionAcademyKind, List<ObjectNode>> entry : kinds.entrySet())
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
            sync.setPageItemCount(kinds.get(OptionAcademyKind.PAGE).size());
            sync.setLessonCount(kinds.get(OptionAcademyKind.LESSON).size());
            sync.setQaCount(kinds.get(OptionAcademyKind.QA).size());
            sync.setGlossaryCount(kinds.get(OptionAcademyKind.GLOSSARY).size());
            sync.setNewVersionCount(newVersions);
            sync.setChangedKinds(String.join(",", changed));
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage("同步成功");
            academyMapper.updateAcademySync(sync);

            // 4、写入 Redis
            publish(sync.getSyncId(), DateUtils.getTime(), kinds);
            updateStatus(UPDATED, "同步成功", sync.getSyncId());
            log.info("学堂菜单同步完成：批次 {}，页面 {}，课程 {}，问答 {}，术语 {}，新增版本 {}，变化类型 [{}]，耗时 {}ms",
                    sync.getSyncId(), fetch.pages, sync.getLessonCount(), sync.getQaCount(), sync.getGlossaryCount(),
                    newVersions, sync.getChangedKinds(), sync.getCostMs());

            result.put("result", UPDATED);
            result.put("syncId", sync.getSyncId());
            result.put("pageCount", fetch.pages);
            result.put("transferBytes", fetch.bytes);
            result.put("counts", counts(sync));
            result.put("newVersionCount", newVersions);
            result.put("changedKinds", sync.getChangedKinds());
            result.put("costMs", sync.getCostMs());
            return result;
        }
        catch (RuntimeException e)
        {
            log.error("学堂菜单同步失败", e);
            sync.setResult(FAILED);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            sync.setMessage(StringUtils.substring("存库失败：" + e.getMessage(), 0, 500));
            academyMapper.updateAcademySync(sync);
            updateStatus(FAILED, sync.getMessage(), sync.getSyncId());
            result.put("result", FAILED);
            result.put("syncId", sync.getSyncId());
            result.put("message", sync.getMessage());
            return result;
        }
    }

    /**
     * 抓取：首页 -> 课程（首页课程清单 + 书目）-> 问答书架 -> 各期问答 -> 术语速查
     */
    private Map<OptionAcademyKind, List<ObjectNode>> crawl(Fetch fetch)
    {
        String base = osmClient.getBaseUrl();
        Map<OptionAcademyKind, List<ObjectNode>> kinds = new EnumMap<>(OptionAcademyKind.class);
        List<ObjectNode> pages = new ArrayList<>();
        List<ObjectNode> lessons = new ArrayList<>();
        List<ObjectNode> issues = new ArrayList<>();
        kinds.put(OptionAcademyKind.PAGE, pages);
        kinds.put(OptionAcademyKind.LESSON, lessons);
        kinds.put(OptionAcademyKind.QA, issues);

        Node homeRoot = page(fetch, OptionAcademyAssembler.HOME_PATH, true);
        ObjectNode home = assembler.home(homeRoot, base);
        pages.add(home);

        // 课程：以首页课程清单为准，书目中额外出现的课程一并抓取
        Map<String, JsonNode> entries = new LinkedHashMap<>();
        for (JsonNode entry : home.path("lessons"))
        {
            entries.put(entry.path("path").asText(), entry);
        }
        Set<String> paths = new LinkedHashSet<>(entries.keySet());
        for (JsonNode book : home.path("books"))
        {
            if ("lesson".equals(book.path("type").asText()))
            {
                paths.add(book.path("path").asText());
            }
        }
        if (paths.isEmpty())
        {
            throw new Abort(FAILED, "学堂首页未解析到课程清单，页面结构可能已调整");
        }
        for (String path : paths)
        {
            Node root = page(fetch, path, true);
            JsonNode entry = entries.get(path);
            if (OptionAcademyAssembler.locked(root))
            {
                String message = "学堂课程 " + path + " 返回会员门禁（本课正文对会员开放），OSM 会话已失效，同步已暂停，请重新登录";
                sessionService.expire(message);
                throw new Abort(PAUSED, message);
            }
            ObjectNode lesson = assembler.lesson(root, path, entry, base);
            if (entry == null)
            {
                lesson.put("order", lessons.size() + 1);
            }
            lessons.add(lesson);
        }

        // 一周问答：书架 + 各期（不存在的期次跳过）
        Node shelfRoot = page(fetch, OptionAcademyAssembler.QA_PATH, true);
        ObjectNode shelf = assembler.qaShelf(shelfRoot, base);
        pages.add(shelf);
        int fetched = 0;
        for (JsonNode cover : shelf.path("issues"))
        {
            if (fetched++ >= MAX_QA_ISSUES)
            {
                break;
            }
            String path = cover.path("path").asText();
            Node root = page(fetch, path, false);
            if (root == null)
            {
                log.warn("一周问答 {} 无法访问，已跳过", path);
                continue;
            }
            issues.add(assembler.qa(root, path, cover, base));
        }

        // 术语速查
        Node glossaryRoot = page(fetch, OptionAcademyAssembler.GLOSSARY_PATH, true);
        ObjectNode[] header = new ObjectNode[1];
        kinds.put(OptionAcademyKind.GLOSSARY, assembler.glossary(glossaryRoot, base, header));
        pages.add(header[0]);
        return kinds;
    }

    /**
     * 请求页面并解析
     * 
     * @param required 必需页面：非 200 视为失败；否则 404 等返回 null
     */
    private Node page(Fetch fetch, String path, boolean required)
    {
        if (fetch.pages > 0)
        {
            sleepQuietly();
        }
        OsmClient.OsmResponse response = osmClient.getPage(path, fetch.cookie);
        fetch.pages++;
        fetch.bytes += response.transferBytes();
        if (response.status() == 401 || response.status() == 403)
        {
            String message = "OSM 会话已失效（HTTP " + response.status() + "），同步已暂停，请重新登录";
            sessionService.expire(message);
            throw new Abort(PAUSED, message);
        }
        if (response.status() != 200 || StringUtils.isEmpty(response.body()))
        {
            if (!required)
            {
                return null;
            }
            throw new Abort(FAILED, "OSM 页面 " + path + " 返回异常（HTTP " + response.status() + "）"
                    + (response.retryAfter() == null ? "" : "，Retry-After " + response.retryAfter() + " 秒"));
        }
        Node root = OptionHtml.parse(response.body());
        if (OptionAcademyAssembler.main(root) == null)
        {
            if (!required)
            {
                return null;
            }
            throw new Abort(FAILED, "OSM 页面 " + path + " 未找到正文（main），页面结构可能已调整");
        }
        return root;
    }

    /**
     * 未写入数据时结束：强制同步或状态变化时记一条流水，定时任务重复的失败/暂停只更新 Redis 状态
     */
    private Map<String, Object> finish(Map<String, Object> result, OptionAcademySync sync, String state,
            String message, long start, boolean force)
    {
        Object lastState = redisCache.getCacheMapValue(STATUS_KEY, "state");
        Long syncId = null;
        if (force || !state.equals(lastState))
        {
            sync.setResult(state);
            sync.setMessage(StringUtils.substring(message, 0, 500));
            academyMapper.insertAcademySync(sync);
            sync.setCostMs((int) (System.currentTimeMillis() - start));
            academyMapper.updateAcademySync(sync);
            syncId = sync.getSyncId();
        }
        updateStatus(state, message, syncId);
        log.warn("学堂菜单同步{}：{}", state, message);
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
    private int store(OptionAcademyKind kind, List<ObjectNode> items, Long syncId, String operator)
    {
        if (items.isEmpty())
        {
            return 0;
        }
        String table = kind.getTableName();
        List<OptionAcademyItem> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
        {
            OptionAcademyItem row = toRow(kind, items.get(i), i);
            rows.add(row);
            keys.add(row.getItemKey());
        }
        Map<String, Long> existing = new HashMap<>();
        for (List<String> chunk : chunks(keys.stream().distinct().toList()))
        {
            for (OptionAcademyItem v : academyMapper.selectVersionKeys(table, chunk))
            {
                existing.put(v.getItemKey() + "|" + v.getContentHash(), v.getVersionId());
            }
        }
        int created = 0;
        List<OptionAcademyItem> touched = new ArrayList<>();
        for (OptionAcademyItem row : rows)
        {
            String key = row.getItemKey() + "|" + row.getContentHash();
            Long versionId = existing.get(key);
            if (versionId != null)
            {
                OptionAcademyItem touch = new OptionAcademyItem();
                touch.setVersionId(versionId);
                touch.setSortNo(row.getSortNo());
                touched.add(touch);
                continue;
            }
            row.setFirstSyncId(syncId);
            row.setLastSyncId(syncId);
            row.setCreateBy(operator);
            academyMapper.insertItem(table, row);
            existing.put(key, row.getVersionId());
            created++;
        }
        for (List<OptionAcademyItem> chunk : chunks(touched))
        {
            academyMapper.touchItems(table, syncId, chunk, operator);
        }
        return created;
    }

    private OptionAcademyItem toRow(OptionAcademyKind kind, ObjectNode item, int sortNo)
    {
        String payload = OptionJson.write(item);
        OptionAcademyItem row = new OptionAcademyItem();
        row.setContentHash(OptionJson.sha256(payload));
        row.setSortNo(sortNo);
        row.setPayload(payload);
        row.setPagePath(cut(item, 255, "path", "anchor"));
        row.setItemCount(item.has("itemCount") ? item.path("itemCount").asInt() : null);
        row.setTextLength(item.has("textLength") ? item.path("textLength").asInt() : null);
        switch (kind)
        {
            case PAGE:
                row.setTitle(cut(item, 200, "title"));
                row.setSubTitle(cut(item, 500, "lede", "deck"));
                row.setCategory(cut(item, 64, "key"));
                break;
            case LESSON:
                row.setTitle(cut(item, 200, "title", "navTitle"));
                row.setSubTitle(cut(item, 500, "deck", "summary"));
                row.setCategory(cut(item, 64, "no"));
                row.setAccessLevel(cut(item, 16, "access"));
                break;
            case QA:
                row.setTitle(cut(item, 200, "title"));
                row.setSubTitle(cut(item, 500, "range", "deck"));
                row.setCategory(cut(item, 64, "no"));
                row.setAccessLevel(item.path("latest").asBoolean() ? "最新" : null);
                row.setPublishDate(cut(item, 10, "publishDate"));
                break;
            default:
                row.setTitle(cut(item, 200, "term"));
                row.setSubTitle(cut(item, 500, "definition"));
                row.setCategory(cut(item, 64, "en"));
                row.setItemCount(1);
                row.setTextLength(StringUtils.nvl(text(item, "definition"), "").length());
                break;
        }
        String itemKey = text(item, "key");
        row.setItemKey(StringUtils.substring(StringUtils.isEmpty(itemKey) ? "h:" + row.getContentHash() : itemKey,
                0, 160));
        return row;
    }

    /**
     * 写入 Redis：每个类型一份列表（精简）+ 单条完整内容 Hash + 汇总
     */
    private void publish(Long syncId, String syncTime, Map<OptionAcademyKind, List<ObjectNode>> kinds)
    {
        ObjectNode summary = OptionJson.MAPPER.createObjectNode();
        summary.put("syncId", syncId);
        summary.put("syncTime", syncTime);
        ArrayNode list = summary.putArray("kinds");
        for (Map.Entry<OptionAcademyKind, List<ObjectNode>> entry : kinds.entrySet())
        {
            OptionAcademyKind kind = entry.getKey();
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
                items.add(brief(item));
                full.put(item.path("key").asText(), OptionJson.write(item));
            }
            String itemKey = ITEM_KEY_PREFIX + kind.getKey();
            stringRedisTemplate.delete(itemKey);
            if (!full.isEmpty())
            {
                stringRedisTemplate.opsForHash().putAll(itemKey, full);
            }
            stringRedisTemplate.opsForValue().set(DATA_KEY_PREFIX + kind.getKey(), OptionJson.write(body));
            list.addObject().put("kind", kind.getKey()).put("label", kind.getLabel()).put("count",
                    entry.getValue().size());
        }
        ObjectNode home = null;
        for (ObjectNode page : kinds.getOrDefault(OptionAcademyKind.PAGE, List.of()))
        {
            if ("home".equals(page.path("key").asText()))
            {
                home = page;
            }
        }
        if (home != null)
        {
            summary.put("kick", text(home, "kick"));
            summary.put("title", text(home, "title"));
            summary.put("lede", text(home, "lede"));
            summary.put("scopeLine", text(home, "scopeLine"));
            summary.set("books", home.get("books"));
            summary.set("refs", home.get("refs"));
        }
        ArrayNode lessons = summary.putArray("lessons");
        for (ObjectNode lesson : kinds.getOrDefault(OptionAcademyKind.LESSON, List.of()))
        {
            ObjectNode brief = lessons.addObject();
            for (String field : List.of("key", "path", "order", "no", "title", "navTitle", "access", "open",
                    "tagline", "summary", "caseLine", "itemCount", "textLength"))
            {
                brief.set(field, lesson.get(field));
            }
        }
        List<ObjectNode> qa = kinds.getOrDefault(OptionAcademyKind.QA, List.of());
        if (!qa.isEmpty())
        {
            ObjectNode latest = summary.putObject("latestQa");
            for (String field : List.of("key", "path", "no", "title", "range", "summary", "publishDate", "count"))
            {
                latest.set(field, qa.get(0).get(field));
            }
        }
        stringRedisTemplate.opsForValue().set(SUMMARY_KEY, OptionJson.write(summary));
    }

    /**
     * 列表精简：去掉正文 HTML / 全文 / 问答答案，完整内容走单条接口
     */
    private static JsonNode brief(ObjectNode item)
    {
        ObjectNode copy = item.deepCopy();
        copy.remove("html");
        copy.remove("text");
        JsonNode sections = copy.get("sections");
        if (sections != null)
        {
            stripText(sections);
        }
        for (JsonNode chapter : copy.path("chapters"))
        {
            for (JsonNode qa : chapter.path("items"))
            {
                ((ObjectNode) qa).remove("answerHtml");
                ((ObjectNode) qa).remove("answer");
            }
        }
        return copy;
    }

    private static void stripText(JsonNode sections)
    {
        for (JsonNode sec : sections)
        {
            ((ObjectNode) sec).remove("text");
            if (sec.has("subsections"))
            {
                stripText(sec.get("subsections"));
            }
        }
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

    @Override
    public FeedBody getKind(String kind, String keyword)
    {
        OptionAcademyKind spec = requireKind(kind);
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
        if (StringUtils.isNotEmpty(word))
        {
            ObjectNode root = (ObjectNode) OptionJson.read(body);
            ArrayNode items = OptionJson.MAPPER.createArrayNode();
            for (JsonNode item : root.path("items"))
            {
                if (OptionJson.write(item).toLowerCase(Locale.ROOT).contains(word))
                {
                    items.add(item);
                }
            }
            root.put("keyword", keyword.trim());
            root.put("count", items.size());
            root.set("items", items);
            body = OptionJson.write(root);
        }
        return new FeedBody(body, OptionJson.etag(body), null, null);
    }

    @Override
    public FeedBody getItem(String kind, String key)
    {
        OptionAcademyKind spec = requireKind(kind);
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
     * Redis 未命中：按最近一次成功批次从 4 张表回填
     * 
     * @return 是否回填成功
     */
    private boolean rebuild()
    {
        rebuildLock.lock();
        try
        {
            if (stringRedisTemplate.hasKey(SUMMARY_KEY)
                    && stringRedisTemplate.hasKey(DATA_KEY_PREFIX + OptionAcademyKind.LESSON.getKey())
                    && stringRedisTemplate.hasKey(ITEM_KEY_PREFIX + OptionAcademyKind.LESSON.getKey()))
            {
                return true;
            }
            OptionAcademySync latest = academyMapper.selectLatestUpdatedSync();
            if (latest == null)
            {
                return false;
            }
            log.info("Redis 未命中学堂菜单数据，从表回填批次 {}", latest.getSyncId());
            Map<OptionAcademyKind, List<ObjectNode>> kinds = new EnumMap<>(OptionAcademyKind.class);
            for (OptionAcademyKind kind : OptionAcademyKind.values())
            {
                List<ObjectNode> items = new ArrayList<>();
                for (OptionAcademyItem row : academyMapper.selectItemsBySync(kind.getTableName(), latest.getSyncId()))
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
    public List<OptionAcademySync> selectSyncList(OptionAcademySync sync)
    {
        return academyMapper.selectAcademySyncList(sync);
    }

    @Override
    public List<OptionAcademyItem> selectItemList(String kind, OptionAcademyItem item)
    {
        return academyMapper.selectItemList(requireKind(kind).getTableName(),
                item == null ? new OptionAcademyItem() : item);
    }

    @Override
    public OptionAcademyItem selectItem(String kind, Long versionId)
    {
        return academyMapper.selectItemById(requireKind(kind).getTableName(), versionId);
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

    private static Map<String, Integer> counts(OptionAcademySync sync)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(OptionAcademyKind.PAGE.getKey(), sync.getPageItemCount());
        counts.put(OptionAcademyKind.LESSON.getKey(), sync.getLessonCount());
        counts.put(OptionAcademyKind.QA.getKey(), sync.getQaCount());
        counts.put(OptionAcademyKind.GLOSSARY.getKey(), sync.getGlossaryCount());
        return counts;
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

    private static void sleepQuietly()
    {
        try
        {
            Thread.sleep(REQUEST_GAP_MILLIS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static OptionAcademyKind requireKind(String kind)
    {
        OptionAcademyKind spec = OptionAcademyKind.of(kind);
        if (spec == null)
        {
            throw new ServiceException("类型无效，可选：page、lesson、qa、glossary");
        }
        return spec;
    }
}
