package com.ruoyi.option.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从多表拼接出的 feed（完整 + 分组）
 * 
 * @author yscandice
 */
public class OptionFeedSnapshot
{
    /** 批次ID */
    private Long syncId;

    /** 上游 generated_at */
    private String generatedAt;

    /** 同步时间 */
    private String syncTime;

    /** 分组名 -> JSON（含 full） */
    private final Map<String, String> bodies = new LinkedHashMap<>();

    /** 分组名 -> ETag（含 full） */
    private final Map<String, String> etags = new LinkedHashMap<>();

    public Long getSyncId()
    {
        return syncId;
    }

    public void setSyncId(Long syncId)
    {
        this.syncId = syncId;
    }

    public String getGeneratedAt()
    {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt)
    {
        this.generatedAt = generatedAt;
    }

    public String getSyncTime()
    {
        return syncTime;
    }

    public void setSyncTime(String syncTime)
    {
        this.syncTime = syncTime;
    }

    public Map<String, String> getBodies()
    {
        return bodies;
    }

    public Map<String, String> getEtags()
    {
        return etags;
    }
}
