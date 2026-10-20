package com.ruoyi.option.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionMemberRegisterLog;

/**
 * 会员注册与风控日志 数据层（option_member_register_log）
 * 
 * @author yscandice
 */
public interface OptionMemberRegisterLogMapper
{
    /**
     * 查询会员注册与风控日志
     * 
     * @param logId 主键
     * @return 会员注册与风控日志
     */
    public OptionMemberRegisterLog selectOptionMemberRegisterLogById(Long logId);

    /**
     * 查询会员注册与风控日志列表
     * 
     * @param optionMemberRegisterLog 查询条件
     * @return 会员注册与风控日志集合
     */
    public List<OptionMemberRegisterLog> selectOptionMemberRegisterLogList(OptionMemberRegisterLog optionMemberRegisterLog);

    /**
     * 统计日志条数（IP 每日注册上限、验证码发送次数等风控判断）
     * 
     * @param ip IP，可为空
     * @param email 邮箱，可为空
     * @param action 动作 SEND_CODE/REGISTER，可为空
     * @param result 结果 SUCCESS/REJECTED/FAILED，可为空
     * @param since 起始时间（东八区，含），可为空
     * @return 条数
     */
    public int countRegisterLog(@Param("ip") String ip, @Param("email") String email, @Param("action") String action,
            @Param("result") String result, @Param("since") Date since);

    /**
     * 新增会员注册与风控日志
     * 
     * @param optionMemberRegisterLog 会员注册与风控日志
     * @return 结果
     */
    public int insertOptionMemberRegisterLog(OptionMemberRegisterLog optionMemberRegisterLog);

    /**
     * 修改会员注册与风控日志
     * 
     * @param optionMemberRegisterLog 会员注册与风控日志
     * @return 结果
     */
    public int updateOptionMemberRegisterLog(OptionMemberRegisterLog optionMemberRegisterLog);

    /**
     * 删除会员注册与风控日志
     * 
     * @param logId 主键
     * @return 结果
     */
    public int deleteOptionMemberRegisterLogById(Long logId);

    /**
     * 批量删除会员注册与风控日志
     * 
     * @param logIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionMemberRegisterLogByIds(Long[] logIds);
}
