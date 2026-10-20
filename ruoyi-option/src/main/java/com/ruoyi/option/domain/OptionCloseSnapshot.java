package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 盘后视图冻结记录 option_close_snapshot
 * 
 * @author yscandice
 */
public class OptionCloseSnapshot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 冻结ID */
    private Long snapshotId;

    /** 交易日（美东） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date tradeDate;

    /** 收盘冻结点（美东） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cutoffEt;

    /** 收盘冻结点（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cutoffTime;

    /** 收盘冻结点（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cutoffTimeUtc;

    /** 冻结结果 SUCCESS/PARTIAL/FAILED */
    private String result;

    /** 放出状态 FROZEN已冻结未放出/RELEASED已放出/EXPIRED已被后续放出替代 */
    private String status;

    /** 放出时间（东八区，= 下一个交易日收盘冻结点） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date releasedAt;

    /** 放出时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date releasedAtUtc;

    /** 冻结菜单数 */
    private Integer resourceCount;

    /** 冻结 Redis 键数 */
    private Integer keyCount;

    /** 冻结数据字节数 */
    private Long totalBytes;

    /** 数据可能过期的菜单（最近同步失败或早于收盘） */
    private String staleResources;

    /** 各菜单来源同步ID {"conduct":123,...} */
    private String sourceSync;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 触发方式 JOB/MANUAL */
    private String triggerType;

    /** 说明/错误信息 */
    private String message;

    /** 创建时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeUtc;

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setTradeDate(Date tradeDate)
    {
        this.tradeDate = tradeDate;
    }

    public Date getTradeDate()
    {
        return tradeDate;
    }

    public void setCutoffEt(Date cutoffEt)
    {
        this.cutoffEt = cutoffEt;
    }

    public Date getCutoffEt()
    {
        return cutoffEt;
    }

    public void setCutoffTime(Date cutoffTime)
    {
        this.cutoffTime = cutoffTime;
    }

    public Date getCutoffTime()
    {
        return cutoffTime;
    }

    public void setCutoffTimeUtc(Date cutoffTimeUtc)
    {
        this.cutoffTimeUtc = cutoffTimeUtc;
    }

    public Date getCutoffTimeUtc()
    {
        return cutoffTimeUtc;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getResult()
    {
        return result;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setReleasedAt(Date releasedAt)
    {
        this.releasedAt = releasedAt;
    }

    public Date getReleasedAt()
    {
        return releasedAt;
    }

    public void setReleasedAtUtc(Date releasedAtUtc)
    {
        this.releasedAtUtc = releasedAtUtc;
    }

    public Date getReleasedAtUtc()
    {
        return releasedAtUtc;
    }

    public void setResourceCount(Integer resourceCount)
    {
        this.resourceCount = resourceCount;
    }

    public Integer getResourceCount()
    {
        return resourceCount;
    }

    public void setKeyCount(Integer keyCount)
    {
        this.keyCount = keyCount;
    }

    public Integer getKeyCount()
    {
        return keyCount;
    }

    public void setTotalBytes(Long totalBytes)
    {
        this.totalBytes = totalBytes;
    }

    public Long getTotalBytes()
    {
        return totalBytes;
    }

    public void setStaleResources(String staleResources)
    {
        this.staleResources = staleResources;
    }

    public String getStaleResources()
    {
        return staleResources;
    }

    public void setSourceSync(String sourceSync)
    {
        this.sourceSync = sourceSync;
    }

    public String getSourceSync()
    {
        return sourceSync;
    }

    public void setCostMs(Integer costMs)
    {
        this.costMs = costMs;
    }

    public Integer getCostMs()
    {
        return costMs;
    }

    public void setTriggerType(String triggerType)
    {
        this.triggerType = triggerType;
    }

    public String getTriggerType()
    {
        return triggerType;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getMessage()
    {
        return message;
    }

    public void setCreateTimeUtc(Date createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }

    public Date getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("snapshotId", getSnapshotId())
            .append("tradeDate", getTradeDate())
            .append("cutoffEt", getCutoffEt())
            .append("cutoffTime", getCutoffTime())
            .append("cutoffTimeUtc", getCutoffTimeUtc())
            .append("result", getResult())
            .append("status", getStatus())
            .append("releasedAt", getReleasedAt())
            .append("releasedAtUtc", getReleasedAtUtc())
            .append("resourceCount", getResourceCount())
            .append("keyCount", getKeyCount())
            .append("totalBytes", getTotalBytes())
            .append("staleResources", getStaleResources())
            .append("sourceSync", getSourceSync())
            .append("costMs", getCostMs())
            .append("triggerType", getTriggerType())
            .append("message", getMessage())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("createTimeUtc", getCreateTimeUtc())
            .toString();
    }
}
