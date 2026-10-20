package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionMemberRegisterLog;
import com.ruoyi.option.domain.vo.OptionMemberVo;

/**
 * 会员后台管理Service接口（技术文档 9.3.2）
 * 
 * 所有修改都会删除身份缓存 option:member:access:{userId}，会员下一次请求立即按新身份返回。
 * 
 * @author yscandice
 */
public interface IOptionMemberAdminService
{
    /**
     * 会员列表（身份实时计算，附带是否在线）
     * 
     * @param query 筛选条件
     * @return 会员集合
     */
    public List<OptionMemberVo> selectMemberList(OptionMemberVo query);

    /**
     * 统计卡与近 7 天每天注册数
     * 
     * @return 统计
     */
    public Map<String, Object> selectMemberStats();

    /**
     * 会员详情：基本信息、档案、身份与数据视图、设置（只读）、在线会话、最近登录
     * 
     * @param userId 用户ID
     * @return 详情
     */
    public Map<String, Object> selectMemberDetail(Long userId);

    /**
     * 延长 / 缩短体验期
     * 
     * @param userIds 用户ID
     * @param extendHours 小时数（负数为缩短）
     * @param remark 备注
     * @return 处理的会员数
     */
    public int extendTrial(List<Long> userIds, Integer extendHours, String remark);

    /**
     * 设置 / 取消付费
     * 
     * @param userId 用户ID
     * @param body { paidUntil }，null 表示取消
     * @return 结果
     */
    public int updatePaidUntil(Long userId, Map<String, Object> body);

    /**
     * 启用 / 停用（停用时同步 sys_user.status 并强制下线）
     * 
     * @param userId 用户ID
     * @param status 0正常 1停用
     * @return 结果
     */
    public int updateStatus(Long userId, String status);

    /**
     * 重置密码并强制下线
     * 
     * @param userId 用户ID
     * @param password 新密码
     * @return 结果
     */
    public int resetPwd(Long userId, String password);

    /**
     * 强制下线：删除该用户的所有登录会话
     * 
     * @param userId 用户ID
     * @return 删除的会话数
     */
    public int forceLogout(Long userId);

    /**
     * 注册日志
     * 
     * @param log 筛选条件
     * @return 注册日志集合
     */
    public List<OptionMemberRegisterLog> selectRegisterLogList(OptionMemberRegisterLog log);

    /**
     * 记录最近访问时间（每个用户最多 5 分钟写一次库）
     * 
     * @param userId 用户ID
     */
    public void touchLastAccess(Long userId);
}
