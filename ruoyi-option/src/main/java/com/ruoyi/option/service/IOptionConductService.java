package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionConductItem;
import com.ruoyi.option.domain.OptionConductSync;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「进行」菜单 服务层
 * 
 * @author yscandice
 */
public interface IOptionConductService
{
    /**
     * 同步：拉取 feed（复用 feed 同步的会话、ETag、限流与交易时段规则）后重算 5 个栏目，存库并写入 Redis
     * 
     * @param force 是否强制（忽略交易时段并强制重算）
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 结果（result：UPDATED/UNCHANGED/SKIPPED/PAUSED/FAILED）
     */
    public Map<String, Object> sync(boolean force, String trigger, String operator);

    /**
     * 读取栏目（Redis，未命中时从表回填）
     * 
     * @param scope 栏目 active/protection/manage/expired/history
     * @param symbol 标的过滤（可选，忽略大小写包含匹配）
     * @return 响应体，没有任何同步数据返回 null
     */
    public FeedBody getScope(String scope, String symbol);

    /**
     * 读取栏目汇总（各栏目条数与同步批次）
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
    public List<OptionConductSync> selectSyncList(OptionConductSync sync);

    /**
     * 查询栏目条目版本列表（不含内容）
     * 
     * @param scope 栏目
     * @param item 查询条件
     * @return 条目集合
     */
    public List<OptionConductItem> selectItemList(String scope, OptionConductItem item);

    /**
     * 查询栏目条目版本详情（含内容）
     * 
     * @param scope 栏目
     * @param versionId 版本ID
     * @return 条目
     */
    public OptionConductItem selectItem(String scope, Long versionId);
}
