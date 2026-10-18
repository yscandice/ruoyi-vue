package com.ruoyi.option.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionInfoSync;
import com.ruoyi.option.domain.OptionInfoVersion;

/**
 * OSM 信息菜单数据 数据层（表名只来自 {@link com.ruoyi.option.enums.OptionInfo}）
 * 
 * @author yscandice
 */
public interface OptionInfoMapper
{
    /**
     * 按业务键与内容哈希查询版本ID
     * 
     * @param tableName 表名
     * @param bizKey 业务键
     * @param contentHash 内容哈希
     * @return 版本ID，不存在返回 null
     */
    public Long selectVersionIdByHash(@Param("tableName") String tableName, @Param("bizKey") String bizKey,
            @Param("contentHash") String contentHash);

    /**
     * 新增版本
     * 
     * @param tableName 表名
     * @param version 版本（含摘要列）
     * @return 结果
     */
    public int insertVersion(@Param("tableName") String tableName, @Param("v") OptionInfoVersion version);

    /**
     * 内容未变化：更新最近命中时间与次数
     * 
     * @param tableName 表名
     * @param versionId 版本ID
     * @param updateBy 更新者
     * @return 结果
     */
    public int touchVersion(@Param("tableName") String tableName, @Param("versionId") Long versionId,
            @Param("updateBy") String updateBy);

    /**
     * 查询业务键下最近命中的版本（含内容）
     * 
     * @param tableName 表名
     * @param bizKey 业务键
     * @return 版本
     */
    public OptionInfoVersion selectLatestVersion(@Param("tableName") String tableName, @Param("bizKey") String bizKey);

    /**
     * 查询每个业务键最近命中的版本（含内容，不含列表索引）
     * 
     * @param tableName 表名
     * @return 版本集合
     */
    public List<OptionInfoVersion> selectLatestVersions(@Param("tableName") String tableName);

    /**
     * 查询已存在的业务键
     * 
     * @param tableName 表名
     * @return 业务键集合
     */
    public List<String> selectBizKeys(@Param("tableName") String tableName);

    /**
     * 按ID查询版本（含内容）
     * 
     * @param tableName 表名
     * @param versionId 版本ID
     * @return 版本
     */
    public OptionInfoVersion selectVersionById(@Param("tableName") String tableName,
            @Param("versionId") Long versionId);

    /**
     * 查询版本列表（不含内容）
     * 
     * @param tableName 表名
     * @param bizKey 业务键，可为空
     * @return 版本集合
     */
    public List<OptionInfoVersion> selectVersionList(@Param("tableName") String tableName,
            @Param("bizKey") String bizKey);

    /**
     * 新增同步记录
     * 
     * @param sync 记录
     * @return 结果
     */
    public int insertInfoSync(OptionInfoSync sync);

    /**
     * 查询同步记录
     * 
     * @param sync 查询条件
     * @return 记录集合
     */
    public List<OptionInfoSync> selectInfoSyncList(OptionInfoSync sync);
}
