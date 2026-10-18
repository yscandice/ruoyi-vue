package com.ruoyi.option.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.option.constant.OptionFeedConstants;

/**
 * feed 同步状态（存于 Redis，供管理端查看与前端提示）
 * 
 * @author yscandice
 */
public class OptionSyncStatus
{
    private OptionSyncStatus()
    {
    }

    /**
     * 读取状态
     * 
     * @param redisCache Redis
     * @return 状态
     */
    public static Map<String, Object> get(RedisCache redisCache)
    {
        Map<String, Object> status = redisCache.getCacheMap(OptionFeedConstants.SYNC_STATUS_KEY);
        return status == null ? new LinkedHashMap<>() : status;
    }

    /**
     * 更新状态
     * 
     * @param redisCache Redis
     * @param state 状态
     * @param message 说明
     */
    public static void update(RedisCache redisCache, String state, String message)
    {
        update(redisCache, state, message, null);
    }

    /**
     * 更新状态
     * 
     * @param redisCache Redis
     * @param state 状态
     * @param message 说明
     * @param extra 额外字段
     */
    public static void update(RedisCache redisCache, String state, String message, Map<String, Object> extra)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("state", state);
        values.put("message", message);
        values.put("updatedAt", DateUtils.getTime());
        if (extra != null)
        {
            values.putAll(extra);
        }
        redisCache.setCacheMap(OptionFeedConstants.SYNC_STATUS_KEY, values);
    }
}
