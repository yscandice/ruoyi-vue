package com.ruoyi.option.service;

import java.time.ZonedDateTime;
import com.ruoyi.option.domain.OptionReleaseWindow;

/**
 * 会员数据权限 服务层（身份计算、放出交易日计算、策略匹配）
 * 
 * 本步骤先提供放出交易日计算；身份计算（步骤 4）、策略匹配（步骤 6）后续补充。
 * 
 * @author yscandice
 */
public interface IOptionAccessService
{
    /**
     * 计算指定时间点盘后版用户看到的交易日、放出时间与下次放出时间（参数取 sys_config）
     * 
     * @param at 时间点（任意时区）
     * @return 放出窗口
     */
    public OptionReleaseWindow resolveReleaseWindow(ZonedDateTime at);
}
