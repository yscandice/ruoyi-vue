package com.ruoyi.option.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 信息菜单数据版本 option_info_*
 * 
 * @author yscandice
 */
public class OptionInfoVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 数据源 */
    private String infoKey;

    /** 业务键（日期 / 财报ID / 札记ID，- 为列表索引） */
    private String bizKey;

    /** 内容 SHA-256 */
    private String contentHash;

    /** JSON 内容 */
    private String payload;

    /** 摘要列 */
    private List<OptionColumnValue> columns = new ArrayList<>();

    /** 首次同步时间（东八区） */
    private Date firstSyncTime;

    /** 首次同步时间（UTC） */
    private String firstSyncTimeUtc;

    /** 最近命中时间（东八区） */
    private Date lastSyncTime;

    /** 最近命中时间（UTC） */
    private String lastSyncTimeUtc;

    /** 命中次数 */
    private Integer hitCount;

    /** 创建时间（UTC） */
    private String createTimeUtc;

    /** 更新时间（UTC） */
    private String updateTimeUtc;

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public String getInfoKey()
    {
        return infoKey;
    }

    public void setInfoKey(String infoKey)
    {
        this.infoKey = infoKey;
    }

    public String getBizKey()
    {
        return bizKey;
    }

    public void setBizKey(String bizKey)
    {
        this.bizKey = bizKey;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public String getPayload()
    {
        return payload;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public List<OptionColumnValue> getColumns()
    {
        return columns;
    }

    public void setColumns(List<OptionColumnValue> columns)
    {
        this.columns = columns;
    }

    public Date getFirstSyncTime()
    {
        return firstSyncTime;
    }

    public void setFirstSyncTime(Date firstSyncTime)
    {
        this.firstSyncTime = firstSyncTime;
    }

    public String getFirstSyncTimeUtc()
    {
        return firstSyncTimeUtc;
    }

    public void setFirstSyncTimeUtc(String firstSyncTimeUtc)
    {
        this.firstSyncTimeUtc = firstSyncTimeUtc;
    }

    public Date getLastSyncTime()
    {
        return lastSyncTime;
    }

    public void setLastSyncTime(Date lastSyncTime)
    {
        this.lastSyncTime = lastSyncTime;
    }

    public String getLastSyncTimeUtc()
    {
        return lastSyncTimeUtc;
    }

    public void setLastSyncTimeUtc(String lastSyncTimeUtc)
    {
        this.lastSyncTimeUtc = lastSyncTimeUtc;
    }

    public Integer getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Integer hitCount)
    {
        this.hitCount = hitCount;
    }

    public String getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    public void setCreateTimeUtc(String createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }

    public String getUpdateTimeUtc()
    {
        return updateTimeUtc;
    }

    public void setUpdateTimeUtc(String updateTimeUtc)
    {
        this.updateTimeUtc = updateTimeUtc;
    }
}
