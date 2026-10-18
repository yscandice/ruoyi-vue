package com.ruoyi.quartz.task;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.option.service.IOptionFeedSyncService;

/**
 * 期权 OSM 数据同步任务
 * 
 * 调用目标：optionSyncTask.syncFeed()（每分钟，仅美东交易时段 09:30-16:05 实际请求）
 *          optionSyncTask.syncFeedNow()（忽略交易时段，手动"执行一次"用）
 * 
 * @author yscandice
 */
@Component("optionSyncTask")
public class OptionSyncTask
{
    private static final Logger log = LoggerFactory.getLogger(OptionSyncTask.class);

    @Autowired
    private IOptionFeedSyncService feedSyncService;

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
}
