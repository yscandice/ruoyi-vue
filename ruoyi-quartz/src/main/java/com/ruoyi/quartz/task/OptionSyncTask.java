package com.ruoyi.quartz.task;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.option.service.IOptionConductService;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionInfoService;
import com.ruoyi.option.service.IOptionResourceService;

/**
 * 期权 OSM 数据同步任务
 * 
 * 调用目标：optionSyncTask.syncFeed()（每分钟，仅美东交易时段 09:30-16:05 实际请求）
 *          optionSyncTask.syncFeedNow()（忽略交易时段，手动"执行一次"用）
 *          optionSyncTask.syncResource('decisions')（单个接口，按接口自身同步时段判断）
 *          optionSyncTask.syncAllResourcesNow()（全部 7 个接口，忽略同步时段）
 *          optionSyncTask.syncInfo('premarket')（信息菜单单个接口：info-feed / referrals / premarket / earnings / notes）
 *          optionSyncTask.syncAllInfoNow()（信息菜单全部 5 个接口）
 *          optionSyncTask.syncConduct()（进行菜单，每分钟；feed 仅交易时段请求，批次或交易日变化时重算栏目）
 *          optionSyncTask.syncConductNow()（进行菜单强制同步，忽略交易时段）
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

    @Autowired
    private IOptionInfoService infoService;

    @Autowired
    private IOptionConductService conductService;

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

    /**
     * 同步信息菜单单个接口（info-feed / referrals / premarket / earnings / notes）
     */
    public void syncInfo(String info)
    {
        Map<String, Object> result = infoService.sync(info, null, "JOB", "system");
        log.debug("optionSyncTask.syncInfo({}) {}", info, result);
        if ("FAILED".equals(result.get("result")))
        {
            throw new ServiceException("信息接口同步失败：" + result);
        }
    }

    /**
     * 立即同步信息菜单全部接口，任一失败则任务记为失败
     */
    public void syncAllInfoNow()
    {
        Map<String, Object> results = infoService.syncAll("JOB", "system");
        log.info("optionSyncTask.syncAllInfoNow {}", results);
        boolean failed = results.values().stream()
                .anyMatch(r -> r instanceof Map<?, ?> m && "FAILED".equals(m.get("result")));
        if (failed)
        {
            throw new ServiceException("部分信息接口同步失败：" + results);
        }
    }

    /**
     * 同步进行菜单（进行中 / 保护窗口 / 动态管理 / 已到期 / 历史信号）
     */
    public void syncConduct()
    {
        Map<String, Object> result = conductService.sync(false, "JOB", "system");
        log.debug("optionSyncTask.syncConduct {}", result);
        if ("FAILED".equals(result.get("result")))
        {
            throw new ServiceException("进行菜单同步失败：" + result);
        }
    }

    /**
     * 立即同步进行菜单（忽略交易时段并强制重算），失败或会话暂停时任务记为失败
     */
    public void syncConductNow()
    {
        Map<String, Object> result = conductService.sync(true, "JOB", "system");
        log.info("optionSyncTask.syncConductNow {}", result);
        Object state = result.get("result");
        if ("FAILED".equals(state) || "PAUSED".equals(state) || "SKIPPED".equals(state))
        {
            throw new ServiceException("进行菜单同步未完成：" + result);
        }
    }
}
