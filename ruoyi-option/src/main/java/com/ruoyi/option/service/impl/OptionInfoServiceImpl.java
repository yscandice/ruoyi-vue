package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
import com.ruoyi.option.domain.OptionColumnValue;
import com.ruoyi.option.domain.OptionInfoSync;
import com.ruoyi.option.domain.OptionInfoVersion;
import com.ruoyi.option.enums.OptionInfo;
import com.ruoyi.option.mapper.OptionInfoMapper;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionInfoService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionJson;
import com.ruoyi.option.utils.OptionPageParser;

/**
 * OSM「信息」菜单数据 服务层实现
 * 
 * <pre>
 * 方案：定时同步 → 存库（option_info_*，内容哈希去重）→ Redis → 前端只读
 * Redis：option:info:{info}:-          JSON 接口原始响应 / 页面列表索引
 *        option:info:{info}:{bizKey}   盘前简报（日期）、财报解读（财报ID）、札记（札记ID）单篇内容，保留 30 天，过期后从表回填
 *        option:info:sync:status       各数据源最近一次同步状态（Hash）
 * </pre>
 * 
 * @author yscandice
 */
@Service
public class OptionInfoServiceImpl implements IOptionInfoService
{
    private static final Logger log = LoggerFactory.getLogger(OptionInfoServiceImpl.class);

    private static final String KEY_PREFIX = "option:info:";

    private static final String STATUS_KEY = "option:info:sync:status";

    private static final Duration DETAIL_TTL = Duration.ofDays(30);

    /** 单次同步最多补齐的历史篇数 */
    private static final int MAX_BACKFILL = 20;

    /** 连续请求的间隔，避免触发 OSM 限流 */
    private static final long REQUEST_GAP_MILLIS = 800;

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final Pattern SLUG = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    @Autowired
    private OsmClient osmClient;

    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private OptionInfoMapper infoMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisCache redisCache;

