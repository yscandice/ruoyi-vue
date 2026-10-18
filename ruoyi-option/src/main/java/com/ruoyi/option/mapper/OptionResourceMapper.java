package com.ruoyi.option.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionResourceSync;
import com.ruoyi.option.domain.OptionResourceVersion;

/**
 * OSM 接口内容版本与同步记录 数据层
 * 
 * tableName 只允许取自 {@link com.ruoyi.option.enums.OptionResource}，不接受外部输入。
 * 
 * @author yscandice
 */
public interface OptionResourceMapper
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
    public int insertVersion(@Param("tableName") String tableName, @Param("v") OptionResourceVersion version);

    /**
     * 内容未变化：更新最后出现时间与次数
     * 
     * @param tableName 表名
     * @param versionId 版本ID
     * @return 结果
     */
    public int touchVersion(@Param("tableName") String tableName, @Param("versionId") Long versionId);

    /**
     * 查询业务键下最近的版本（含内容）
     * 
     * @param tableName 表名
     * @param bizKey 业务键
     * @return 版本
     */
    public OptionResourceVersion selectLatestVersion(@Param("tableName") String tableName,
            @Param("bizKey") String bizKey);

    /**
     * 按ID查询版本（含内容）
     * 
     * @param tableName 表名
     * @param versionId 版本ID
     * @return 版本
     */
    public OptionResourceVersion selectVersionById(@Param("tableName") String tableName,
            @Param("versionId") Long versionId);

    /**
     * 查询版本列表（不含内容）
     * 
     * @param tableName 表名
     * @param bizKey 业务键，可为空
     * @return 版本集合
     */
    public List<OptionResourceVersion> selectVersionList(@Param("tableName") String tableName,
            @Param("bizKey") String bizKey);

    /**
     * 新增同步记录
     * 
     * @param sync 记录
     * @return 结果
     */
    public int insertResourceSync(OptionResourceSync sync);

    /**
     * 查询同步记录
     * 
     * @param sync 查询条件
     * @return 记录集合
     */
    public List<OptionResourceSync> selectResourceSyncList(OptionResourceSync sync);
}
