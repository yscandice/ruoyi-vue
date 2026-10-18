package com.ruoyi.option.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 信息菜单同步记录 option_info_sync
 * 
 * @author yscandice
 */
public class OptionInfoSync extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long syncId;

    /** 数据源 */
    private String infoKey;

    /** 业务键 */
    private String bizKey;

    /** 上游 HTTP 状态 */
    private Integer httpStatus;

    /** 结果（UPDATED/UNCHANGED/FAILED） */
    private String result;

    /** 对应版本ID */
    private Long versionId;

    /** 响应字节数 */
    private Integer rawBytes;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 说明 */
    private String message;

    /** 触发方式（JOB/MANUAL） */
    private String triggerType;

    /** 同步时间（东八区） */
    private Date syncTime;

    /** 同步时间（UTC） */
    private String syncTimeUtc;

    public Long getSyncId()
    {
        return syncId;
    }

    public void setSyncId(Long syncId)
    {
        this.syncId = syncId;
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

    public Integer getHttpStatus()
    {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus)
    {
        this.httpStatus = httpStatus;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
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

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getTriggerType()
    {
        return triggerType;
    }

    public void setTriggerType(String triggerType)
    {
        this.triggerType = triggerType;
    }

    public Date getSyncTime()
    {
        return syncTime;
    }

    public void setSyncTime(Date syncTime)
    {
        this.syncTime = syncTime;
    }

    public String getSyncTimeUtc()
    {
        return syncTimeUtc;
    }

    public void setSyncTimeUtc(String syncTimeUtc)
    {
        this.syncTimeUtc = syncTimeUtc;
    }
}
