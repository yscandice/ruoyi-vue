package com.ruoyi.option.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
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

    /**
     * 标记已读：没有记录则新建；已有记录只增不减（seenSeq 不大于库里的值时不改动）
     * 
     * @param userId 用户ID
     * @param seenSeq 已读序号
     * @return 影响行数
     */
    public int upsertSeen(@Param("userId") Long userId, @Param("seenSeq") Integer seenSeq);
}
