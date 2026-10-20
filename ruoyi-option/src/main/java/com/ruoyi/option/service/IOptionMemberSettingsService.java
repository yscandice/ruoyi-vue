package com.ruoyi.option.service;

import java.util.Map;

/**
 * 会员「设置」Service接口（技术文档 4.2、7.3.1–7.3.4）
 * 
 * 首次读取没有记录时按默认值返回，第一次保存时才建记录；保存时带上次读到的 updatedAt，不一致返回 409。
 * 
 * @author yscandice
 */
public interface IOptionMemberSettingsService
{
    /**
     * 设置页聚合：资料、通知偏好、身份、阅读进度、功能开关
     * 
     * @param userId 用户ID
     * @return 设置页数据
     */
    public Map<String, Object> selectSettings(Long userId);

    /**
     * 资料（displayName、email、avatar、watchSymbols、strategyPreferences、strongAlerts、updatedAt）
     * 
     * @param userId 用户ID
     * @return 资料
     */
    public Map<String, Object> selectProfile(Long userId);

    /**
     * 修改资料（只传要改的字段）
     * 
     * @param userId 用户ID
     * @param body 请求参数
     * @return 修改后的资料
     */
    public Map<String, Object> updateProfile(Long userId, Map<String, Object> body);

    /**
     * 通知偏好 { prefs, updatedAt }
     * 
     * @param userId 用户ID
     * @return 通知偏好
     */
    public Map<String, Object> selectNotifications(Long userId);

    /**
     * 修改通知偏好 { prefs: { 只传要改的开关 }, updatedAt }，未知开关返回 400
     * 
     * @param userId 用户ID
     * @param body 请求参数
     * @return 修改后的通知偏好
     */
    public Map<String, Object> updateNotifications(Long userId, Map<String, Object> body);

    /**
     * 通知偏好恢复默认值
     * 
     * @param userId 用户ID
     * @return 默认通知偏好
     */
    public Map<String, Object> resetNotifications(Long userId);

    /**
     * 阅读进度 { seenSeq, currentSeq, seenAt, unseen }（seenSeq 按用户记录，currentSeq 来自同步数据）
     * 
     * @param userId 用户ID
     * @return 阅读进度
     */
    public Map<String, Object> selectUpdateProgress(Long userId);

    /**
     * 标记已读（不能超过 currentSeq，只增不减）
     * 
     * @param userId 用户ID
     * @param body 请求参数 { seenSeq }
     * @return 阅读进度
     */
    public Map<String, Object> markUpdatesSeen(Long userId, Map<String, Object> body);

    /**
     * 删除设置缓存
     * 
     * @param userId 用户ID
     */
    public void clearCache(Long userId);
}
