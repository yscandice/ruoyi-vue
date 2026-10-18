package com.ruoyi.option.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * feed 对象字段版本 option_feed_block
 * 
 * @author yscandice
 */
public class OptionFeedBlock extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 顶层字段名 */
    private String blockKey;

    /** 内容哈希 */
    private String contentHash;

    /** 字段内容 JSON */
    private String payload;

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

    public String getBlockKey()
    {
        return blockKey;
    }

    public void setBlockKey(String blockKey)
    {
        this.blockKey = blockKey;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
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
}