    @Override
    public Map<String, Object> sync(String info, String bizKey, String trigger, String operator)
    {
        OptionInfo spec = requireInfo(info);
        String key = StringUtils.trim(bizKey);
        if (StringUtils.isNotEmpty(key))
        {
            validateBizKey(spec, key);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("info", spec.getKey());
        String cookie = sessionService.getCookie();
        if (cookie == null)
        {
            result.put("result", "PAUSED");
            result.put("message", "OSM 会话未登录或已过期，请重新登录后再同步");
            updateStatus(spec, result);
            log.warn("{} 同步暂停：OSM 会话未登录", spec.getKey());
            return result;
        }
        Context ctx = new Context(spec, cookie, trigger, StringUtils.nvl(operator, "system"));
        switch (spec.getSource())
        {
            case JSON:
                syncJson(ctx, result);
                break;
            case PAGE:
                syncPage(ctx, key, result);
                break;
            default:
                syncNotes(ctx, result);
                break;
        }
        updateStatus(spec, result);
        return result;
    }

    @Override
    public Map<String, Object> syncAll(String trigger, String operator)
    {
        Map<String, Object> results = new LinkedHashMap<>();
        for (OptionInfo spec : OptionInfo.values())
        {
            try
            {
                results.put(spec.getKey(), sync(spec.getKey(), null, trigger, operator));
            }
            catch (ServiceException e)
            {
                results.put(spec.getKey(), Map.of("result", "FAILED", "message", e.getMessage()));
                if (sessionService.getCookie() == null)
                {
                    break;
                }
            }
            sleepQuietly();
        }
        return results;
    }

    @Override
    public FeedBody getRaw(String info)
    {
        OptionInfo spec = requireInfo(info);
        if (spec.getSource() != OptionInfo.Source.JSON)
        {
            throw new ServiceException(spec.getKey() + " 不是 JSON 接口");
        }
        String body = readRow(spec, OptionInfo.INDEX_KEY);
        return body == null ? null : new FeedBody(body, OptionJson.etag(body), null, null);
    }

    @Override
    public FeedBody getPremarket(String date)
    {
        if (StringUtils.isNotEmpty(date))
        {
            validateBizKey(OptionInfo.PREMARKET, date);
        }
        JsonNode index = readJson(OptionInfo.PREMARKET, OptionInfo.INDEX_KEY);
        String day = StringUtils.isNotEmpty(date) ? date : index == null ? null : index.path("latest").asText(null);
        JsonNode detail = day == null ? null : readJson(OptionInfo.PREMARKET, day);
        if (index == null && detail == null)
        {
            return null;
        }
        ObjectNode out = OptionJson.MAPPER.createObjectNode();
        out.set("brief", detail == null ? null : detail.get("brief"));
        out.set("dates", index == null ? OptionJson.MAPPER.createArrayNode() : index.path("dates"));
        out.put("missing", detail == null ? day : null);
        out.put("memberValid", detail == null || detail.path("memberValid").asBoolean(true));
        out.put("loadFailed", false);
        out.set("access", detail == null ? null : detail.get("access"));
        return body(out);
    }

    @Override
    public FeedBody getEarnings(String id)
    {
        if (StringUtils.isNotEmpty(id))
        {
            validateBizKey(OptionInfo.EARNINGS, id);
        }
        JsonNode index = readJson(OptionInfo.EARNINGS, OptionInfo.INDEX_KEY);
        String pieceId = StringUtils.isNotEmpty(id) ? id : index == null ? null : index.path("latest").asText(null);
        JsonNode detail = pieceId == null ? null : readJson(OptionInfo.EARNINGS, pieceId);
        if (index == null && detail == null)
        {
            return null;
        }
        ObjectNode out = OptionJson.MAPPER.createObjectNode();
        out.set("piece", detail == null ? null : detail.get("piece"));
        out.set("mainPreview", detail == null ? null : detail.get("mainPreview"));
        out.set("refs", index == null ? OptionJson.MAPPER.createArrayNode() : index.path("refs"));
        out.set("previews", index == null ? OptionJson.MAPPER.createArrayNode() : index.path("previews"));
        out.put("missing", detail == null ? pieceId : null);
        out.put("memberValid", detail == null || detail.path("memberValid").asBoolean(true));
        out.put("loadFailed", false);
        out.set("access", detail == null ? null : detail.get("access"));
        return body(out);
    }

    @Override
    public FeedBody getNotes(String id)
    {
        if (StringUtils.isNotEmpty(id))
        {
            validateBizKey(OptionInfo.NOTES, id);
        }
        JsonNode index = readJson(OptionInfo.NOTES, OptionInfo.INDEX_KEY);
        if (index == null)
        {
            return null;
        }
        ArrayNode notes = OptionJson.MAPPER.createArrayNode();
        JsonNode selected = null;
        for (JsonNode noteId : index.path("ids"))
        {
            JsonNode note = readJson(OptionInfo.NOTES, noteId.asText());
            if (note != null)
            {
                notes.add(note);
                if (noteId.asText().equals(id))
                {
                    selected = note;
                }
            }
        }
        ObjectNode out = OptionJson.MAPPER.createObjectNode();
        out.put("noteId", selected == null ? null : id);
        out.set("note", selected);
        out.set("notes", notes);
        out.put("missing", StringUtils.isNotEmpty(id) && selected == null ? id : null);
        out.put("memberValid", true);
        return body(out);
    }

    @Override
    public List<OptionInfoSync> selectSyncList(OptionInfoSync sync)
    {
        return infoMapper.selectInfoSyncList(sync);
    }

    @Override
    public List<OptionInfoVersion> selectVersionList(String info, String bizKey)
    {
        return infoMapper.selectVersionList(requireInfo(info).getTableName(), bizKey);
    }

    @Override
    public OptionInfoVersion selectVersion(String info, Long versionId)
    {
        return infoMapper.selectVersionById(requireInfo(info).getTableName(), versionId);
    }

    /**
     * JSON 接口：整份响应存一行
     */
    private void syncJson(Context ctx, Map<String, Object> result)
    {
        long start = System.currentTimeMillis();
        OsmClient.OsmResponse response = osmClient.get(ctx.spec.getPath(), ctx.cookie, null);
        String body = check(ctx, OptionInfo.INDEX_KEY, response, start);
        JsonNode root;
        try
        {
            root = OptionJson.read(body);
        }
        catch (ServiceException e)
        {
            throw fail(ctx, OptionInfo.INDEX_KEY, response, "返回内容不是有效 JSON", start);
        }
        Stored stored = store(ctx, OptionInfo.INDEX_KEY, root);
        log(ctx, OptionInfo.INDEX_KEY, response, stored, null, start);
        result.put("result", stored.changed ? "UPDATED" : "UNCHANGED");
        result.put("versionId", stored.versionId);
        result.put("costMs", System.currentTimeMillis() - start);
    }

    /**
     * 页面路由（盘前简报 / 财报解读）：同步指定一篇；未指定时同步最新一篇并补齐缺失的历史
     */
    private void syncPage(Context ctx, String bizKey, Map<String, Object> result)
    {
        long start = System.currentTimeMillis();
        PageResult first = fetchPage(ctx, bizKey);
        List<Object> synced = new ArrayList<>();
        synced.add(first.summary());
        int updated = first.changed ? 1 : 0;
        if (StringUtils.isEmpty(bizKey))
        {
            Set<String> existing = new HashSet<>(infoMapper.selectBizKeys(ctx.spec.getTableName()));
            List<String> missing = new ArrayList<>();
            for (String candidate : first.indexKeys)
            {
                if (!existing.contains(candidate) && !candidate.equals(first.bizKey) && missing.size() < MAX_BACKFILL)
                {
                    missing.add(candidate);
                }
            }
            for (String candidate : missing)
            {
                sleepQuietly();
                try
                {
                    PageResult page = fetchPage(ctx, candidate);
                    updated += page.changed ? 1 : 0;
                    synced.add(page.summary());
                }
                catch (ServiceException e)
                {
                    synced.add(Map.of("bizKey", candidate, "result", "FAILED", "message", e.getMessage()));
                    if (sessionService.getCookie() == null)
                    {
                        break;
                    }
                }
            }
        }
        result.put("result", updated > 0 ? "UPDATED" : "UNCHANGED");
        result.put("latest", first.bizKey);
        result.put("total", first.indexKeys.size());
        result.put("pages", synced);
        result.put("costMs", System.currentTimeMillis() - start);
    }

    private PageResult fetchPage(Context ctx, String bizKey)
    {
        long start = System.currentTimeMillis();
        String path = ctx.spec.getPath() + (StringUtils.isEmpty(bizKey) ? "" : "?" + ctx.spec.getParam() + "=" + bizKey);
        String logKey = StringUtils.isEmpty(bizKey) ? OptionInfo.INDEX_KEY : bizKey;
        OsmClient.OsmResponse response = osmClient.getPage(path, ctx.cookie);
        String html = check(ctx, logKey, response, start);
        ObjectNode props;
        try
        {
            props = OptionPageParser.pageProps(html, ctx.spec.getMarker());
        }
        catch (ServiceException e)
        {
            throw fail(ctx, logKey, response, "页面数据解析失败：" + e.getMessage(), start);
        }
        if (props == null)
        {
            throw fail(ctx, logKey, response, "页面未包含数据（页面结构变化或会话已失效）", start);
        }
        if (props.path("loadFailed").asBoolean(false))
        {
            throw fail(ctx, logKey, response, "OSM 页面加载失败（loadFailed）", start);
        }
        JsonNode data = props.get(ctx.spec.getMarker());
        if (data == null || data.isNull())
        {
            throw fail(ctx, logKey, response, "OSM 无该篇内容（missing=" + props.path("missing").asText("") + "）", start);
        }

        PageResult page = new PageResult();
        ObjectNode index = OptionJson.MAPPER.createObjectNode();
        ObjectNode detail = props.deepCopy();
        detail.remove(List.of("missing", "loadFailed"));
        if (ctx.spec == OptionInfo.PREMARKET)
        {
            page.bizKey = data.path("date").asText();
            index.set("dates", props.path("dates"));
            detail.remove("dates");
        }
        else
        {
            page.bizKey = data.path("id").asText();
            index.set("refs", props.path("refs"));
            index.set("previews", props.path("previews"));
            detail.remove(List.of("refs", "previews"));
        }
        if (StringUtils.isEmpty(page.bizKey))
        {
            throw fail(ctx, logKey, response, "页面数据缺少业务键", start);
        }
        Stored stored = store(ctx, page.bizKey, detail);
        page.changed = stored.changed;
        page.versionId = stored.versionId;

        JsonNode current = readJson(ctx.spec, OptionInfo.INDEX_KEY);
        String latest = StringUtils.isEmpty(bizKey) || current == null ? page.bizKey
                : current.path("latest").asText(page.bizKey);
        index.put("latest", latest);
        store(ctx, OptionInfo.INDEX_KEY, index);
        for (JsonNode item : ctx.spec == OptionInfo.PREMARKET ? index.path("dates") : index.path("refs"))
        {
            String candidate = ctx.spec == OptionInfo.PREMARKET ? item.asText() : item.path("id").asText();
            if (StringUtils.isNotEmpty(candidate) && isSafeKey(ctx.spec, candidate))
            {
                page.indexKeys.add(candidate);
            }
        }
        log(ctx, page.bizKey, response, stored, null, start);
        return page;
    }

    /**
     * 札记：列表随页面脚本下发，逐篇存一行
     */
    private void syncNotes(Context ctx, Map<String, Object> result)
    {
        long start = System.currentTimeMillis();
        OsmClient.OsmResponse page = osmClient.getPage(ctx.spec.getPath(), ctx.cookie);
        String html = check(ctx, OptionInfo.INDEX_KEY, page, start);
        String bundle = OptionPageParser.notesBundlePath(html);
        if (bundle == null)
        {
            throw fail(ctx, OptionInfo.INDEX_KEY, page, "札记页面未找到 NotesClient 脚本", start);
        }
        OsmClient.OsmResponse script = osmClient.getPage(bundle, ctx.cookie);
        String js = check(ctx, OptionInfo.INDEX_KEY, script, start);
        ArrayNode notes;
        try
        {
            notes = OptionPageParser.notes(js);
        }
        catch (ServiceException e)
        {
            throw fail(ctx, OptionInfo.INDEX_KEY, script, e.getMessage(), start);
        }
        if (notes == null || notes.isEmpty())
        {
            throw fail(ctx, OptionInfo.INDEX_KEY, script, "札记脚本中未找到札记列表", start);
        }
        ArrayNode ids = OptionJson.MAPPER.createArrayNode();
        int updated = 0;
        String base = osmClient.getBaseUrl();
        for (JsonNode item : notes)
        {
            String id = item.path("id").asText();
            if (!isSafeKey(OptionInfo.NOTES, id))
            {
                continue;
            }
            ObjectNode note = ((ObjectNode) item).deepCopy();
            note.put("image", base + "/notes/" + id + ".webp");
            note.put("cover", base + "/notes/" + id + "-cover.webp");
            note.put("href", "/member/notes?id=" + id);
            updated += store(ctx, id, note).changed ? 1 : 0;
            ids.add(id);
        }
        ObjectNode index = OptionJson.MAPPER.createObjectNode();
        index.set("ids", ids);
        index.put("bundle", bundle);
        Stored stored = store(ctx, OptionInfo.INDEX_KEY, index);
        log(ctx, OptionInfo.INDEX_KEY, script, stored, "札记 " + ids.size() + " 篇，新增/变化 " + updated + " 篇", start);
        result.put("result", updated > 0 || stored.changed ? "UPDATED" : "UNCHANGED");
        result.put("total", ids.size());
        result.put("updated", updated);
        result.put("costMs", System.currentTimeMillis() - start);
    }

    /**
     * 校验响应：会话失效时暂停同步
     */
    private String check(Context ctx, String bizKey, OsmClient.OsmResponse response, long start)
    {
        if (response.status() == 401 || response.status() == 403)
        {
            String message = "OSM 会话已失效（HTTP " + response.status() + "），同步已暂停，请重新登录";
            sessionService.expire(message);
            throw fail(ctx, bizKey, response, message, start);
        }
        if (response.status() != 200 || StringUtils.isEmpty(response.body()))
        {
            throw fail(ctx, bizKey, response, "OSM 返回异常（HTTP " + response.status() + "）"
                    + (response.retryAfter() == null ? "" : "，Retry-After " + response.retryAfter() + " 秒"), start);
        }
        return response.body();
    }

    /**
     * 存版本表（内容未变化只更新最近命中时间）→ 写 Redis
     */
    private Stored store(Context ctx, String bizKey, JsonNode root)
    {
        String payload = OptionJson.write(root);
        String hash = OptionJson.sha256(payload);
        String table = ctx.spec.getTableName();
        Long versionId = infoMapper.selectVersionIdByHash(table, bizKey, hash);
        boolean changed = versionId == null;
        if (changed)
        {
            OptionInfoVersion version = new OptionInfoVersion();
            version.setBizKey(bizKey);
            version.setContentHash(hash);
            version.setPayload(payload);
            version.setColumns(extractColumns(ctx.spec, bizKey, root));
            version.setCreateBy(ctx.operator);
            version.setRemark(OptionInfo.INDEX_KEY.equals(bizKey) && ctx.spec.getSource() != OptionInfo.Source.JSON
                    ? "列表索引" : null);
            infoMapper.insertVersion(table, version);
            versionId = version.getVersionId();
        }
        else
        {
            infoMapper.touchVersion(table, versionId, ctx.operator);
        }
        writeRedis(ctx.spec, bizKey, payload);
        return new Stored(versionId, changed, payload.length());
    }

    private void log(Context ctx, String bizKey, OsmClient.OsmResponse response, Stored stored, String message,
            long start)
    {
        OptionInfoSync record = record(ctx, bizKey, response);
        record.setResult(stored.changed ? "UPDATED" : "UNCHANGED");
        record.setVersionId(stored.versionId);
        record.setCostMs((int) (System.currentTimeMillis() - start));
        record.setMessage(message != null ? message : stored.changed ? "内容有变化，新增版本" : "内容未变化");
        infoMapper.insertInfoSync(record);
        log.info("{}[{}] 同步完成：{}，版本 {}，{} 字节", ctx.spec.getKey(), bizKey, record.getResult(),
                stored.versionId, stored.bytes);
    }

    private ServiceException fail(Context ctx, String bizKey, OsmClient.OsmResponse response, String message,
            long start)
    {
        OptionInfoSync record = record(ctx, bizKey, response);
        record.setResult("FAILED");
        record.setMessage(StringUtils.substring(message, 0, 500));
        record.setCostMs((int) (System.currentTimeMillis() - start));
        infoMapper.insertInfoSync(record);
        log.warn("{}[{}] 同步失败：{}", ctx.spec.getKey(), bizKey, message);
        return new ServiceException(ctx.spec.getKey() + "：" + message);
    }

    private OptionInfoSync record(Context ctx, String bizKey, OsmClient.OsmResponse response)
    {
        OptionInfoSync record = new OptionInfoSync();
        record.setInfoKey(ctx.spec.getKey());
        record.setBizKey(StringUtils.substring(bizKey, 0, 64));
        record.setHttpStatus(response == null ? null : response.status());
        record.setRawBytes(response == null ? null : response.transferBytes());
        record.setTriggerType(ctx.trigger);
        record.setCreateBy(ctx.operator);
        return record;
    }

    private List<OptionColumnValue> extractColumns(OptionInfo spec, String bizKey, JsonNode root)
    {
        boolean index = OptionInfo.INDEX_KEY.equals(bizKey) && spec.getSource() != OptionInfo.Source.JSON;
        List<OptionColumnValue> columns = new ArrayList<>();
        for (OptionInfo.Column column : spec.getColumns())
        {
            String pointer = column.pointer();
            String value = null;
            if (!index && pointer.endsWith("#size"))
            {
                JsonNode node = root.at(pointer.substring(0, pointer.length() - 5));
                value = node.isArray() || node.isObject() ? String.valueOf(node.size()) : null;
            }
            else if (!index)
            {
                JsonNode node = root.at(pointer);
                if (!node.isMissingNode() && !node.isNull() && !node.isContainerNode())
                {
                    value = node.isBoolean() ? (node.asBoolean() ? "1" : "0")
                            : StringUtils.substring(node.asText(), 0, column.maxLength());
                }
            }
            columns.add(new OptionColumnValue(column.name(), value));
        }
        return columns;
    }

    /**
     * 读一行内容：Redis 优先，未命中从表回填
     */
    private String readRow(OptionInfo spec, String bizKey)
    {
        String key = redisKey(spec, bizKey);
        String body = stringRedisTemplate.opsForValue().get(key);
        if (body == null)
        {
            OptionInfoVersion latest = infoMapper.selectLatestVersion(spec.getTableName(), bizKey);
            if (latest == null)
            {
                return null;
            }
            log.info("Redis 未命中 {}，从 {} 回填版本 {}", key, spec.getTableName(), latest.getVersionId());
            body = latest.getPayload();
            writeRedis(spec, bizKey, body);
        }
        return body;
    }

    private JsonNode readJson(OptionInfo spec, String bizKey)
    {
        String body = readRow(spec, bizKey);
        return body == null ? null : OptionJson.read(body);
    }

    private void writeRedis(OptionInfo spec, String bizKey, String body)
    {
        if (OptionInfo.INDEX_KEY.equals(bizKey))
        {
            stringRedisTemplate.opsForValue().set(redisKey(spec, bizKey), body);
        }
        else
        {
            stringRedisTemplate.opsForValue().set(redisKey(spec, bizKey), body, DETAIL_TTL);
        }
    }

    private void updateStatus(OptionInfo spec, Map<String, Object> result)
    {
        Map<String, Object> status = new LinkedHashMap<>(result);
        status.remove("pages");
        status.put("updatedAt", DateUtils.getTime());
        redisCache.setCacheMapValue(STATUS_KEY, spec.getKey(), status);
    }

    private static FeedBody body(JsonNode node)
    {
        String body = OptionJson.write(node);
        return new FeedBody(body, OptionJson.etag(body), null, null);
    }

    private static void validateBizKey(OptionInfo spec, String bizKey)
    {
        if (spec.getSource() == OptionInfo.Source.JSON)
        {
            throw new ServiceException(spec.getKey() + " 不区分业务键");
        }
        if (!isSafeKey(spec, bizKey))
        {
            throw new ServiceException(spec == OptionInfo.PREMARKET ? "d 必须为 yyyy-MM-dd" : "id 格式不正确");
        }
    }

    private static boolean isSafeKey(OptionInfo spec, String bizKey)
    {
        if (spec == OptionInfo.PREMARKET)
        {
            if (!DATE.matcher(bizKey).matches())
            {
                return false;
            }
            try
            {
                LocalDate.parse(bizKey);
                return true;
            }
            catch (Exception e)
            {
                return false;
            }
        }
        return SLUG.matcher(bizKey).matches();
    }

    private static String redisKey(OptionInfo spec, String bizKey)
    {
        return KEY_PREFIX + spec.getKey() + ":" + bizKey;
    }

    private static OptionInfo requireInfo(String info)
    {
        OptionInfo spec = OptionInfo.of(info);
        if (spec == null)
        {
            throw new ServiceException("不支持的数据源：" + info);
        }
        return spec;
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

    /** 同步上下文 */
    private record Context(OptionInfo spec, String cookie, String trigger, String operator)
    {
    }

    /** 存储结果 */
    private record Stored(Long versionId, boolean changed, int bytes)
    {
    }

    /** 单页同步结果 */
    private static class PageResult
    {
        private String bizKey;

        private boolean changed;

        private Long versionId;

        private final List<String> indexKeys = new ArrayList<>();

        private Map<String, Object> summary()
        {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("bizKey", bizKey);
            map.put("result", changed ? "UPDATED" : "UNCHANGED");
            map.put("versionId", versionId);
            return map;
        }
    }
}
