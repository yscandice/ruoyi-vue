package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionInfoSync;
import com.ruoyi.option.domain.OptionInfoVersion;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「信息」菜单数据 服务层
 * 
 * @author yscandice
 */
public interface IOptionInfoService
{
    /**
     * 同步一个数据源：请求 OSM → 存表（内容哈希去重）→ 写 Redis
     * 
     * @param info 数据源（info-feed / referrals / premarket / earnings / notes）
     * @param bizKey 业务键（premarket 为日期，earnings 为财报ID）；为空时同步最新一期并补齐缺失的历史
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 同步结果
     */
    public Map<String, Object> sync(String info, String bizKey, String trigger, String operator);

    /**
     * 依次同步全部数据源
     * 
     * @param trigger 触发方式
     * @param operator 操作人
     * @return 各数据源结果
     */
    public Map<String, Object> syncAll(String trigger, String operator);

    /**
     * 读取 JSON 接口数据（info-feed / referrals）
     * 
     * @param info 数据源
     * @return 原始 JSON，无数据返回 null
     */
    public FeedBody getRaw(String info);

    /**
     * 盘前简报页数据（brief + dates）
     * 
     * @param date 日期 yyyy-MM-dd，为空取最新一期
     * @return JSON，无数据返回 null
     */
    public FeedBody getPremarket(String date);

    /**
     * 财报解读页数据（piece + refs + previews）
     * 
     * @param id 财报ID，如 TSM-2026-10-15，为空取最新一篇
     * @return JSON，无数据返回 null
     */
    public FeedBody getEarnings(String id);

    /**
     * 札记页数据（notes 列表，传 id 时附带 note）
     * 
     * @param id 札记ID，可为空
     * @return JSON，无数据返回 null
     */
    public FeedBody getNotes(String id);

    /**
     * 查询同步记录
     * 
     * @param sync 查询条件
     * @return 记录集合
     */
    public List<OptionInfoSync> selectSyncList(OptionInfoSync sync);

    /**
     * 查询数据版本列表
     * 
     * @param info 数据源
     * @param bizKey 业务键，可为空
     * @return 版本集合
     */
    public List<OptionInfoVersion> selectVersionList(String info, String bizKey);

    /**
     * 查询指定版本
     * 
     * @param info 数据源
     * @param versionId 版本ID
     * @return 版本（含内容）
     */
    public OptionInfoVersion selectVersion(String info, Long versionId);
}
