package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 跟踪菜单同步记录 option_follow_sync
 * 
 * @author yscandice
 */
public class OptionFollowSync extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 同步ID */
    private Long syncId;

    /** 来源 feed 批次 */
    private Long feedSyncId;

    /** feed 同步结果 */
    private String feedResult;

    /** decisions 同步结果 */
    private String decisionResult;

    /** management-choices 同步结果 */
    private String choiceResult;

    /** 美东交易日 */
    private String marketDay;

    /** 来源内容哈希 */
    private String sourceHash;

    /** 结果（UPDATED/UNCHANGED/PAUSED/FAILED） */
    private String result;

    /** 卖方信号标记条数 */
    private Integer signalCount;

    /** 中期信号标记条数 */
    private Integer midtermCount;

    /** 买方信号标记条数 */
    private Integer buyerCount;

    /** 动态管理标记条数 */
    private Integer manageCount;

    /** 新增版本数 */
    private Integer newVersionCount;

    /** 有变化的标记类型 */
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

    public String getDecisionResult()
    {
        return decisionResult;
    }

    public void setDecisionResult(String decisionResult)
    {
        this.decisionResult = decisionResult;
    }

    public String getChoiceResult()
    {
        return choiceResult;
    }

    public void setChoiceResult(String choiceResult)
    {
        this.choiceResult = choiceResult;
    }

    public String getMarketDay()
    {
        return marketDay;
    }

    public void setMarketDay(String marketDay)
    {
        this.marketDay = marketDay;
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

    public Integer getSignalCount()
    {
        return signalCount;
    }

    public void setSignalCount(Integer signalCount)
    {
        this.signalCount = signalCount;
    }

    public Integer getMidtermCount()
    {
        return midtermCount;
    }

    public void setMidtermCount(Integer midtermCount)
    {
        this.midtermCount = midtermCount;
    }

    public Integer getBuyerCount()
    {
        return buyerCount;
    }

    public void setBuyerCount(Integer buyerCount)
    {
        this.buyerCount = buyerCount;
    }

    public Integer getManageCount()
    {
        return manageCount;
    }

    public void setManageCount(Integer manageCount)
    {
        this.manageCount = manageCount;
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
