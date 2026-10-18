package com.ruoyi.option.mapper;

import java.util.List;
import com.ruoyi.option.domain.OptionFeedSync;

/**
 * feed 同步批次 数据层
 * 
 * @author yscandice
 */
public interface OptionFeedSyncMapper
{
    /**
     * 新增同步批次
     * 
     * @param sync 批次
     * @return 结果
     */
    public int insertOptionFeedSync(OptionFeedSync sync);

    /**
     * 修改同步批次
     * 
     * @param sync 批次
     * @return 结果
     */
    public int updateOptionFeedSync(OptionFeedSync sync);

    /**
     * 查询同步批次（含清单）
     * 
     * @param syncId 批次ID
     * @return 批次
     */
    public OptionFeedSync selectOptionFeedSyncById(Long syncId);

    /**
     * 查询最近一次成功的批次（含清单）
     * 
     * @return 批次
     */
    public OptionFeedSync selectLatestOptionFeedSync();

    /**
     * 查询同步批次列表（不含清单）
     * 
     * @param sync 查询条件
     * @return 批次集合
     */
    public List<OptionFeedSync> selectOptionFeedSyncList(OptionFeedSync sync);
}
