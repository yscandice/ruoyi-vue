package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 会员「更新」阅读进度 option_member_update_seen
 * 
 * @author yscandice
 */
public class OptionMemberUpdateSeen extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 若依用户ID */
    private Long userId;

    /** 已读到的序号 */
    private Integer seenSeq;

    /** 最近标记已读时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date seenAt;

    /** 最近标记已读时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date seenAtUtc;

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setSeenSeq(Integer seenSeq)
    {
        this.seenSeq = seenSeq;
    }

    public Integer getSeenSeq()
    {
        return seenSeq;
    }

    public void setSeenAt(Date seenAt)
    {
        this.seenAt = seenAt;
    }

    public Date getSeenAt()
    {
        return seenAt;
    }

    public void setSeenAtUtc(Date seenAtUtc)
    {
        this.seenAtUtc = seenAtUtc;
    }

    public Date getSeenAtUtc()
    {
        return seenAtUtc;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("seenSeq", getSeenSeq())
            .append("seenAt", getSeenAt())
            .append("seenAtUtc", getSeenAtUtc())
            .toString();
    }
}
