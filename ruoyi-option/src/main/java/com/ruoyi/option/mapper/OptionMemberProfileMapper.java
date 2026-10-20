package com.ruoyi.option.mapper;

import java.util.List;
import com.ruoyi.option.domain.OptionMemberProfile;

/**
 * 会员资料扩展 数据层（option_member_profile）
 * 
 * @author yscandice
 */
public interface OptionMemberProfileMapper
{
    /**
     * 查询会员资料扩展
     * 
     * @param userId 主键
     * @return 会员资料扩展
     */
    public OptionMemberProfile selectOptionMemberProfileById(Long userId);

    /**
     * 查询会员资料扩展列表
     * 
     * @param optionMemberProfile 查询条件
     * @return 会员资料扩展集合
     */
    public List<OptionMemberProfile> selectOptionMemberProfileList(OptionMemberProfile optionMemberProfile);

    /**
     * 新增会员资料扩展
     * 
     * @param optionMemberProfile 会员资料扩展
     * @return 结果
     */
    public int insertOptionMemberProfile(OptionMemberProfile optionMemberProfile);

    /**
     * 修改会员资料扩展（乐观锁：version 不一致时返回 0）
     * 
     * @param optionMemberProfile 会员资料扩展
     * @return 结果
     */
    public int updateOptionMemberProfile(OptionMemberProfile optionMemberProfile);

    /**
     * 删除会员资料扩展
     * 
     * @param userId 主键
     * @return 结果
     */
    public int deleteOptionMemberProfileById(Long userId);

    /**
     * 批量删除会员资料扩展
     * 
     * @param userIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionMemberProfileByIds(Long[] userIds);
}
