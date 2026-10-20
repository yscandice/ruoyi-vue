package com.ruoyi.option.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 盘后视图冻结数据 option_close_snapshot_data
 * 
 * @author yscandice
 */
public class OptionCloseSnapshotData extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long dataId;

    /** 冻结ID */
    private Long snapshotId;

    /** 菜单 */
    private String resourceCode;

    /** 实时视图键（不含 option:delayed: 前缀转换） */
    private String redisKey;

    /** Redis 类型 string/hash */
    private String valueType;

    /** 内容 SHA-256 */
    private String contentHash;

    /** 值（gzip；hash 类型为 field→value 的 JSON） */
    @JsonIgnore
    private byte[] payload;

    public void setDataId(Long dataId)
    {
        this.dataId = dataId;
    }

    public Long getDataId()
    {
        return dataId;
    }

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setResourceCode(String resourceCode)
    {
        this.resourceCode = resourceCode;
    }

    public String getResourceCode()
    {
        return resourceCode;
    }

    public void setRedisKey(String redisKey)
    {
        this.redisKey = redisKey;
    }

    public String getRedisKey()
    {
        return redisKey;
    }

    public void setValueType(String valueType)
    {
        this.valueType = valueType;
    }

    public String getValueType()
    {
        return valueType;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setPayload(byte[] payload)
    {
        this.payload = payload;
    }

    public byte[] getPayload()
    {
        return payload;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("dataId", getDataId())
            .append("snapshotId", getSnapshotId())
            .append("resourceCode", getResourceCode())
            .append("redisKey", getRedisKey())
            .append("valueType", getValueType())
            .append("contentHash", getContentHash())
            .append("payload", payload == null ? null : payload.length + " bytes")
            .append("createTime", getCreateTime())
            .toString();
    }
}
