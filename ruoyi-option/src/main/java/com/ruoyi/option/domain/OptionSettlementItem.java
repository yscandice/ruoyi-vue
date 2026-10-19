package com.ruoyi.option.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 结算菜单条目版本 option_settlement_{kind}
 * 
 * @author yscandice
 */
public class OptionSettlementItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 条目键 */
    private String itemKey;

    /** 内容哈希 */
    private String contentHash;

    /** 记录类型 */
    private String recordType;

    /** 标的 */
    private String symbol;

    /** 信号ID */
    private String signalId;

    /** 推荐ID */
    private String recommendationId;

    /** 父信号ID */
    private String parentSignalId;

    /** 方向 */
    private String intent;

    /** 价差结构 */
    private String structure;

    /** 到期日 */
    private String expiryDate;

    /** 状态码 */
    private String statusCode;

    /** 结果类型 */
    private String resultKind;

    /** 结算日 MM/dd */
    private String settledDay;

    /** 发报日 MM/dd */
    private String issuedDay;

    /** 盈亏（美元） */
    private BigDecimal pnlUsd;

    /** 发报时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date emittedTime;

    /** 发报时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date emittedTimeUtc;

    /** 结算时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date settledTime;

    /** 结算时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date settledTimeUtc;

    /** 排序号 */
    private Integer sortNo;

    /** 条目内容 JSON */
    private String payload;

    /** 首次出现批次 */
    private Long firstSyncId;

    /** 最后出现批次 */
    private Long lastSyncId;

    /** 创建时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeUtc;

    /** 更新时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTimeUtc;

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public String getItemKey()
    {
        return itemKey;
    }

    public void setItemKey(String itemKey)
    {
        this.itemKey = itemKey;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public String getRecordType()
    {
        return recordType;
    }

    public void setRecordType(String recordType)
    {
        this.recordType = recordType;
    }

    public String getSymbol()
    {
        return symbol;
    }

    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }

    public String getSignalId()
    {
        return signalId;
    }

    public void setSignalId(String signalId)
    {
        this.signalId = signalId;
    }

    public String getRecommendationId()
    {
        return recommendationId;
    }

    public void setRecommendationId(String recommendationId)
    {
        this.recommendationId = recommendationId;
    }

    public String getParentSignalId()
    {
        return parentSignalId;
    }

    public void setParentSignalId(String parentSignalId)
    {
        this.parentSignalId = parentSignalId;
    }

    public String getIntent()
    {
        return intent;
    }

    public void setIntent(String intent)
    {
        this.intent = intent;
    }

    public String getStructure()
    {
        return structure;
    }

    public void setStructure(String structure)
    {
        this.structure = structure;
    }

    public String getExpiryDate()
    {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate)
    {
        this.expiryDate = expiryDate;
    }

    public String getStatusCode()
    {
        return statusCode;
    }

    public void setStatusCode(String statusCode)
    {
        this.statusCode = statusCode;
    }

    public String getResultKind()
    {
        return resultKind;
    }

    public void setResultKind(String resultKind)
    {
        this.resultKind = resultKind;
    }

    public String getSettledDay()
    {
        return settledDay;
    }

    public void setSettledDay(String settledDay)
    {
        this.settledDay = settledDay;
    }

    public String getIssuedDay()
    {
        return issuedDay;
    }

    public void setIssuedDay(String issuedDay)
    {
        this.issuedDay = issuedDay;
    }

    public BigDecimal getPnlUsd()
    {
        return pnlUsd;
    }

    public void setPnlUsd(BigDecimal pnlUsd)
    {
        this.pnlUsd = pnlUsd;
    }

    public Date getEmittedTime()
    {
        return emittedTime;
    }

    public void setEmittedTime(Date emittedTime)
    {
        this.emittedTime = emittedTime;
    }

    public Date getEmittedTimeUtc()
    {
        return emittedTimeUtc;
    }

    public void setEmittedTimeUtc(Date emittedTimeUtc)
    {
        this.emittedTimeUtc = emittedTimeUtc;
    }

    public Date getSettledTime()
    {
        return settledTime;
    }

    public void setSettledTime(Date settledTime)
    {
        this.settledTime = settledTime;
    }

    public Date getSettledTimeUtc()
    {
        return settledTimeUtc;
    }

    public void setSettledTimeUtc(Date settledTimeUtc)
    {
        this.settledTimeUtc = settledTimeUtc;
    }

    public Integer getSortNo()
    {
        return sortNo;
    }

    public void setSortNo(Integer sortNo)
    {
        this.sortNo = sortNo;
    }

    public String getPayload()
    {
        return payload;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public Long getFirstSyncId()
    {
        return firstSyncId;
    }

    public void setFirstSyncId(Long firstSyncId)
    {
        this.firstSyncId = firstSyncId;
    }

    public Long getLastSyncId()
    {
        return lastSyncId;
    }

    public void setLastSyncId(Long lastSyncId)
    {
        this.lastSyncId = lastSyncId;
    }

    public Date getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    public void setCreateTimeUtc(Date createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }

    public Date getUpdateTimeUtc()
    {
        return updateTimeUtc;
    }

    public void setUpdateTimeUtc(Date updateTimeUtc)
    {
        this.updateTimeUtc = updateTimeUtc;
    }
}
