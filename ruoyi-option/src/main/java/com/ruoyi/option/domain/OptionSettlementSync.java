package com.ruoyi.option.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 结算菜单同步流水 option_settlement_sync
 * 
 * @author yscandice
 */
public class OptionSettlementSync extends BaseEntity
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

    /** 结果 */
    private String result;

    /** 已结算记录条数 */
    private Integer recordCount;

    /** 其中中期已结算条数 */
    private Integer midtermCount;

    /** 结算单条数 */
    private Integer cardCount;

    /** 结算统计条数 */
    private Integer statCount;

    /** 已结算笔数（strategyTracking.settledTotal） */
    private Integer settledCount;

    /** 结算盈亏 */
    private BigDecimal settledPnlUsd;

    /** 管理前对照 */
    private BigDecimal rawPnlUsd;

    /** 保护成本 */
    private BigDecimal protectionCostUsd;

    /** 动态管理成本 */
    private BigDecimal manageCostUsd;

    /** 动态管理收入 */
    private BigDecimal manageCreditUsd;

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

    public Integer getRecordCount()
    {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount)
    {
        this.recordCount = recordCount;
    }

    public Integer getMidtermCount()
    {
        return midtermCount;
    }

    public void setMidtermCount(Integer midtermCount)
    {
        this.midtermCount = midtermCount;
    }

    public Integer getCardCount()
    {
        return cardCount;
    }

    public void setCardCount(Integer cardCount)
    {
        this.cardCount = cardCount;
    }

    public Integer getStatCount()
    {
        return statCount;
    }

    public void setStatCount(Integer statCount)
    {
        this.statCount = statCount;
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

    public BigDecimal getRawPnlUsd()
    {
        return rawPnlUsd;
    }

    public void setRawPnlUsd(BigDecimal rawPnlUsd)
    {
        this.rawPnlUsd = rawPnlUsd;
    }

    public BigDecimal getProtectionCostUsd()
    {
        return protectionCostUsd;
    }

    public void setProtectionCostUsd(BigDecimal protectionCostUsd)
    {
        this.protectionCostUsd = protectionCostUsd;
    }

    public BigDecimal getManageCostUsd()
    {
        return manageCostUsd;
    }

    public void setManageCostUsd(BigDecimal manageCostUsd)
    {
        this.manageCostUsd = manageCostUsd;
    }

    public BigDecimal getManageCreditUsd()
    {
        return manageCreditUsd;
    }

    public void setManageCreditUsd(BigDecimal manageCreditUsd)
    {
        this.manageCreditUsd = manageCreditUsd;
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
