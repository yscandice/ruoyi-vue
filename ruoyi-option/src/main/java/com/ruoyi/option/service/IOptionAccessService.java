package com.ruoyi.option.service;

import java.time.ZonedDateTime;
import java.util.Map;
import com.ruoyi.option.domain.OptionAccessIdentity;
import com.ruoyi.option.domain.OptionAccessPolicy;
import com.ruoyi.option.domain.OptionReleaseWindow;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.enums.OptionDataView;

/**
 * 会员数据权限 服务层（身份计算、放出交易日计算、策略匹配）
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

    /**
     * 计算会员当前身份（每次请求实时判断，判断依据走缓存）
     * 
     * @param userId 用户ID
     * @return 身份
     */
    public OptionAccessLevel resolve(Long userId);

    /**
     * 查询会员身份判断依据（优先读缓存 option:member:access:{userId}）
     * 
     * @param userId 用户ID
     * @return 判断依据
     */
    public OptionAccessIdentity selectAccessIdentity(Long userId);

    /**
     * 按身份和菜单策略确定数据视图
     * 
     * @param level 身份
     * @param policy 菜单策略，为空时按身份默认视图
     * @return 数据视图
     */
    public OptionDataView resolveView(OptionAccessLevel level, OptionAccessPolicy policy);

    /**
     * 当前身份（/api/member/access，技术文档 7.1.3）
     * 
     * @param userId 用户ID
     * @return 身份、体验期、盘后数据交易日与放出时间、各菜单视图
     */
    public Map<String, Object> selectAccessInfo(Long userId);

    /**
     * 删除会员身份缓存（后台修改档案后调用，下一次请求立即按新身份返回）
     * 
     * @param userIds 用户ID
     */
    public void clearAccessCache(Long... userIds);

    /**
     * 回写全部会员档案的 level 字段（仅用于后台统计和展示）
     * 
     * @return 处理结果：total / changed / 各身份人数
     */
    public Map<String, Object> refreshLevels();
}
