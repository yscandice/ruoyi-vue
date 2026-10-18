package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionResourceSync;
import com.ruoyi.option.domain.OptionResourceVersion;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM 会员接口（feed 之外）同步与读取 服务层
 * 
 * @author yscandice
 */
public interface IOptionResourceService
{
    /**
     * 同步单个接口：请求 OSM → 按内容哈希存版本表 → 写入 Redis
     * 
     * @param resource 接口名，如 seller-guide
     * @param date premarket 的日期（yyyy-MM-dd），为空取美东当天；其他接口忽略
     * @param force true 时忽略同步时段
     * @param trigger 触发方式 JOB / MANUAL
     * @return 结果
     */
    public Map<String, Object> sync(String resource, String date, boolean force, String trigger);

    /**
     * 同步全部 7 个接口（忽略同步时段）
     * 
     * @param trigger 触发方式
     * @return 各接口结果
     */
    public Map<String, Object> syncAll(String trigger);

    /**
     * 读取接口数据：只读 Redis，未命中时从版本表取最新一版并回填
     * 
     * @param resource 接口名
     * @param date premarket 的日期
     * @return 内容，无数据返回 null
     */
    public FeedBody get(String resource, String date);

    /**
     * 管理员手动导入一份接口数据（走同一套存表 + Redis 流程）
     * 
     * @param resource 接口名
     * @param date premarket 的日期
     * @param payload JSON
     * @return 结果
     */
    public Map<String, Object> importPayload(String resource, String date, String payload);

    /**
     * 同步记录列表
     * 
     * @param sync 查询条件
     * @return 记录集合
     */
    public List<OptionResourceSync> selectSyncList(OptionResourceSync sync);

    /**
     * 版本列表（不含内容）
     * 
     * @param resource 接口名
     * @param bizKey 业务键
     * @return 版本集合
     */
    public List<OptionResourceVersion> selectVersionList(String resource, String bizKey);

    /**
     * 查询指定版本（含内容）
     * 
     * @param resource 接口名
     * @param versionId 版本ID
     * @return 版本
     */
    public OptionResourceVersion selectVersion(String resource, Long versionId);
}
