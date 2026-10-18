package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionFeedItem;
import com.ruoyi.option.domain.OptionFeedSync;

/**
 * feed 同步 服务层
 * 
 * @author yscandice
 */
public interface IOptionFeedSyncService
{
    /**
     * 同步 feed：请求 OSM → 拆分写入多表 → 从多表拼接 → 写入 Redis
     * 
     * @param force true 时忽略交易时段
     * @return 同步结果
     */
    public Map<String, Object> syncFeed(boolean force);

    /**
     * 管理员手动导入一份 feed（走同一套拆分存表 + Redis 流程）
     * 
     * @param payload feed JSON
     * @return 结果
     */
    public Map<String, Object> importFeed(String payload);

    /**
     * 查询同步批次列表
     * 
     * @param sync 查询条件
     * @return 批次集合
     */
    public List<OptionFeedSync> selectSyncList(OptionFeedSync sync);

    /**
     * 查询条目版本列表
     * 
     * @param item 查询条件（listType 必填）
     * @return 版本集合
     */
    public List<OptionFeedItem> selectItemList(OptionFeedItem item);
}
