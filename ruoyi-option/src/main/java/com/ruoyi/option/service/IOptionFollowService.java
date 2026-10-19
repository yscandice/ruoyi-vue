package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionFollowItem;
import com.ruoyi.option.domain.OptionFollowSync;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「跟踪」菜单（卖方信号 / 中期信号 / 买方信号 / 动态管理 标记） 服务层
 * 
 * @author yscandice
 */
public interface IOptionFollowService
{
    /**
     * 同步跟踪菜单：拉取 feed、decisions、management-choices，合并标记与系统参考估值，存库并写入 Redis
     * 
     * @param force 是否强制（忽略交易时段并重算）
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 同步结果
     */
    public Map<String, Object> sync(boolean force, String trigger, String operator);

    /**
     * 读取某类标记（Redis，未命中时从表回填）
     * 
     * @param kind signal / midterm / buyer / manage / all
     * @param symbol 标的过滤，可为空
     * @param mark 标记类型过滤 ADOPT_INTENT / OBSERVE / SKIP，可为空
     * @return 响应体，无数据返回 null
     */
    public FeedBody getKind(String kind, String symbol, String mark);

    /**
     * 读取汇总（交易日、批次、各类条数、标记计数、参考盈亏、同步状态）
     * 
     * @return 响应体，无数据返回 null
     */
    public FeedBody getSummary();

    /**
     * 查询同步记录列表
     * 
     * @param sync 查询条件
     * @return 同步记录集合
     */
    public List<OptionFollowSync> selectSyncList(OptionFollowSync sync);

    /**
     * 查询标记版本列表
     * 
     * @param kind 标记类型
     * @param item 查询条件
     * @return 版本列表
     */
    public List<OptionFollowItem> selectItemList(String kind, OptionFollowItem item);

    /**
     * 查询标记版本详情
     * 
     * @param kind 标记类型
     * @param versionId 版本ID
     * @return 标记版本
     */
    public OptionFollowItem selectItem(String kind, Long versionId);
}
