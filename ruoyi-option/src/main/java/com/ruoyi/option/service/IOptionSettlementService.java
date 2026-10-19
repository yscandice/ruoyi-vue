package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionSettlementItem;
import com.ruoyi.option.domain.OptionSettlementSync;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「结算」菜单 服务层
 * 
 * @author yscandice
 */
public interface IOptionSettlementService
{
    /**
     * 同步：拉取 feed（复用 feed 同步的会话、ETag、限流与交易时段规则）后重算 已结算 / 结算单 / 结算统计，存库并写入 Redis
     * 
     * @param force 是否强制（忽略交易时段并强制重算）
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 结果（result：UPDATED/UNCHANGED/SKIPPED/PAUSED/FAILED）
     */
    public Map<String, Object> sync(boolean force, String trigger, String operator);

    /**
     * 读取类型数据（Redis，未命中时从表回填）
     * 
     * @param kind 类型 record/card/stat
     * @param query 查询条件（symbol 标的；range week/month/all/custom、from、to、sort settled/issued 仅作用于 record）
     * @return 响应体，没有任何同步数据返回 null
     */
    public FeedBody getKind(String kind, SettlementQuery query);

    /**
     * 读取结算菜单汇总（各类型条数、结算统计与同步状态）
     * 
     * @return 响应体，没有任何同步数据返回 null
     */
    public FeedBody getSummary();

    /**
     * 查询同步记录
     * 
     * @param sync 查询条件
     * @return 记录集合
     */
    public List<OptionSettlementSync> selectSyncList(OptionSettlementSync sync);

    /**
     * 查询条目版本列表（不含内容）
     * 
     * @param kind 类型
     * @param item 查询条件
     * @return 条目集合
     */
    public List<OptionSettlementItem> selectItemList(String kind, OptionSettlementItem item);

    /**
     * 查询条目版本详情（含内容）
     * 
     * @param kind 类型
     * @param versionId 版本ID
     * @return 条目
     */
    public OptionSettlementItem selectItem(String kind, Long versionId);

    /**
     * 读接口查询条件
     * 
     * @param symbol 标的或公司名（忽略大小写包含匹配）
     * @param range 区间 week 本周 / month 本月 / all 全部 / custom 自定义
     * @param from 自定义起始日（yyyy-MM-dd 或 MM/dd）
     * @param to 自定义截止日（yyyy-MM-dd 或 MM/dd）
     * @param sort 分组日期 settled 按结算日 / issued 按发报日
     */
    public record SettlementQuery(String symbol, String range, String from, String to, String sort)
    {
        public boolean isEmpty()
        {
            return isBlank(symbol) && isBlank(range) && isBlank(from) && isBlank(to) && isBlank(sort);
        }

        private static boolean isBlank(String text)
        {
            return text == null || text.trim().isEmpty();
        }
    }
}
