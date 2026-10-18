package com.ruoyi.quartz.task;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionResourceService;

/**
 * 期权 OSM 数据同步任务
 * 
 * 调用目标：optionSyncTask.syncFeed()（每分钟，仅美东交易时段 09:30-16:05 实际请求）
 *          optionSyncTask.syncFeedNow()（忽略交易时段，手动"执行一次"用）
 *          optionSyncTask.syncResource('decisions')（单个接口，按接口自身同步时段判断）
 *          optionSyncTask.syncAllResourcesNow()（全部 7 个接口，忽略同步时段）
 * 
 * @author yscandice
 */
@Component("optionSyncTask")
public class OptionSyncTask
{
    private static final Logger log = LoggerFactory.getLogger(OptionSyncTask.class);

    @Autowired
    private IOptionFeedSyncService feedSyncService;

    @Autowired
    private IOptionResourceService resourceService;

    /**
     * 交易时段同步 feed
     */
    public void syncFeed()
    {
        Map<String, Object> result = feedSyncService.syncFeed(false);
        log.debug("optionSyncTask.syncFeed {}", result);
    }

    /**
     * 立即同步 feed（忽略交易时段）
     */
    public void syncFeedNow()
    {
        Map<String, Object> result = feedSyncService.syncFeed(true);
        log.info("optionSyncTask.syncFeedNow {}", result);
    }

    /**
     * 同步单个接口（seller-guide / entitlement / decisions / management-choices / premarket / updates / support）
     */
    public void syncResource(String resource)
    {
        Map<String, Object> result = resourceService.sync(resource, null, false, "JOB");
        log.debug("optionSyncTask.syncResource({}) {}", resource, result);
    }

    /**
     * 立即同步全部接口（忽略同步时段），任一失败则任务记为失败
     */
    public void syncAllResourcesNow()
    {
        Map<String, Object> results = resourceService.syncAll("JOB");
        log.info("optionSyncTask.syncAllResourcesNow {}", results);
        boolean failed = results.values().stream()
                .anyMatch(r -> r instanceof Map<?, ?> m && "FAILED".equals(m.get("result")));
        if (failed)
        {
            throw new ServiceException("部分接口同步失败：" + results);
        }
    }
}
