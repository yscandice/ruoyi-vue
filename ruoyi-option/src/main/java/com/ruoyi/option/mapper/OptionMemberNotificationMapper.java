package com.ruoyi.option.mapper;

import java.util.List;
import com.ruoyi.option.domain.OptionMemberNotification;

/**
 * 会员通知偏好 数据层（option_member_notification）
 * 
 * @author yscandice
 */
public interface OptionMemberNotificationMapper
{
    /**
     * 查询会员通知偏好
     * 
     * @param userId 主键
     * @return 会员通知偏好
     */
    public OptionMemberNotification selectOptionMemberNotificationById(Long userId);

    /**
     * 查询会员通知偏好列表
     * 
     * @param optionMemberNotification 查询条件
     * @return 会员通知偏好集合
     */
    public List<OptionMemberNotification> selectOptionMemberNotificationList(OptionMemberNotification optionMemberNotification);

    /**
     * 新增会员通知偏好
     * 
     * @param optionMemberNotification 会员通知偏好
     * @return 结果
     */
    public int insertOptionMemberNotification(OptionMemberNotification optionMemberNotification);

    /**
     * 修改会员通知偏好（乐观锁：version 不一致时返回 0）
     * 
     * @param optionMemberNotification 会员通知偏好
     * @return 结果
     */
    public int updateOptionMemberNotification(OptionMemberNotification optionMemberNotification);

    /**
     * 删除会员通知偏好
     * 
     * @param userId 主键
     * @return 结果
     */
    public int deleteOptionMemberNotificationById(Long userId);

    /**
     * 批量删除会员通知偏好
     * 
     * @param userIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionMemberNotificationByIds(Long[] userIds);
}
