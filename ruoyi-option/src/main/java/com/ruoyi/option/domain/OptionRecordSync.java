package com.ruoyi.option.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 战绩菜单同步流水 option_record_sync
 * 
 * @author yscandice
 */
public class OptionRecordSync extends BaseEntity
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

    /** 美东交易日 */
    private String marketDay;

    /** 来源哈希 */
    private String sourceHash;

    /** 结果 */
    private String result;

    /** 战绩明细条数 */
    private Integer tradeCount;

    /** 每日结算净额条数 */
    private Integer dailyCount;

    /** 按标的条数 */
    private Integer symbolCount;

    /** 战绩统计条数 */
    private Integer statCount;

    /** 账户模拟条数 */
    private Integer accountCount;

    /** 已结算笔数 */
    private Integer settledCount;

    /** 结算盈亏 */
    private BigDecimal settledPnlUsd;

    /** 管理后累计盈亏 */
    private BigDecimal managedPnlUsd;

    /** 管理前对照 */
    private BigDecimal rawPnlUsd;

    /** 胜率% */
    private BigDecimal winRate;

    /** 我跟了且已结算笔数 */
    private Integer adoptedCount;

    /** 新增版本数 */
    private Integer newVersionCount;

    /** 有变化的类型 */
    private String changedKinds;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 说明/错误信息 */
    private String message;

    /** 触发方式 */
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

    public Integer getTradeCount()
    {
        return tradeCount;
    }

    public void setTradeCount(Integer tradeCount)
    {
        this.tradeCount = tradeCount;
    }

    public Integer getDailyCount()
    {
        return dailyCount;
    }

    public void setDailyCount(Integer dailyCount)
    {
        this.dailyCount = dailyCount;
    }

    public Integer getSymbolCount()
    {
        return symbolCount;
    }

    public void setSymbolCount(Integer symbolCount)
    {
        this.symbolCount = symbolCount;
    }

    public Integer getStatCount()
    {
        return statCount;
    }

    public void setStatCount(Integer statCount)
    {
        this.statCount = statCount;
    }

    public Integer getAccountCount()
    {
        return accountCount;
    }

    public void setAccountCount(Integer accountCount)
    {
        this.accountCount = accountCount;
    }

    public Integer getSettledCount()
    {
        return settledCount;
    }

    public void setSettledCount(Integer settledCount)
    {
        this.settledCount = settledCount;
    }

    public BigDecimal getSettledPnlUsd()
    {
        return settledPnlUsd;
    }

    public void setSettledPnlUsd(BigDecimal settledPnlUsd)
    {
        this.settledPnlUsd = settledPnlUsd;
    }

    public BigDecimal getManagedPnlUsd()
    {
        return managedPnlUsd;
    }

    public void setManagedPnlUsd(BigDecimal managedPnlUsd)
    {
        this.managedPnlUsd = managedPnlUsd;
    }

    public BigDecimal getRawPnlUsd()
    {
        return rawPnlUsd;
    }

    public void setRawPnlUsd(BigDecimal rawPnlUsd)
    {
        this.rawPnlUsd = rawPnlUsd;
    }

    public BigDecimal getWinRate()
    {
        return winRate;
    }

    public void setWinRate(BigDecimal winRate)
    {
        this.winRate = winRate;
    }

    public Integer getAdoptedCount()
    {
        return adoptedCount;
    }

    public void setAdoptedCount(Integer adoptedCount)
    {
        this.adoptedCount = adoptedCount;
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
