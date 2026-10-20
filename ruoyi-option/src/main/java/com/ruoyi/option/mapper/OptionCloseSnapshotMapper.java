package com.ruoyi.option.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionCloseSnapshot;

/**
 * 盘后视图冻结记录 数据层（option_close_snapshot）
 * 
 * @author yscandice
 */
public interface OptionCloseSnapshotMapper
{
    /**
     * 查询盘后视图冻结记录
     * 
     * @param snapshotId 主键
     * @return 盘后视图冻结记录
     */
    public OptionCloseSnapshot selectOptionCloseSnapshotById(Long snapshotId);

    /**
     * 查询盘后视图冻结记录列表
     * 
     * @param optionCloseSnapshot 查询条件
     * @return 盘后视图冻结记录集合
     */
    public List<OptionCloseSnapshot> selectOptionCloseSnapshotList(OptionCloseSnapshot optionCloseSnapshot);

    /**
     * 按交易日查询冻结记录
     * 
     * @param tradeDate 交易日（美东）
     * @return 冻结记录
     */
    public OptionCloseSnapshot selectOptionCloseSnapshotByTradeDate(Date tradeDate);

    /**
     * 查询当前已放出（最新一条 RELEASED）的冻结记录，用于 Redis 丢失后恢复
     * 
     * @return 冻结记录
     */
    public OptionCloseSnapshot selectLatestReleasedSnapshot();

    /**
     * 查询某交易日之前最近一份冻结成功的记录（T-1 缺失时往前找）
     * 
     * @param tradeDate 交易日（不含）
     * @return 冻结记录
     */
    public OptionCloseSnapshot selectOptionCloseSnapshotBefore(@Param("tradeDate") Date tradeDate);

    /**
     * 新增盘后视图冻结记录
     * 
     * @param optionCloseSnapshot 盘后视图冻结记录
     * @return 结果
     */
    public int insertOptionCloseSnapshot(OptionCloseSnapshot optionCloseSnapshot);

    /**
     * 修改盘后视图冻结记录
     * 
     * @param optionCloseSnapshot 盘后视图冻结记录
     * @return 结果
     */
    public int updateOptionCloseSnapshot(OptionCloseSnapshot optionCloseSnapshot);

    /**
     * 删除盘后视图冻结记录
     * 
     * @param snapshotId 主键
     * @return 结果
     */
    public int deleteOptionCloseSnapshotById(Long snapshotId);

    /**
     * 批量删除盘后视图冻结记录
     * 
     * @param snapshotIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionCloseSnapshotByIds(Long[] snapshotIds);
}
