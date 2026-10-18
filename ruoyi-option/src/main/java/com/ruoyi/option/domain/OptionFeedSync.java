package com.ruoyi.option.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * feed 同步批次 option_feed_sync
 * 
 * @author yscandice
 */
public class OptionFeedSync extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    private Long syncId;

    /** 同步时间 */
    private Date syncTime;

    /** 上游 ETag */
    private String upstreamEtag;

    /** 上游 generated_at */
    private String generatedAt;

    /** etDay */
    private String etDay;

    /** marketEtDay */
    private String marketEtDay;

    /** marketPhase */
    private String marketPhase;

    /** mode */
    private String mode;

    /** schema_version */
    private Integer schemaVersion;

    /** 上游 status */
    private String sourceStatus;

    /** 顶层字段顺序 JSON 数组 */
    private String fieldOrder;

    /** 顶层标量字段 JSON 对象 */
    private String scalarData;

    /** 对象字段版本 {字段:版本ID} */
    private String blockVersions;

    /** 数组清单 {字段:[版本ID]} */
    private String manifest;

    /** 有变化的分组 */
    private String changedGroups;

    /** 数组条目总数 */
    private Integer itemCount;

    /** 新增版本数 */
    private Integer newVersionCount;

    /** 上游响应字节数 */
    private Integer rawBytes;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 状态（0成功 1失败） */
    private String status;

    public Long getSyncId()
    {
        return syncId;
    }

    public void setSyncId(Long syncId)
    {
        this.syncId = syncId;
    }

    public Date getSyncTime()
    {
        return syncTime;
    }

    public void setSyncTime(Date syncTime)
    {
        this.syncTime = syncTime;
    }

    public String getUpstreamEtag()
    {
        return upstreamEtag;
    }

    public void setUpstreamEtag(String upstreamEtag)
    {
        this.upstreamEtag = upstreamEtag;
    }

    public String getGeneratedAt()
    {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt)
    {
        this.generatedAt = generatedAt;
    }

    public String getEtDay()
    {
        return etDay;
    }

    public void setEtDay(String etDay)
    {
        this.etDay = etDay;
    }

    public String getMarketEtDay()
    {
        return marketEtDay;
    }

    public void setMarketEtDay(String marketEtDay)
    {
        this.marketEtDay = marketEtDay;
    }

    public String getMarketPhase()
    {
        return marketPhase;
    }

    public void setMarketPhase(String marketPhase)
    {
        this.marketPhase = marketPhase;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public Integer getSchemaVersion()
    {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion)
    {
        this.schemaVersion = schemaVersion;
    }

    public String getSourceStatus()
    {
        return sourceStatus;
    }

    public void setSourceStatus(String sourceStatus)
    {
        this.sourceStatus = sourceStatus;
    }

    public String getFieldOrder()
    {
        return fieldOrder;
    }

    public void setFieldOrder(String fieldOrder)
    {
        this.fieldOrder = fieldOrder;
    }

    public String getScalarData()
    {
        return scalarData;
    }

    public void setScalarData(String scalarData)
    {
        this.scalarData = scalarData;
    }

    public String getBlockVersions()
    {
        return blockVersions;
    }

    public void setBlockVersions(String blockVersions)
    {
        this.blockVersions = blockVersions;
    }

    public String getManifest()
    {
        return manifest;
    }

    public void setManifest(String manifest)
    {
        this.manifest = manifest;
    }

    public String getChangedGroups()
    {
        return changedGroups;
    }

    public void setChangedGroups(String changedGroups)
    {
        this.changedGroups = changedGroups;
    }

    public Integer getItemCount()
    {
        return itemCount;
    }

    public void setItemCount(Integer itemCount)
    {
        this.itemCount = itemCount;
    }

    public Integer getNewVersionCount()
    {
        return newVersionCount;
    }

    public void setNewVersionCount(Integer newVersionCount)
    {
        this.newVersionCount = newVersionCount;
    }

    public Integer getRawBytes()
    {
        return rawBytes;
    }

    public void setRawBytes(Integer rawBytes)
    {
        this.rawBytes = rawBytes;
    }

    public Integer getCostMs()
    {
        return costMs;
    }

    public void setCostMs(Integer costMs)
    {
        this.costMs = costMs;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    /** 同步时间（UTC） */
    private String syncTimeUtc;

    public String getSyncTimeUtc()
    {
        return syncTimeUtc;
    }

    public void setSyncTimeUtc(String syncTimeUtc)
    {
        this.syncTimeUtc = syncTimeUtc;
    }

    /** 创建时间（UTC） */
    private String createTimeUtc;

    public String getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    public void setCreateTimeUtc(String createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }
}
