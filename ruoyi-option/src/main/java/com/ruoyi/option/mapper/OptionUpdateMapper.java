package com.ruoyi.option.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionUpdateItem;
import com.ruoyi.option.domain.OptionUpdateSync;

/**
 * OSM 更新菜单 数据层（条目表按类型动态传入，表名来自 OptionUpdateKind 枚举）
 * 
 * @author yscandice
 */
public interface OptionUpdateMapper
{
    /**
     * 查询条目键对应的已有版本（版本ID、条目键、内容哈希）
     */
    public List<OptionUpdateItem> selectVersionKeys(@Param("tableName") String tableName,
            @Param("itemKeys") List<String> itemKeys);

    /**
     * 新增条目版本
     */
    public int insertItem(@Param("tableName") String tableName, @Param("item") OptionUpdateItem item);

    /**
     * 内容未变化的条目：更新最后出现批次与排序号
     */
    public int touchItems(@Param("tableName") String tableName, @Param("syncId") Long syncId,
            @Param("items") List<OptionUpdateItem> items, @Param("updateBy") String updateBy);

    /**
     * 查询某批次的当前条目（含内容）
     */
    public List<OptionUpdateItem> selectItemsBySync(@Param("tableName") String tableName,
            @Param("syncId") Long syncId);

    /**
     * 查询条目版本列表（不含内容）
     */
    public List<OptionUpdateItem> selectItemList(@Param("tableName") String tableName,
            @Param("item") OptionUpdateItem item);

    /**
     * 查询条目版本详情（含内容）
     */
    public OptionUpdateItem selectItemById(@Param("tableName") String tableName,
            @Param("versionId") Long versionId);

    /**
     * 新增同步流水
     */
    public int insertUpdateSync(OptionUpdateSync sync);

    /**
     * 更新同步流水
     */
    public int updateUpdateSync(OptionUpdateSync sync);

    /**
     * 最近一次成功写入的批次
     */
    public OptionUpdateSync selectLatestUpdatedSync();

    /**
     * 查询同步流水列表
     */
    public List<OptionUpdateSync> selectUpdateSyncList(OptionUpdateSync sync);
}
