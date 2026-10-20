package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.client.OsmClient;
import com.ruoyi.option.domain.OptionColumnValue;
import com.ruoyi.option.domain.OptionResourceSync;
import com.ruoyi.option.domain.OptionResourceVersion;
import com.ruoyi.option.enums.OptionResource;
import com.ruoyi.option.mapper.OptionResourceMapper;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionCloseSnapshotService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionResourceService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionAccessContext;
import com.ruoyi.option.utils.OptionCacheKeys;
import com.ruoyi.option.utils.OptionJson;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * OSM 会员接口（feed 之外）同步与读取 服务层实现
 * 
 * <pre>
 * Redis：option:res:{resource}            接口原始 JSON（seller-guide、entitlement、decisions、
 *                                          management-choices、updates、support）
 *        option:res:premarket:{yyyy-MM-dd} 盘前简报（保留 7 天，过期后从表回填）
 *        option:res:sync:status            各接口最近一次同步状态（Hash）
 * </pre>
 * 
 * @author yscandice
 */
@Service
public class OptionResourceServiceImpl implements IOptionResourceService
{
    private static final Logger log = LoggerFactory.getLogger(OptionResourceServiceImpl.class);

    private static final String KEY_PREFIX = "option:res:";

    private static final String STATUS_KEY = "option:res:sync:status";

    private static final Duration DATED_TTL = Duration.ofDays(7);

    /** 连续请求多个接口时的间隔，避免触发 OSM 限流 */
    private static final long REQUEST_GAP_MILLIS = 800;

    @Autowired
    private OsmClient osmClient;

    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private OptionMarketCalendar marketCalendar;

    @Autowired
    private OptionResourceMapper resourceMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private IOptionCloseSnapshotService closeSnapshotService;

    @Autowired
    private IOptionAccessService accessService;

    @Override
    public Map<String, Object> sync(String resource, String date, boolean force, String trigger)
    {
        OptionResource spec = requireResource(resource);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resource", spec.getKey());
        if (!force && !inWindow(spec))
        {
            result.put("result", "SKIPPED");
            result.put("message", "不在同步时段（" + spec.getWindow() + "）");
            return result;
        }
        String bizKey = bizKey(spec, date);
        result.put("bizKey", bizKey);
        String cookie = sessionService.getCookie();
        if (cookie == null)
        {
            result.put("result", "PAUSED");
            result.put("message", "OSM 会话未登录或已过期，请重新登录后再同步");
            updateStatus(spec, result);
            log.warn("{} 同步暂停：OSM 会话未登录", spec.getKey());
            return result;
        }

        long start = System.currentTimeMillis();
        String path = spec.getPath() + (spec.isDated() ? "?d=" + bizKey : "");
        OsmClient.OsmResponse response = osmClient.get(path, cookie, null);
        OptionResourceSync record = new OptionResourceSync();
        record.setResourceKey(spec.getKey());
        record.setBizKey(bizKey);
        record.setHttpStatus(response.status());
        record.setRawBytes(response.transferBytes());
        record.setTriggerType(trigger);

        if (response.status() == 401 || response.status() == 403)
        {
            String message = "OSM 会话已失效（HTTP " + response.status() + "），同步已暂停，请重新登录";
            sessionService.expire(message);
            fail(spec, record, result, message, start);
            throw new ServiceException(spec.getKey() + "：" + message);
        }
        if (response.status() != 200 || StringUtils.isEmpty(response.body()))
        {
            String message = "OSM 返回异常（HTTP " + response.status() + "）"
                    + (response.retryAfter() == null ? "" : "，Retry-After " + response.retryAfter() + " 秒");
            fail(spec, record, result, message, start);
            throw new ServiceException(spec.getKey() + "：" + message);
        }
        JsonNode root;
        try
        {
            root = OptionJson.read(response.body());
        }
        catch (ServiceException e)
        {
            fail(spec, record, result, "返回内容不是有效 JSON", start);
            throw new ServiceException(spec.getKey() + "：返回内容不是有效 JSON");
        }
        store(spec, bizKey, root, record, result, start);
        return result;
    }

