package com.ruoyi.quartz.task;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionCloseSnapshotService;

/**
 * 会员数据权限任务
 * 
 * 调用目标：optionAccessTask.refreshLevels()（会员身份回写，每小时第 15 分钟；只更新 level 字段，用于后台统计和展示，
 *          身份判断本身每次请求实时计算，不依赖本任务）
 *          optionAccessTask.freezeClose()（OSM盘后视图冻结，每 5 分钟检查；交易日收盘冻结点后冻结当天、放出前一个交易日，
 *          同一交易日只冻结、放出各一次）
 *          optionAccessTask.cleanSnapshots()（盘后冻结数据清理，每天 04:30；超过 option.access.snapshotKeepDays 的记录）
 * 
 * @author yscandice
 */
@Component("optionAccessTask")
public class OptionAccessTask
{
    private static final Logger log = LoggerFactory.getLogger(OptionAccessTask.class);

    @Autowired
    private IOptionAccessService accessService;

    @Autowired
    private IOptionCloseSnapshotService closeSnapshotService;

    /**
     * 盘后视图冻结与放出
     */
    public void freezeClose()
    {
        Map<String, Object> result = closeSnapshotService.freezeClose();
        if (result.get("freeze") instanceof Map || result.get("release") instanceof Map)
        {
            log.info("optionAccessTask.freezeClose {}", result);
        }
        else
        {
            log.debug("optionAccessTask.freezeClose {}", result);
        }
        if (result.containsKey("error"))
        {
            throw new ServiceException("盘后视图冻结失败：" + result.get("error"));
        }
    }

    /**
     * 盘后冻结数据清理
     */
    public void cleanSnapshots()
    {
        int count = closeSnapshotService.cleanSnapshots();
        log.info("optionAccessTask.cleanSnapshots 删除冻结记录 {} 条", count);
    }

    /**
     * 会员身份回写
     */
    public void refreshLevels()
    {
        Map<String, Object> result = accessService.refreshLevels();
        log.info("optionAccessTask.refreshLevels {}", result);
    }
}
