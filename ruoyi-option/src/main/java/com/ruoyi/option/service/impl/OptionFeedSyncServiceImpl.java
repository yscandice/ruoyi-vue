package com.ruoyi.option.service.impl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.client.OsmClient;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionFeedItem;
import com.ruoyi.option.domain.OptionFeedSnapshot;
import com.ruoyi.option.domain.OptionFeedSync;
import com.ruoyi.option.enums.OptionFeedList;
import com.ruoyi.option.mapper.OptionFeedItemMapper;
import com.ruoyi.option.mapper.OptionFeedSyncMapper;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionJson;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * feed 同步 服务层实现
 * 
 * @author yscandice
 */
@Service
public class OptionFeedSyncServiceImpl implements IOptionFeedSyncService
{
    private static final Logger log = LoggerFactory.getLogger(OptionFeedSyncServiceImpl.class);

    @Autowired
    private OsmClient osmClient;

    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private IOptionFeedCacheService cacheService;

    @Autowired
    private OptionFeedStorage storage;

    @Autowired
    private OptionFeedAssembler assembler;

    @Autowired
    private OptionMarketCalendar marketCalendar;

    @Autowired
    private OptionFeedSyncMapper syncMapper;

    @Autowired
    private OptionFeedItemMapper itemMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Object> syncFeed(boolean force)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!force && !marketCalendar.isFeedSyncWindow())
        {
            result.put("result", "SKIPPED");
            result.put("message", "非美东交易时段，使用 Redis 缓存");
            return result;
        }
        String cookie = sessionService.getCookie();
        if (cookie == null)
        {
            String message = "OSM 会话未登录或已过期，请重新登录后再同步";
            OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_PAUSED, message);
            log.warn(message);
            result.put("result", "PAUSED");
            result.put("message", message);
            return result;
        }
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(OptionFeedConstants.SYNC_LOCK_KEY,
                DateUtils.getTime(), Duration.ofSeconds(OptionFeedConstants.SYNC_LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked))
        {
            result.put("result", "SKIPPED");
            result.put("message", "上一次同步尚未结束");
            return result;
        }
        try
        {
            return doSync(cookie, force, result);
        }
        catch (RuntimeException e)
        {
            // 会话失效时已标记为 PAUSED（需手动重新登录），不覆盖为 ERROR
            if (sessionService.getCookie() != null)
            {
                OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_ERROR, e.getMessage());
            }
            throw e;
        }
        finally
        {
            stringRedisTemplate.delete(OptionFeedConstants.SYNC_LOCK_KEY);
        }
    }

    private Map<String, Object> doSync(String cookie, boolean force, Map<String, Object> result)
    {
        long start = System.currentTimeMillis();
        OptionFeedSync latest = syncMapper.selectLatestOptionFeedSync();
        String lastEtag = latest == null ? null : latest.getUpstreamEtag();
        // 强制同步同样携带 ETag：OSM 对不带 If-None-Match 的轮询会限流（429）
        OsmClient.OsmResponse response = osmClient.getFeed(cookie, lastEtag);

        if (response.status() == 401 || response.status() == 403)
        {
            String message = "OSM 会话已失效（HTTP " + response.status() + "），同步已暂停，请重新登录";
            sessionService.expire(message);
            throw new ServiceException(message);
        }
        if (response.status() == 429)
        {
            throw new ServiceException("OSM 限流（HTTP 429），稍后重试" + (response.retryAfter() == null ? ""
                    : "（Retry-After " + response.retryAfter() + " 秒）"));
        }
        boolean notModified = response.status() == 304
                || (StringUtils.isNotEmpty(response.etag()) && response.etag().equals(lastEtag) && latest != null);
        if (notModified)
        {
            if (latest == null)
            {
                throw new ServiceException("上游返回 304，但本地没有任何同步数据，请使用强制同步");
            }
            // 确保 Redis 中有当前版本（未命中时会从多表拼接回填）
            cacheService.getMeta();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("lastPollAt", DateUtils.getTime());
            extra.put("lastSyncId", latest.getSyncId());
            OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_OK, "上游无变化（304）", extra);
            result.put("result", "NOT_MODIFIED");
            result.put("syncId", latest.getSyncId());
            return result;
        }
        if (response.status() != 200 || StringUtils.isEmpty(response.body()))
        {
            throw new ServiceException("OSM feed 返回异常（HTTP " + response.status() + "）");
        }
        JsonNode root = OptionJson.read(response.body());
        if (!root.isObject())
        {
            throw new ServiceException("OSM feed 返回的不是 JSON 对象");
        }

        OptionFeedSync sync = storage.store((ObjectNode) root, response.etag(), response.transferBytes(), start);
        OptionFeedSnapshot snapshot = assembler.assemble(sync.getSyncId());
        cacheService.publish(snapshot);

        long cost = System.currentTimeMillis() - start;
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("lastPollAt", DateUtils.getTime());
        extra.put("lastSuccessAt", DateUtils.getTime());
        extra.put("lastSyncId", sync.getSyncId());
        extra.put("changedGroups", sync.getChangedGroups());
        OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_OK, "同步成功", extra);
        log.info("feed 同步完成：批次 {}，条目 {}，新增版本 {}，变化分组 [{}]，耗时 {}ms", sync.getSyncId(),
                sync.getItemCount(), sync.getNewVersionCount(), sync.getChangedGroups(), cost);

        result.put("result", "UPDATED");
        result.put("syncId", sync.getSyncId());
        result.put("itemCount", sync.getItemCount());
        result.put("newVersionCount", sync.getNewVersionCount());
        result.put("changedGroups", sync.getChangedGroups());
        result.put("bytes", snapshot.getBodies().get(OptionFeedConstants.GROUP_FULL).length());
        result.put("costMs", cost);
        return result;
    }

    @Override
    public Map<String, Object> importFeed(String payload)
    {
        long start = System.currentTimeMillis();
        JsonNode root = OptionJson.read(payload);
        if (!root.isObject())
        {
            throw new ServiceException("feed 必须是 JSON 对象");
        }
        OptionFeedSync sync = storage.store((ObjectNode) root, null, payload.length(), start);
        OptionFeedSnapshot snapshot = assembler.assemble(sync.getSyncId());
        cacheService.publish(snapshot);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", "IMPORTED");
        result.put("syncId", sync.getSyncId());
        result.put("newVersionCount", sync.getNewVersionCount());
        return result;
    }

    @Override
    public List<OptionFeedSync> selectSyncList(OptionFeedSync sync)
    {
        return syncMapper.selectOptionFeedSyncList(sync);
    }

    @Override
    public List<OptionFeedItem> selectItemList(OptionFeedItem item)
    {
        OptionFeedList spec = item == null ? null : OptionFeedList.ofField(item.getListType());
        if (spec == null)
        {
            throw new ServiceException("listType 无效，可选：history、managementCards、protections、sentProtections、"
                    + "settlementCards、orderIntents、midtermShadowCards、midtermSettledCards、signals、buyerSignals、"
                    + "buyerAlerts、events");
        }
        return itemMapper.selectItemList(spec.getTableName(), item);
    }
}
