package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionUpdateItem;
import com.ruoyi.option.domain.OptionUpdateSync;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「更新」菜单（/member/updates 功能更新：页头 + 更新条目） 服务层
 * 
 * @author yscandice
 */
public interface IOptionUpdateService
{
    /**
     * 同步：抓取更新页面 -> 解析 -> 按类型存库（内容去重）-> 写 Redis
     * 
     * @param force 是否强制写入（来源未变化也重新发布）
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 同步结果
     */
    public Map<String, Object> sync(boolean force, String trigger, String operator);

    /**
     * 读取更新汇总（页头、条目数、日期范围、按月分布、最新几条、阅读进度 seenSeq/currentSeq/unseen、同步状态）
     * 
     * @return 响应体，无数据返回 null
     */
    public FeedBody getSummary();

    /**
     * 读取某类型列表
     * 
     * @param kind 类型 page/entry
     * @param keyword 关键字（可选，匹配标题与正文）
     * @param month 上线月份 yyyy-MM（可选，仅 entry）
     * @param target 目标链接前缀（可选，仅 entry，如 /member/autopilot）
     * @return 响应体，无数据返回 null
     */
    public FeedBody getKind(String kind, String keyword, String month, String target);

    /**
     * 读取单条完整内容
     * 
     * @param kind 类型
     * @param key 条目键（页头 updates / 条目如 2026-10-19-roll-safer）
     * @return 响应体，无数据返回 null
     */
    public FeedBody getItem(String kind, String key);

    /**
     * 查询同步流水
     */
    public List<OptionUpdateSync> selectSyncList(OptionUpdateSync sync);

    /**
     * 查询条目版本列表
     */
    public List<OptionUpdateItem> selectItemList(String kind, OptionUpdateItem item);

    /**
     * 查询条目版本详情
     */
    public OptionUpdateItem selectItem(String kind, Long versionId);
}
