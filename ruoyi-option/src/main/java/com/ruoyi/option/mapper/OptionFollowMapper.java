package com.ruoyi.option.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionFollowItem;
import com.ruoyi.option.domain.OptionFollowSync;

/**
 * OSM 跟踪菜单 数据层（标记表名由 OptionFollowKind 枚举提供，不接受外部输入）
 * 
 * @author yscandice
 */
public interface OptionFollowMapper
{
    /**
     * 按条目键查询已有版本（仅 version_id、item_key、content_hash）
     * 
     * @param tableName 表名
     * @param itemKeys 条目键
     * @return 版本列表
     */
    public List<OptionFollowItem> selectVersionKeys(@Param("tableName") String tableName,
            @Param("itemKeys") List<String> itemKeys);

    /**
     * 新增标记版本
     * 
     * @param tableName 表名
     * @param item 标记版本
     * @return 结果
     */
    public int insertItem(@Param("tableName") String tableName, @Param("item") OptionFollowItem item);

    /**
     * 内容未变的版本：更新最后出现批次与排序号
     * 
     * @param tableName 表名
     * @param syncId 批次
     * @param items 版本ID与排序号
     * @param updateBy 更新者
     * @return 结果
     */
    public int touchItems(@Param("tableName") String tableName, @Param("syncId") Long syncId,
            @Param("items") List<OptionFollowItem> items, @Param("updateBy") String updateBy);

    /**
     * 查询某批次的全部标记（含内容，按排序号）
     * 
     * @param tableName 表名
     * @param syncId 批次
     * @return 标记列表
     */
    public List<OptionFollowItem> selectItemsBySync(@Param("tableName") String tableName,
            @Param("syncId") Long syncId);

    /**
     * 查询标记版本列表（不含内容）
     * 
     * @param tableName 表名
     * @param item 查询条件
     * @return 版本列表
     */
    public List<OptionFollowItem> selectItemList(@Param("tableName") String tableName,
            @Param("item") OptionFollowItem item);

    /**
     * 查询标记版本详情（含内容）
     * 
     * @param tableName 表名
     * @param versionId 版本ID
     * @return 标记版本
     */
    public OptionFollowItem selectItemById(@Param("tableName") String tableName,
            @Param("versionId") Long versionId);

    /**
     * 新增同步记录
     * 
     * @param sync 同步记录
     * @return 结果
     */
    public int insertFollowSync(OptionFollowSync sync);

    /**
     * 修改同步记录
     * 
     * @param sync 同步记录
     * @return 结果
     */
    public int updateFollowSync(OptionFollowSync sync);

    /**
     * 查询最近一次成功的同步记录
     * 
     * @return 同步记录
     */
    public OptionFollowSync selectLatestUpdatedSync();

    /**
     * 查询同步记录列表
     * 
     * @param sync 查询条件
     * @return 同步记录集合
     */
    public List<OptionFollowSync> selectFollowSyncList(OptionFollowSync sync);
}
