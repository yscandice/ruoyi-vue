package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 更新菜单同步流水 option_update_sync
 * 
 * @author yscandice
 */
public class OptionUpdateSync extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 同步ID */
    private Long syncId;

    /** 来源哈希 */
    private String sourceHash;

    /** 结果 UPDATED/UNCHANGED/PAUSED/FAILED */
    private String result;

    /** 抓取页面数 */
    private Integer pageCount;

    /** 更新条目数 */
    private Integer entryCount;

    /** 最新上线日期 */
    private String latestDate;

    /** 同步时 /api/member/updates 的 currentSeq */
    private Integer currentSeq;

    /** 传输字节数 */
    private Integer transferBytes;

    /** 新增版本数 */
    private Integer newVersionCount;

    /** 有变化的类型 */
    private String changedKinds;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 说明/错误信息 */
    private String message;

    /** 触发方式 JOB/MANUAL */
    private String triggerType;

    /** 同步时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date syncTime;

    /** 同步时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date syncTimeUtc;

    /** 创建时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeUtc;

    public Long getSyncId()
    {
        return syncId;
    }

    public void setSyncId(Long syncId)
    {
        this.syncId = syncId;
    }

    public String getSourceHash()
    {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash)
    {
        this.sourceHash = sourceHash;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public Integer getPageCount()
    {
        return pageCount;
    }

    public void setPageCount(Integer pageCount)
    {
        this.pageCount = pageCount;
    }

    public Integer getEntryCount()
    {
        return entryCount;
    }

    public void setEntryCount(Integer entryCount)
    {
        this.entryCount = entryCount;
    }

    public String getLatestDate()
    {
        return latestDate;
    }

    public void setLatestDate(String latestDate)
    {
        this.latestDate = latestDate;
    }

    public Integer getCurrentSeq()
    {
        return currentSeq;
    }

    public void setCurrentSeq(Integer currentSeq)
    {
        this.currentSeq = currentSeq;
    }

    public Integer getTransferBytes()
    {
        return transferBytes;
    }

    public void setTransferBytes(Integer transferBytes)
    {
        this.transferBytes = transferBytes;
    }

    public Integer getNewVersionCount()
    {
        return newVersionCount;
    }

    public void setNewVersionCount(Integer newVersionCount)
    {
        this.newVersionCount = newVersionCount;
    }

    public String getChangedKinds()
    {
        return changedKinds;
    }

    public void setChangedKinds(String changedKinds)
    {
        this.changedKinds = changedKinds;
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

    public Date getSyncTimeUtc()
    {
        return syncTimeUtc;
    }

    public void setSyncTimeUtc(Date syncTimeUtc)
    {
        this.syncTimeUtc = syncTimeUtc;
    }

    public Date getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    public void setCreateTimeUtc(Date createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }
}
