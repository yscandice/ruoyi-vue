package com.ruoyi.option.service.impl;

import java.time.Duration;
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
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionFeedSnapshot;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.utils.OptionJson;

/**
 * feed Redis 缓存 服务层实现
 * 
 * <pre>
 * option:feed:current                   当前版本元数据 {syncId, generatedAt, syncTime, etags}
 * option:feed:data:{syncId}:full        完整 feed JSON
 * option:feed:data:{syncId}:{group}     分组 JSON（meta/desk/signal/management/settlement/history/health/session）
 * </pre>
 * 
 * @author yscandice
 */
@Service
public class OptionFeedCacheServiceImpl implements IOptionFeedCacheService
{
    private static final Logger log = LoggerFactory.getLogger(OptionFeedCacheServiceImpl.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OptionFeedAssembler assembler;

    @Override
    public void publish(OptionFeedSnapshot snapshot)
    {
        Duration ttl = Duration.ofSeconds(OptionFeedConstants.DATA_TTL_SECONDS);
        for (Map.Entry<String, String> entry : snapshot.getBodies().entrySet())
        {
            stringRedisTemplate.opsForValue().set(dataKey(snapshot.getSyncId(), entry.getKey()), entry.getValue(), ttl);
        }
        Long previous = currentSyncId();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("syncId", snapshot.getSyncId());
        meta.put("generatedAt", snapshot.getGeneratedAt());
        meta.put("syncTime", snapshot.getSyncTime());
        meta.put("etags", snapshot.getEtags());
        stringRedisTemplate.opsForValue().set(OptionFeedConstants.CURRENT_KEY, OptionJson.write(meta));
        if (previous != null && !previous.equals(snapshot.getSyncId()))
        {
            expireVersion(previous);
        }
    }

    @Override
    public Map<String, Object> getMeta()
    {
        JsonNode meta = readCurrent();
        if (meta == null)
        {
            OptionFeedSnapshot snapshot = rebuild(null);
            if (snapshot == null)
            {
                return null;
            }
            meta = readCurrent();
            if (meta == null)
            {
                return toMeta(snapshot);
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = OptionJson.MAPPER.convertValue(meta, LinkedHashMap.class);
        return result;
    }

    @Override
    public FeedBody get(String group)
    {
        JsonNode meta = readCurrent();
        if (meta == null)
        {
            OptionFeedSnapshot snapshot = rebuild(null);
            return snapshot == null ? null : fromSnapshot(snapshot, group);
        }
        Long syncId = meta.get("syncId").asLong();
        String generatedAt = meta.path("generatedAt").asText(null);
        JsonNode etag = meta.path("etags").get(group);
        if (etag == null)
        {
            return null;
        }
        String body = stringRedisTemplate.opsForValue().get(dataKey(syncId, group));
        if (body != null)
        {
            return new FeedBody(body, etag.asText(), syncId, generatedAt);
        }
        log.info("Redis 未命中 feed 分组 {}（批次 {}），从多表拼接回填", group, syncId);
        OptionFeedSnapshot snapshot = rebuild(syncId);
        return snapshot == null ? null : fromSnapshot(snapshot, group);
    }

    /**
     * 从多表拼接并回填；拿到锁的请求负责写 Redis，其余请求只拼接返回
     */
    private OptionFeedSnapshot rebuild(Long syncId)
    {
        OptionFeedSnapshot snapshot = syncId == null ? assembler.assembleLatest() : assembler.assemble(syncId);
        if (snapshot == null)
        {
            return null;
        }
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(OptionFeedConstants.REBUILD_LOCK_KEY, "1",
                Duration.ofSeconds(30));
        if (Boolean.TRUE.equals(locked))
        {
            try
            {
                Long current = currentSyncId();
                if (current == null || current <= snapshot.getSyncId())
                {
                    publish(snapshot);
                }
            }
            finally
            {
                stringRedisTemplate.delete(OptionFeedConstants.REBUILD_LOCK_KEY);
            }
        }
        return snapshot;
    }

    private FeedBody fromSnapshot(OptionFeedSnapshot snapshot, String group)
    {
        String body = snapshot.getBodies().get(group);
        return body == null ? null
                : new FeedBody(body, snapshot.getEtags().get(group), snapshot.getSyncId(), snapshot.getGeneratedAt());
    }

    private Map<String, Object> toMeta(OptionFeedSnapshot snapshot)
    {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("syncId", snapshot.getSyncId());
        meta.put("generatedAt", snapshot.getGeneratedAt());
        meta.put("syncTime", snapshot.getSyncTime());
        meta.put("etags", snapshot.getEtags());
        return meta;
    }

    private JsonNode readCurrent()
    {
        String text = stringRedisTemplate.opsForValue().get(OptionFeedConstants.CURRENT_KEY);
        return text == null ? null : OptionJson.read(text);
    }

    private Long currentSyncId()
    {
        JsonNode meta = readCurrent();
        return meta == null ? null : meta.get("syncId").asLong();
    }

    private void expireVersion(Long syncId)
    {
        List<String> keys = new ArrayList<>();
        keys.add(dataKey(syncId, OptionFeedConstants.GROUP_FULL));
        OptionFeedConstants.GROUPS.keySet().forEach(g -> keys.add(dataKey(syncId, g)));
        keys.add(dataKey(syncId, OptionFeedConstants.GROUP_EXTRA));
        Duration ttl = Duration.ofSeconds(OptionFeedConstants.OLD_DATA_TTL_SECONDS);
        keys.forEach(key -> stringRedisTemplate.expire(key, ttl));
    }

    private static String dataKey(Long syncId, String group)
    {
        return OptionFeedConstants.DATA_KEY_PREFIX + syncId + ":" + group;
    }
}
