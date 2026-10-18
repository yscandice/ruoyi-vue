package com.ruoyi.option.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.client.OsmClient;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.service.IOptionSessionService;

/**
 * OSM 会话 服务层实现
 * 
 * @author yscandice
 */
@Service
public class OptionSessionServiceImpl implements IOptionSessionService
{
    private static final Logger log = LoggerFactory.getLogger(OptionSessionServiceImpl.class);

    @Autowired
    private OsmClient osmClient;

    @Autowired
    private RedisCache redisCache;

    @Override
    public int login(String email, String password)
    {
        OsmClient.OsmSession session = osmClient.login(email, password);
        redisCache.setCacheObject(OptionFeedConstants.COOKIE_KEY, session.cookie(), session.ttlSeconds(),
                TimeUnit.SECONDS);
        OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_OK, "OSM 已登录，等待下次同步");
        log.info("OSM 会话已建立，有效期 {} 秒", session.ttlSeconds());
        return session.ttlSeconds();
    }

    @Override
    public void logout()
    {
        redisCache.deleteObject(OptionFeedConstants.COOKIE_KEY);
        OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_PAUSED, "OSM 会话已退出，请重新登录");
    }

    @Override
    public String getCookie()
    {
        String cookie = redisCache.getCacheObject(OptionFeedConstants.COOKIE_KEY);
        return StringUtils.isEmpty(cookie) ? null : cookie;
    }

    @Override
    public void expire(String reason)
    {
        redisCache.deleteObject(OptionFeedConstants.COOKIE_KEY);
        OptionSyncStatus.update(redisCache, OptionFeedConstants.STATE_PAUSED, reason);
        log.warn("OSM 会话失效，同步已暂停：{}", reason);
    }

    @Override
    public Map<String, Object> getStatus()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean loggedIn = getCookie() != null;
        result.put("loggedIn", loggedIn);
        result.put("expireSeconds", loggedIn ? redisCache.getExpire(OptionFeedConstants.COOKIE_KEY) : 0);
        result.put("sync", OptionSyncStatus.get(redisCache));
        return result;
    }
}
