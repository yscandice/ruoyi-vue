package com.ruoyi.option.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.vo.OptionMemberVo;

/**
 * 会员后台管理Mapper接口（option_member_access 关联 sys_user）
 * 
 * @author yscandice
 */
public interface OptionMemberAdminMapper
{
    /**
     * 会员列表（身份按 3.1 规则实时计算）
     * 
     * @param query 筛选条件
     * @return 会员集合
     */
    public List<OptionMemberVo> selectMemberList(OptionMemberVo query);

    /**
     * 统计卡：total、today、trial、delayed、paid、disabled
     * 
     * @return 统计
     */
    public Map<String, Object> selectMemberStats();

    /**
     * 每天注册数（day、count）
     * 
     * @param since 开始日期（东八区，含）
     * @return 每天注册数
     */
    public List<Map<String, Object>> selectRegisterDaily(@Param("since") Date since);

    /**
     * 只保留确实是会员的用户ID
     * 
     * @param userIds 用户ID
     * @return 会员用户ID
     */
    public List<Long> selectMemberIds(@Param("userIds") List<Long> userIds);

    /**
     * 延长 / 缩短体验期（trial_end_time 与 trial_extend_hours 同时调整）
     * 
     * @param userIds 用户ID
     * @param hours 小时数，可为负数
     * @param remark 备注（为空不改）
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int extendTrial(@Param("userIds") List<Long> userIds, @Param("hours") int hours, @Param("remark") String remark,
            @Param("updateBy") String updateBy);

    /**
     * 设置 / 取消付费到期
     * 
     * @param userId 用户ID
     * @param paidUntil 付费到期，null 表示取消
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int updatePaidUntil(@Param("userId") Long userId, @Param("paidUntil") Date paidUntil, @Param("updateBy") String updateBy);

    /**
     * 会员档案状态
     * 
     * @param userId 用户ID
     * @param status 0正常 1停用
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int updateMemberStatus(@Param("userId") Long userId, @Param("status") String status, @Param("updateBy") String updateBy);

    /**
     * 写最近访问时间
     * 
     * @param userId 用户ID
     * @return 影响行数
     */
    public int updateLastAccessTime(@Param("userId") Long userId);
}
