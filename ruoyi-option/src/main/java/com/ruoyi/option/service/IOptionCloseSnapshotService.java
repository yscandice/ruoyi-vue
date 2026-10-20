package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionCloseSnapshot;

/**
 * 盘后视图 收盘冻结 / 放出 / 恢复 / 清理 服务层（技术文档 3.4）
 * 
 * @author yscandice
 */
public interface IOptionCloseSnapshotService
{
    /**
     * 查询冻结记录列表
     * 
     * @param snapshot 查询条件
     * @return 冻结记录集合
     */
    public List<OptionCloseSnapshot> selectOptionCloseSnapshotList(OptionCloseSnapshot snapshot);

    /**
     * 查询冻结记录（含各菜单键数量与存储字节数）
     * 
     * @param snapshotId 冻结ID
     * @return 冻结记录与菜单统计
     */
    public Map<String, Object> selectOptionCloseSnapshotById(Long snapshotId);

    /**
     * 定时任务：交易日收盘冻结点之后冻结当天（同一交易日只冻结一次），并放出前 delayedLagSessions 个交易日的冻结数据
     * 
     * @return 执行结果
     */
    public Map<String, Object> freezeClose();

    /**
     * 手动冻结当天（非交易日取最近一个交易日）
     * 
     * @param force 是否强制：已冻结时重新冻结、未到收盘冻结点时也冻结（数据标记为 STALE）
     * @param operator 操作人
     * @return 冻结结果
     */
    public Map<String, Object> freeze(boolean force, String operator);

    /**
     * 手动放出指定冻结记录（首次上线、补放）
     * 
     * @param snapshotId 冻结ID
     * @return 放出结果
     */
    public Map<String, Object> release(Long snapshotId);

    /**
     * 用指定冻结记录回填 Redis 盘后视图（不改变放出状态）
     * 
     * @param snapshotId 冻结ID
     * @return 恢复结果
     */
    public Map<String, Object> restore(Long snapshotId);

    /**
     * 盘后视图缺失时（服务启动、Redis 丢失）按当前放出记录从库里回填
     * 
     * @return 是否执行了回填
     */
    public boolean restoreIfMissing();

    /**
     * 清理超过保留天数（option.access.snapshotKeepDays）的冻结数据，当前放出的记录不删
     * 
     * @return 删除的冻结记录数
     */
    public int cleanSnapshots();

    /**
     * 盘后视图状态：最近一次冻结、当前放出记录、各菜单实时键与盘后键数量对比
     * 
     * @return 状态
     */
    public Map<String, Object> selectViewStatus();

    /**
     * 当前放出记录（读取路由使用）：优先 Redis option:access:release:current，丢失时取库里最新 RELEASED
     * 
     * @return tradeDate / snapshotId / releasedAt / staleResources，还没有放出过时为 null
     */
    public Map<String, Object> selectReleaseCurrent();
}
