package com.ruoyi.option.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionConductItem;
import com.ruoyi.option.domain.OptionConductSync;

/**
 * OSM 进行菜单 数据层（表名只来自 {@link com.ruoyi.option.enums.OptionConductScope}）
 * 
 * @author yscandice
 */
public interface OptionConductMapper
{
    /**
     * 按条目键查询已有版本（仅 version_id / item_key / content_hash）
     * 
     * @param tableName 表名
     * @param itemKeys 条目键集合
     * @return 版本集合
     */
    public List<OptionConductItem> selectVersionKeys(@Param("tableName") String tableName,
            @Param("itemKeys") List<String> itemKeys);

    /**
     * 新增条目版本
     * 
     * @param tableName 表名
     * @param item 条目
     * @return 结果
     */
    public int insertItem(@Param("tableName") String tableName, @Param("item") OptionConductItem item);

    /**
     * 内容未变化的条目：更新最后出现批次与排序号
     * 
     * @param tableName 表名
     * @param syncId 本次批次
     * @param items 条目（versionId、sortNo）
     * @param updateBy 更新者
     * @return 结果
     */
    public int touchItems(@Param("tableName") String tableName, @Param("syncId") Long syncId,
            @Param("items") List<OptionConductItem> items, @Param("updateBy") String updateBy);

    /**
     * 查询某批次的栏目内容（含内容，按排序号）
     * 
     * @param tableName 表名
     * @param syncId 批次
     * @return 条目集合
     */
    public List<OptionConductItem> selectItemsBySync(@Param("tableName") String tableName,
            @Param("syncId") Long syncId);

    /**
     * 查询条目版本列表（不含内容）
     * 
     * @param tableName 表名
     * @param item 查询条件
     * @return 条目集合
     */
    public List<OptionConductItem> selectItemList(@Param("tableName") String tableName,
            @Param("item") OptionConductItem item);

    /**
     * 按ID查询条目版本（含内容）
     * 
     * @param tableName 表名
     * @param versionId 版本ID
     * @return 条目
     */
    public OptionConductItem selectItemById(@Param("tableName") String tableName,
            @Param("versionId") Long versionId);

    /**
     * 新增同步记录
     * 
     * @param sync 记录
     * @return 结果
     */
    public int insertConductSync(OptionConductSync sync);

    /**
     * 更新同步记录（写入统计结果）
     * 
     * @param sync 记录
     * @return 结果
     */
    public int updateConductSync(OptionConductSync sync);

    /**
     * 查询最近一次写入栏目内容的同步记录（result = UPDATED）
     * 
     * @return 记录
     */
    public OptionConductSync selectLatestUpdatedSync();

    /**
     * 查询同步记录
     * 
     * @param sync 查询条件
     * @return 记录集合
     */
    public List<OptionConductSync> selectConductSyncList(OptionConductSync sync);
}
