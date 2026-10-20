package com.ruoyi.option.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionCloseSnapshotData;

/**
 * 盘后视图冻结数据 数据层（option_close_snapshot_data）
 * 
 * @author yscandice
 */
public interface OptionCloseSnapshotDataMapper
{
    /**
     * 查询盘后视图冻结数据
     * 
     * @param dataId 主键
     * @return 盘后视图冻结数据
     */
    public OptionCloseSnapshotData selectOptionCloseSnapshotDataById(Long dataId);

    /**
     * 查询盘后视图冻结数据列表
     * 
     * @param optionCloseSnapshotData 查询条件
     * @return 盘后视图冻结数据集合
     */
    public List<OptionCloseSnapshotData> selectOptionCloseSnapshotDataList(OptionCloseSnapshotData optionCloseSnapshotData);

    /**
     * 查询某次冻结的全部数据（含 payload，用于放出 / 恢复）
     * 
     * @param snapshotId 冻结ID
     * @return 冻结数据集合
     */
    public List<OptionCloseSnapshotData> selectOptionCloseSnapshotDataBySnapshotId(Long snapshotId);

    /**
     * 批量新增冻结数据
     * 
     * @param list 冻结数据集合
     * @return 结果
     */
    public int batchInsertOptionCloseSnapshotData(List<OptionCloseSnapshotData> list);

    /**
     * 按冻结ID批量删除冻结数据（清理任务）
     * 
     * @param snapshotIds 冻结ID集合
     * @return 结果
     */
    public int deleteOptionCloseSnapshotDataBySnapshotIds(Long[] snapshotIds);

    /**
     * 新增盘后视图冻结数据
     * 
     * @param optionCloseSnapshotData 盘后视图冻结数据
     * @return 结果
     */
    public int insertOptionCloseSnapshotData(OptionCloseSnapshotData optionCloseSnapshotData);

    /**
     * 删除盘后视图冻结数据
     * 
     * @param dataId 主键
     * @return 结果
     */
    public int deleteOptionCloseSnapshotDataById(Long dataId);

    /**
     * 批量删除盘后视图冻结数据
     * 
     * @param dataIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionCloseSnapshotDataByIds(Long[] dataIds);

    /**
     * 按菜单统计冻结记录的键数量与存储字节数（gzip 后）
     * 
     * @param snapshotId 冻结ID
     * @return resourceCode / keyCount / storedBytes
     */
    public List<Map<String, Object>> selectSnapshotDataStats(Long snapshotId);
}
