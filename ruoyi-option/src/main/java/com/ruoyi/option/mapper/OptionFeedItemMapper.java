package com.ruoyi.option.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionFeedItem;

/**
 * feed 数组条目版本 数据层
 * 
 * 多张条目表结构相同，tableName 只允许取自 {@link com.ruoyi.option.enums.OptionFeedList}，不接受外部输入。
 * 
 * @author yscandice
 */
public interface OptionFeedItemMapper
{
    /**
     * 按内容哈希查询已有版本（不含内容）
     * 
     * @param tableName 表名
     * @param listType 数组字段
     * @param hashes 内容哈希集合
     * @return 版本集合
     */
    public List<OptionFeedItem> selectItemByHashes(@Param("tableName") String tableName,
            @Param("listType") String listType, @Param("hashes") Collection<String> hashes);

    /**
     * 按版本ID查询（含内容）
     * 
     * @param tableName 表名
     * @param versionIds 版本ID集合
     * @return 版本集合
     */
    public List<OptionFeedItem> selectItemByVersionIds(@Param("tableName") String tableName,
            @Param("versionIds") Collection<Long> versionIds);

    /**
     * 批量新增版本
     * 
     * @param tableName 表名
     * @param list 版本集合
     * @return 结果
     */
    public int insertItemBatch(@Param("tableName") String tableName, @Param("list") List<OptionFeedItem> list);

    /**
     * 更新最后出现批次
     * 
     * @param tableName 表名
     * @param syncId 批次ID
     * @param versionIds 版本ID集合
     * @return 结果
     */
    public int touchItems(@Param("tableName") String tableName, @Param("syncId") Long syncId,
            @Param("versionIds") Collection<Long> versionIds);

    /**
     * 批量更新易变字段
     * 
     * @param tableName 表名
     * @param list 版本集合（versionId + volatileData）
     * @return 结果
     */
    public int updateVolatileBatch(@Param("tableName") String tableName, @Param("list") List<OptionFeedItem> list);

    /**
     * 查询条目版本列表（不含内容）
     * 
     * @param tableName 表名
     * @param item 查询条件
     * @return 版本集合
     */
    public List<OptionFeedItem> selectItemList(@Param("tableName") String tableName, @Param("item") OptionFeedItem item);
}
