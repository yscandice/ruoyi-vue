package com.ruoyi.option.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 接口内容版本（option_res_* 多张表共用）
 * 
 * @author yscandice
 */
public class OptionResourceVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 接口名 */
    private String resourceKey;

    /** 业务键（premarket 为日期，其余为 -） */
    private String bizKey;

    /** 内容哈希 */
    private String contentHash;

    /** 接口原始 JSON */
    private String payload;

    /** 摘要列 */
    private List<OptionColumnValue> columns = new ArrayList<>();

    /** 首次出现时间 */
    private Date firstSyncTime;

    /** 最后出现时间 */
    private Date lastSyncTime;

    /** 出现次数 */
    private Integer hitCount;

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public String getResourceKey()
    {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey)
    {
        this.resourceKey = resourceKey;
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

    public Date getLastSyncTime()
    {
        return lastSyncTime;
    }

    public void setLastSyncTime(Date lastSyncTime)
    {
        this.lastSyncTime = lastSyncTime;
    }

    public Integer getHitCount()
    {
        return hitCount;
    }

    public void setHitCount(Integer hitCount)
    {
        this.hitCount = hitCount;
    }
}