    @Override
    public Map<String, Object> syncAll(String trigger)
    {
        Map<String, Object> results = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (OptionResource spec : OptionResource.values())
        {
            try
            {
                results.put(spec.getKey(), sync(spec.getKey(), null, true, trigger));
            }
            catch (ServiceException e)
            {
                failed.add(e.getMessage());
                results.put(spec.getKey(), Map.of("result", "FAILED", "message", e.getMessage()));
                if (sessionService.getCookie() == null)
                {
                    break;
                }
            }
            sleepQuietly();
        }
        if (!failed.isEmpty())
        {
            log.warn("接口同步部分失败：{}", failed);
        }
        return results;
    }

    @Override
    public FeedBody get(String resource, String date)
    {
        OptionResource spec = requireResource(resource);
        String bizKey = bizKey(spec, date);
        String key = redisKey(spec, bizKey);
        String body = stringRedisTemplate.opsForValue().get(key);
        if (body == null)
        {
            OptionResourceVersion latest = resourceMapper.selectLatestVersion(spec.getTableName(), bizKey);
            if (latest == null)
            {
                return null;
            }
            log.info("Redis 未命中 {}，从 {} 回填版本 {}", key, spec.getTableName(), latest.getVersionId());
            body = latest.getPayload();
            writeRedis(spec, key, body);
        }
        return new FeedBody(body, OptionJson.etag(body), null, null);
    }

    @Override
    public FeedBody getView(String resource, String date)
    {
        if (!OptionAccessContext.isDelayed())
        {
            return get(resource, date);
        }
        OptionResource spec = requireResource(resource);
        String key = OptionCacheKeys.delayed(redisKey(spec, bizKey(spec, date)));
        String body = stringRedisTemplate.opsForValue().get(key);
        if (body == null && OptionAccessContext.hasRelease() && closeSnapshotService.restoreIfMissing())
        {
            body = stringRedisTemplate.opsForValue().get(key);
        }
        if (body != null)
        {
            return new FeedBody(body, OptionJson.etag(body), null, null);
        }
        if (accessService.isDelayedFallbackRealtime())
        {
            log.warn("盘后视图缺少 {}，按 delayedFallback=REALTIME 回落实时", key);
            return get(resource, date);
        }
        // 只借用实时数据的结构，不带任何实际值
        FeedBody realtime = get(resource, date);
        String empty = OptionJson.emptyStructure(realtime == null ? null : realtime.body());
        OptionAccessContext.markEmpty();
        return new FeedBody(empty, OptionJson.etag(empty), null, null);
    }

    @Override
    public Map<String, Object> importPayload(String resource, String date, String payload)
    {
        OptionResource spec = requireResource(resource);
        String bizKey = bizKey(spec, date);
        JsonNode root = OptionJson.read(payload);
        OptionResourceSync record = new OptionResourceSync();
        record.setResourceKey(spec.getKey());
        record.setBizKey(bizKey);
        record.setHttpStatus(200);
        record.setRawBytes(payload.length());
        record.setTriggerType("IMPORT");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resource", spec.getKey());
        result.put("bizKey", bizKey);
        store(spec, bizKey, root, record, result, System.currentTimeMillis());
        return result;
    }

    @Override
    public List<OptionResourceSync> selectSyncList(OptionResourceSync sync)
    {
        return resourceMapper.selectResourceSyncList(sync);
    }

    @Override
    public List<OptionResourceVersion> selectVersionList(String resource, String bizKey)
    {
        return resourceMapper.selectVersionList(requireResource(resource).getTableName(), bizKey);
    }

    @Override
    public OptionResourceVersion selectVersion(String resource, Long versionId)
    {
        return resourceMapper.selectVersionById(requireResource(resource).getTableName(), versionId);
    }

