package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 进行菜单栏目条目版本 option_conduct_{scope}
 * 
 * @author yscandice
 */
public class OptionConductItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 条目键 */
    private String itemKey;

    /** 内容哈希 */
    private String contentHash;

    /** 生命周期键 */
    private String threadKey;

    /** 标的 */
    private String symbol;

    /** 信号ID */
    private String signalId;

    /** 推荐ID */
    private String recommendationId;

    /** 方向 */
    private String intent;

    /** 价差结构 */
    private String structure;

    /** 到期日 */
    private String expiryDate;

    /** 状态 */
    private String itemState;

    /** 状态码 */
    private String statusCode;

    /** 结果类型 */
    private String resultKind;

    /** 发出时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date emittedTime;

    /** 发出时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date emittedTimeUtc;

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

    public String getThreadKey()
    {
        return threadKey;
    }

    public void setThreadKey(String threadKey)
    {
        this.threadKey = threadKey;
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

    public String getItemState()
    {
        return itemState;
    }

    public void setItemState(String itemState)
    {
        this.itemState = itemState;
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
