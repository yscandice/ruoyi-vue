package com.ruoyi.option.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionMemberAccess;

/**
 * 会员权限档案 数据层（option_member_access）
 * 
 * @author yscandice
 */
public interface OptionMemberAccessMapper
{
    /**
     * 查询会员权限档案
     * 
     * @param userId 主键
     * @return 会员权限档案
     */
    public OptionMemberAccess selectOptionMemberAccessById(Long userId);

    /**
     * 查询会员权限档案列表
     * 
     * @param optionMemberAccess 查询条件
     * @return 会员权限档案集合
     */
    public List<OptionMemberAccess> selectOptionMemberAccessList(OptionMemberAccess optionMemberAccess);

    /**
     * 按邮箱查询会员权限档案
     * 
     * @param email 邮箱（小写）
     * @return 会员权限档案
     */
    public OptionMemberAccess selectOptionMemberAccessByEmail(String email);

    /**
     * 统计某个设备指纹在指定时间之后注册的会员数（同一设备 30 天内只给一次体验期）
     * 
     * @param deviceHash 设备指纹哈希
     * @param since 起始时间（东八区）
     * @return 会员数
     */
    public int countMemberByDevice(@Param("deviceHash") String deviceHash, @Param("since") Date since);

    /**
     * 新增会员权限档案
     * 
     * @param optionMemberAccess 会员权限档案
     * @return 结果
     */
    public int insertOptionMemberAccess(OptionMemberAccess optionMemberAccess);

    /**
     * 修改会员权限档案
     * 
     * @param optionMemberAccess 会员权限档案
     * @return 结果
     */
    public int updateOptionMemberAccess(OptionMemberAccess optionMemberAccess);

    /**
     * 回写最近一次计算的身份（仅用于统计，不改更新时间）
     * 
     * @param userId 用户ID
     * @param level 身份代码
     * @return 结果
     */
    public int updateOptionMemberAccessLevel(@Param("userId") Long userId, @Param("level") String level);

    /**
     * 删除会员权限档案
     * 
     * @param userId 主键
     * @return 结果
     */
    public int deleteOptionMemberAccessById(Long userId);

    /**
     * 批量删除会员权限档案
     * 
     * @param userIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionMemberAccessByIds(Long[] userIds);
}