    /**
     * 存版本表（内容未变化只更新最后出现时间）→ 写同步记录 → 写 Redis
     */
    private void store(OptionResource spec, String bizKey, JsonNode root, OptionResourceSync record,
            Map<String, Object> result, long start)
    {
        String payload = OptionJson.write(root);
        String hash = OptionJson.sha256(payload);
        Long versionId = resourceMapper.selectVersionIdByHash(spec.getTableName(), bizKey, hash);
        boolean changed = versionId == null;
        if (changed)
        {
            OptionResourceVersion version = new OptionResourceVersion();
            version.setBizKey(bizKey);
            version.setContentHash(hash);
            version.setPayload(payload);
            version.setColumns(extractColumns(spec, root));
            resourceMapper.insertVersion(spec.getTableName(), version);
            versionId = version.getVersionId();
        }
        else
        {
            resourceMapper.touchVersion(spec.getTableName(), versionId);
        }
        writeRedis(spec, redisKey(spec, bizKey), payload);

        record.setResult(changed ? "UPDATED" : "UNCHANGED");
        record.setVersionId(versionId);
        record.setCostMs((int) (System.currentTimeMillis() - start));
        record.setMessage(changed ? "内容有变化，新增版本" : "内容未变化");
        resourceMapper.insertResourceSync(record);

        result.put("result", record.getResult());
        result.put("versionId", versionId);
        result.put("bytes", payload.length());
        result.put("costMs", record.getCostMs());
        updateStatus(spec, result);
        log.info("{}[{}] 同步完成：{}，版本 {}，{} 字节", spec.getKey(), bizKey, record.getResult(), versionId,
                payload.length());
    }

    private void fail(OptionResource spec, OptionResourceSync record, Map<String, Object> result, String message,
            long start)
    {
        record.setResult("FAILED");
        record.setMessage(message);
        record.setCostMs((int) (System.currentTimeMillis() - start));
        resourceMapper.insertResourceSync(record);
        result.put("result", "FAILED");
        result.put("message", message);
        updateStatus(spec, result);
    }

    private List<OptionColumnValue> extractColumns(OptionResource spec, JsonNode root)
    {
        List<OptionColumnValue> columns = new ArrayList<>();
        for (Map.Entry<String, String> entry : spec.getColumns().entrySet())
        {
            String pointer = entry.getValue();
            String value;
            if (pointer.endsWith("#size"))
            {
                JsonNode node = root.at(pointer.substring(0, pointer.length() - 5));
                value = node.isArray() || node.isObject() ? String.valueOf(node.size()) : null;
            }
            else
            {
                JsonNode node = root.at(pointer);
                if (node.isMissingNode() || node.isNull() || node.isContainerNode())
                {
                    value = null;
                }
                else if (node.isBoolean())
                {
                    value = node.asBoolean() ? "1" : "0";
                }
                else
                {
                    value = StringUtils.substring(node.asText(), 0, 64);
                }
            }
            columns.add(new OptionColumnValue(entry.getKey(), value));
        }
        return columns;
    }

    private void writeRedis(OptionResource spec, String key, String body)
    {
        if (spec.isDated())
        {
            stringRedisTemplate.opsForValue().set(key, body, DATED_TTL);
        }
        else
        {
            stringRedisTemplate.opsForValue().set(key, body);
        }
    }

    private void updateStatus(OptionResource spec, Map<String, Object> result)
    {
        Map<String, Object> status = new LinkedHashMap<>(result);
        status.put("updatedAt", DateUtils.getTime());
        redisCache.setCacheMapValue(STATUS_KEY, spec.getKey(), status);
    }

    private boolean inWindow(OptionResource spec)
    {
        switch (spec.getWindow())
        {
            case TRADING:
                return marketCalendar.isFeedSyncWindow();
            case PREMARKET:
                return marketCalendar.isPremarketWindow();
            default:
                return true;
        }
    }

    private String bizKey(OptionResource spec, String date)
    {
        if (!spec.isDated())
        {
            return OptionResource.DEFAULT_BIZ_KEY;
        }
        if (StringUtils.isEmpty(date))
        {
            return marketCalendar.todayEt();
        }
        try
        {
            return LocalDate.parse(date).toString();
        }
        catch (Exception e)
        {
            throw new ServiceException("d 必须为 yyyy-MM-dd");
        }
    }

    private static String redisKey(OptionResource spec, String bizKey)
    {
        return KEY_PREFIX + spec.getKey() + (spec.isDated() ? ":" + bizKey : "");
    }

    private static OptionResource requireResource(String resource)
    {
        OptionResource spec = OptionResource.of(resource);
        if (spec == null)
        {
            throw new ServiceException("不支持的接口：" + resource);
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
}
