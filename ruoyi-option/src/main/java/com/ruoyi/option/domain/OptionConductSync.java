package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 进行菜单同步记录 option_conduct_sync
 * 
 * @author yscandice
 */
public class OptionConductSync extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 同步ID */
    private Long syncId;

    /** 来源 feed 批次 */
    private Long feedSyncId;

    /** feed 同步结果 */
    private String feedResult;

    /** 美东交易日 */
    private String marketDay;

    /** 结果（UPDATED/UNCHANGED/PAUSED/FAILED） */
    private String result;

    /** 进行中条数 */
    private Integer activeCount;

    /** 保护窗口条数 */
    private Integer protectionCount;

    /** 动态管理条数 */
    private Integer manageCount;

    /** 已到期条数 */
    private Integer expiredCount;

    /** 历史信号条数 */
    private Integer historyCount;

    /** 新增版本数 */
    private Integer newVersionCount;

    /** 有变化的栏目 */
    private String changedScopes;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 说明 */
    private String message;

    /** 触发方式（JOB/MANUAL） */
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

    public Long getFeedSyncId()
    {
        return feedSyncId;
    }

    public void setFeedSyncId(Long feedSyncId)
    {
        this.feedSyncId = feedSyncId;
    }

    public String getFeedResult()
    {
        return feedResult;
    }

    public void setFeedResult(String feedResult)
    {
        this.feedResult = feedResult;
    }

    public String getMarketDay()
    {
        return marketDay;
    }

    public void setMarketDay(String marketDay)
    {
        this.marketDay = marketDay;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public Integer getActiveCount()
    {
        return activeCount;
    }

    public void setActiveCount(Integer activeCount)
    {
        this.activeCount = activeCount;
    }

    public Integer getProtectionCount()
    {
        return protectionCount;
    }

    public void setProtectionCount(Integer protectionCount)
    {
        this.protectionCount = protectionCount;
    }

    public Integer getManageCount()
    {
        return manageCount;
    }

    public void setManageCount(Integer manageCount)
    {
        this.manageCount = manageCount;
    }

    public Integer getExpiredCount()
    {
        return expiredCount;
    }

    public void setExpiredCount(Integer expiredCount)
    {
        this.expiredCount = expiredCount;
    }

    public Integer getHistoryCount()
    {
        return historyCount;
    }

    public void setHistoryCount(Integer historyCount)
    {
        this.historyCount = historyCount;
    }

    public Integer getNewVersionCount()
    {
        return newVersionCount;
    }

    public void setNewVersionCount(Integer newVersionCount)
    {
        this.newVersionCount = newVersionCount;
    }

    public String getChangedScopes()
    {
        return changedScopes;
    }

    public void setChangedScopes(String changedScopes)
    {
        this.changedScopes = changedScopes;
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
