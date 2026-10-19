package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OSM 更新菜单条目版本 option_update_{kind}（page 页头 / entry 更新条目）
 * 
 * @author yscandice
 */
public class OptionUpdateItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 条目键（页头 updates / 条目锚点去掉 u- 前缀，如 2026-10-19-roll-safer） */
    private String itemKey;

    /** 内容哈希 */
    private String contentHash;

    /** 页面路径（含锚点） */
    private String pagePath;

    /** 标题 */
    private String title;

    /** 副标题/摘要（页头说明 / 条目「做了什么」前 500 字） */
    private String subTitle;

    /** 上线日期 yyyy-MM-dd（页头为最新一条） */
    private String publishDate;

    /** 目标链接（「去用一下」/ 页头设置入口） */
    private String targetPath;

    /** 配图类型 video/svg/image/none */
    private String visualType;

    /** 配图地址（视频 / 海报 / 吉祥物） */
    private String mediaUrl;

    /** 子项数（页头为条目数） */
    private Integer itemCount;

    /** 正文字数（不含空白） */
    private Integer textLength;

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

    public String getPagePath()
    {
        return pagePath;
    }

    public void setPagePath(String pagePath)
    {
        this.pagePath = pagePath;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSubTitle()
    {
        return subTitle;
    }

    public void setSubTitle(String subTitle)
    {
        this.subTitle = subTitle;
    }

    public String getPublishDate()
    {
        return publishDate;
    }

    public void setPublishDate(String publishDate)
    {
        this.publishDate = publishDate;
    }

    public String getTargetPath()
    {
        return targetPath;
    }

    public void setTargetPath(String targetPath)
    {
        this.targetPath = targetPath;
    }

    public String getVisualType()
    {
        return visualType;
    }

    public void setVisualType(String visualType)
    {
        this.visualType = visualType;
    }

    public String getMediaUrl()
    {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl)
    {
        this.mediaUrl = mediaUrl;
    }

    public Integer getItemCount()
    {
        return itemCount;
    }

    public void setItemCount(Integer itemCount)
    {
        this.itemCount = itemCount;
    }

    public Integer getTextLength()
    {
        return textLength;
    }

    public void setTextLength(Integer textLength)
    {
        this.textLength = textLength;
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
