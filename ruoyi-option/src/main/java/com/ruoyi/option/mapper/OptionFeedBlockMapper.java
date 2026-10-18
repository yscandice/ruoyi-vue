package com.ruoyi.option.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionFeedBlock;

/**
 * feed 对象字段版本 数据层
 * 
 * @author yscandice
 */
public interface OptionFeedBlockMapper
{
    /**
     * 按内容哈希查询已有版本（不含内容）
     * 
     * @param hashes 内容哈希集合
     * @return 版本集合
     */
    public List<OptionFeedBlock> selectBlockByHashes(@Param("hashes") Collection<String> hashes);

    /**
     * 按版本ID查询（含内容）
     * 
     * @param versionIds 版本ID集合
     * @return 版本集合
     */
    public List<OptionFeedBlock> selectBlockByVersionIds(@Param("versionIds") Collection<Long> versionIds);

    /**
     * 新增版本
     * 
     * @param block 版本
     * @return 结果
     */
    public int insertBlock(OptionFeedBlock block);

    /**
     * 更新最后出现批次
     * 
     * @param syncId 批次ID
     * @param versionIds 版本ID集合
     * @return 结果
     */
    public int touchBlocks(@Param("syncId") Long syncId, @Param("versionIds") Collection<Long> versionIds);
}
