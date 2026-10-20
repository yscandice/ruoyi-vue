package com.ruoyi.option.mapper;

import java.util.List;
import com.ruoyi.option.domain.OptionMemberUpdateSeen;

/**
 * 会员「更新」阅读进度 数据层（option_member_update_seen）
 * 
 * @author yscandice
 */
public interface OptionMemberUpdateSeenMapper
{
    /**
     * 查询会员「更新」阅读进度
     * 
     * @param userId 主键
     * @return 会员「更新」阅读进度
     */
    public OptionMemberUpdateSeen selectOptionMemberUpdateSeenById(Long userId);

    /**
     * 查询会员「更新」阅读进度列表
     * 
     * @param optionMemberUpdateSeen 查询条件
     * @return 会员「更新」阅读进度集合
     */
    public List<OptionMemberUpdateSeen> selectOptionMemberUpdateSeenList(OptionMemberUpdateSeen optionMemberUpdateSeen);

    /**
     * 新增会员「更新」阅读进度
     * 
     * @param optionMemberUpdateSeen 会员「更新」阅读进度
     * @return 结果
     */
    public int insertOptionMemberUpdateSeen(OptionMemberUpdateSeen optionMemberUpdateSeen);

    /**
     * 修改会员「更新」阅读进度
     * 
     * @param optionMemberUpdateSeen 会员「更新」阅读进度
     * @return 结果
     */
    public int updateOptionMemberUpdateSeen(OptionMemberUpdateSeen optionMemberUpdateSeen);

    /**
     * 删除会员「更新」阅读进度
     * 
     * @param userId 主键
     * @return 结果
     */
    public int deleteOptionMemberUpdateSeenById(Long userId);

    /**
     * 批量删除会员「更新」阅读进度
     * 
     * @param userIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionMemberUpdateSeenByIds(Long[] userIds);
}
