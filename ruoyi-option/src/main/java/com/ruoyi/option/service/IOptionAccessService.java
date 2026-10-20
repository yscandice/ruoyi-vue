package com.ruoyi.option.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionAccessIdentity;
import com.ruoyi.option.domain.OptionAccessPolicy;
import com.ruoyi.option.domain.OptionReleaseWindow;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.enums.OptionDataView;
import com.ruoyi.option.utils.OptionAccessContext;

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

    /**
     * 数据权限总开关（option.access.enabled），关闭时会员接口行为与接入前完全一样
     * 
     * @return 是否开启
     */
    public boolean isEnabled();

    /**
     * 盘后视图读不到数据时的处理（option.access.delayedFallback）：EMPTY 返回空结构（默认），REALTIME 回落实时
     * 
     * @return 是否回落实时
     */
    public boolean isDelayedFallbackRealtime();

    /**
     * 按请求路径匹配菜单策略（启用状态，按 order_num 取第一个匹配，策略进程内缓存 60 秒）
     * 
     * @param path 请求路径（不含 context-path）
     * @return 菜单策略，未登记时为 null
     */
    public OptionAccessPolicy matchPolicy(String path);

    /**
     * 构建会员请求的数据权限上下文（身份 + 菜单策略 + 当前放出记录）
     * 
     * @param userId 用户ID
     * @param path 请求路径
     * @return 上下文
     */
    public OptionAccessContext buildContext(Long userId, String path);

    /**
     * 查询菜单策略列表
     * 
     * @param policy 查询条件
     * @return 策略集合
     */
    public List<OptionAccessPolicy> selectPolicyList(OptionAccessPolicy policy);

    /**
     * 查询菜单策略
     * 
     * @param policyId 策略ID
     * @return 策略
     */
    public OptionAccessPolicy selectPolicyById(Long policyId);

    /**
     * 修改菜单策略（校验视图取值，修改后立即清除策略缓存）
     * 
     * @param policy 策略
     * @return 结果
     */
    public int updatePolicy(OptionAccessPolicy policy);

    /**
     * 清除进程内策略缓存
     */
    public void clearPolicyCache();

    /**
     * 核对会员接口与菜单策略：逐条给出所属菜单和各身份视图，列出未登记策略的路径
     * 
     * @param endpoints 接口列表，每项 path（路径模板）与 methods（请求方法）
     * @return total / registered / excluded / unregistered / items
     */
    public Map<String, Object> checkPolicies(List<Map<String, Object>> endpoints);
}
