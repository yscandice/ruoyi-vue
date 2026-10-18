package com.ruoyi.option.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * feed 数组条目版本（多张条目表共用）
 * 
 * @author yscandice
 */
public class OptionFeedItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 所属数组字段 */
    private String listType;

    /** 条目主键 */
    private String itemId;

    /** 内容哈希（不含易变字段） */
    private String contentHash;

    /** 标的 */
    private String symbol;

    /** 信号ID */
    private String signalId;

    /** 状态 */
    private String itemState;

    /** 类型 */
    private String itemKind;

    /** 业务日期 */
    private String itemDate;

    /** 条目内容 JSON */
    private String payload;

    /** 易变字段 JSON */
    private String volatileData;

    /** 首次出现批次 */
    private Long firstSyncId;

    /** 最后出现批次 */
    private Long lastSyncId;

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public String getListType()
    {
        return listType;
    }

    public void setListType(String listType)
    {
        this.listType = listType;
    }

    public String getItemId()
    {
        return itemId;
    }

    public void setItemId(String itemId)
    {
        this.itemId = itemId;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
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

    public String getItemState()
    {
        return itemState;
    }

    public void setItemState(String itemState)
    {
        this.itemState = itemState;
    }

    public String getItemKind()
    {
        return itemKind;
    }

    public void setItemKind(String itemKind)
    {
        this.itemKind = itemKind;
    }

    public String getItemDate()
    {
        return itemDate;
    }

    public void setItemDate(String itemDate)
    {
        this.itemDate = itemDate;
    }

    public String getPayload()
    {
        return payload;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public String getVolatileData()
    {
        return volatileData;
    }

    public void setVolatileData(String volatileData)
    {
        this.volatileData = volatileData;
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

    /** 创建时间（UTC） */
    private String createTimeUtc;

    public String getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    public void setCreateTimeUtc(String createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }

    /** 更新时间（UTC） */
    private String updateTimeUtc;

    public String getUpdateTimeUtc()
    {
        return updateTimeUtc;
    }

    public void setUpdateTimeUtc(String updateTimeUtc)
    {
        this.updateTimeUtc = updateTimeUtc;
    }
}
