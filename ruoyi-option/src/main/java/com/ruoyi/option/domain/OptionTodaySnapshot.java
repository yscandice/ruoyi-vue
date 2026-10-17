package com.ruoyi.option.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

/** 今日页面接口响应快照。保留上游 JSON，避免对未定型嵌套字段做错误建模。 */
public class OptionTodaySnapshot extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long snapshotId;
    private String resourceKey;
    private Long userId;
    private Date businessDate;
    private String payload;
    private String sourceEtag;
    private Date generatedAt;

    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Date getBusinessDate() { return businessDate; }
    public void setBusinessDate(Date businessDate) { this.businessDate = businessDate; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getSourceEtag() { return sourceEtag; }
    public void setSourceEtag(String sourceEtag) { this.sourceEtag = sourceEtag; }
    public Date getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Date generatedAt) { this.generatedAt = generatedAt; }
}
