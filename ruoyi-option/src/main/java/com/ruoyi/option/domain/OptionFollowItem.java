package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 跟踪菜单标记版本 option_follow_{kind}
 * 
 * @author yscandice
 */
public class OptionFollowItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 条目键（eventId / cardId） */
    private String itemKey;

    /** 内容哈希 */
    private String contentHash;

    /** 卡片类型 */
    private String cardKind;

    /** 卡片ID */
    private String cardId;

    /** 标记事件ID */
    private String eventId;

    /** 标的 */
    private String symbol;

    /** 信号ID */
    private String signalId;

    /** 推荐ID */
    private String recommendationId;

    /** 标记类型 ADOPT_INTENT/OBSERVE/SKIP */
    private String decisionType;

    /** 原始选择 */
    private String rawChoice;

    /** 采用方式 AUTO/MANUAL */
    private String adoptMode;

    /** 组数 */
    private Integer quantity;

    /** 否决原因 */
    private String skipReason;

    /** 价差结构 */
    private String structure;

    /** 到期日 */
    private String expiryDate;

    /** 系统参考分类 */
    private String category;

    /** 信号状态 */
    private String itemState;

    /** 系统参考浮动盈亏 */
    private String floatingPnl;

    /** 结算日 MM/dd */
    private String settledDay;

    /** 标记时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date markedTime;

    /** 标记时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date markedTimeUtc;

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

    public String getCardKind()
    {
        return cardKind;
    }

    public void setCardKind(String cardKind)
    {
        this.cardKind = cardKind;
    }

    public String getCardId()
    {
        return cardId;
    }

    public void setCardId(String cardId)
    {
        this.cardId = cardId;
    }

    public String getEventId()
    {
        return eventId;
    }

    public void setEventId(String eventId)
    {
        this.eventId = eventId;
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

    public String getDecisionType()
    {
        return decisionType;
    }

    public void setDecisionType(String decisionType)
    {
        this.decisionType = decisionType;
    }

    public String getRawChoice()
    {
        return rawChoice;
    }

    public void setRawChoice(String rawChoice)
    {
        this.rawChoice = rawChoice;
    }

    public String getAdoptMode()
    {
        return adoptMode;
    }

    public void setAdoptMode(String adoptMode)
    {
        this.adoptMode = adoptMode;
    }

    public Integer getQuantity()
    {
        return quantity;
    }

    public void setQuantity(Integer quantity)
    {
        this.quantity = quantity;
    }

    public String getSkipReason()
    {
        return skipReason;
    }

    public void setSkipReason(String skipReason)
    {
        this.skipReason = skipReason;
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

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getItemState()
    {
        return itemState;
    }

    public void setItemState(String itemState)
    {
        this.itemState = itemState;
    }

    public String getFloatingPnl()
    {
        return floatingPnl;
    }

    public void setFloatingPnl(String floatingPnl)
    {
        this.floatingPnl = floatingPnl;
    }

    public String getSettledDay()
    {
        return settledDay;
    }

    public void setSettledDay(String settledDay)
    {
        this.settledDay = settledDay;
    }

    public Date getMarkedTime()
    {
        return markedTime;
    }

    public void setMarkedTime(Date markedTime)
    {
        this.markedTime = markedTime;
    }

    public Date getMarkedTimeUtc()
    {
        return markedTimeUtc;
    }

    public void setMarkedTimeUtc(Date markedTimeUtc)
    {
        this.markedTimeUtc = markedTimeUtc;
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
