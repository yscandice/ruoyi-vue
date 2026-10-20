package com.ruoyi.quartz.task;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.option.service.IOptionAccessService;

/**
 * 会员数据权限任务
 * 
 * 调用目标：optionAccessTask.refreshLevels()（会员身份回写，每小时第 15 分钟；只更新 level 字段，用于后台统计和展示，
 *          身份判断本身每次请求实时计算，不依赖本任务）
 *          盘后视图冻结 freezeClose()、冻结数据清理 cleanSnapshots() 在步骤 5 补充
 * 
 * @author yscandice
 */
@Component("optionAccessTask")
public class OptionAccessTask
{
    private static final Logger log = LoggerFactory.getLogger(OptionAccessTask.class);

    @Autowired
    private IOptionAccessService accessService;

    /**
     * 会员身份回写
     */
    public void refreshLevels()
    {
        Map<String, Object> result = accessService.refreshLevels();
        log.info("optionAccessTask.refreshLevels {}", result);
    }
}
