package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionRecordItem;
import com.ruoyi.option.domain.OptionRecordSync;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「战绩」菜单 服务层
 * 
 * @author yscandice
 */
public interface IOptionRecordService
{
    /**
     * 同步战绩菜单：拉取 feed 与 decisions，按页面规则组装 5 个类型，存库并写入 Redis
     * 
     * @param force 是否强制（忽略交易时段与来源未变化判断）
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 同步结果
     */
    public Map<String, Object> sync(boolean force, String trigger, String operator);

    /**
     * 读取某个类型（Redis 未命中时从表回填）
     * 
     * @param kind trade / daily / symbol / stat / account
     * @param query 过滤条件
     * @return 响应体，暂无数据返回 null
     */
    public FeedBody getKind(String kind, RecordQuery query);

    /**
     * 读取汇总（交易日、批次、各类型条数、全部/本月统计、同步状态）
     * 
     * @return 响应体，暂无数据返回 null
     */
    public FeedBody getSummary();

    /**
     * 查询同步流水
     * 
     * @param sync 查询条件
     * @return 流水集合
     */
    public List<OptionRecordSync> selectSyncList(OptionRecordSync sync);

    /**
     * 查询条目版本列表
     * 
     * @param kind 类型
     * @param item 查询条件
     * @return 版本集合（不含内容）
     */
    public List<OptionRecordItem> selectItemList(String kind, OptionRecordItem item);

    /**
     * 查询条目版本详情
     * 
     * @param kind 类型
     * @param versionId 版本ID
     * @return 版本（含内容）
     */
    public OptionRecordItem selectItem(String kind, Long versionId);

    /**
     * 读接口过滤条件
     * 
     * @param range 区间 all 全部 / month 本月（trade、daily、symbol）
     * @param scope 口径 all 全部正式信号 / adopted 我跟了的（trade、daily）
     * @param granularity 粒度 day / week / month（daily）
     * @param symbol 标的或公司名（trade、symbol）
     * @param outcome 结果口径 EXPIRY / EARLY_TAKE_PROFIT（trade）
     */
    public record RecordQuery(String range, String scope, String granularity, String symbol, String outcome)
    {
        public boolean isEmpty()
        {
            return isBlank(range) && isBlank(scope) && isBlank(granularity) && isBlank(symbol) && isBlank(outcome);
        }

        private static boolean isBlank(String text)
        {
            return text == null || text.trim().isEmpty();
        }
    }
}
