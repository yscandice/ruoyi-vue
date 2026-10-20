package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 会员权限档案 option_member_access
 * 
 * @author yscandice
 */
public class OptionMemberAccess extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 若依用户ID（sys_user.user_id） */
    private Long userId;

    /** 注册邮箱（小写） */
    private String email;

    /** 最近一次计算的身份 STAFF/PAID/TRIAL/DELAYED/DISABLED（仅用于统计） */
    private String level;

    /** 注册时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date registerTime;

    /** 注册时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date registerTimeUtc;

    /** 体验期结束时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date trialEndTime;

    /** 体验期结束时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date trialEndTimeUtc;

    /** 后台累计延长体验期的小时数 */
    private Integer trialExtendHours;

    /** 付费到期时间（东八区，预留） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date paidUntil;

    /** 付费到期时间（UTC，预留） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date paidUntilUtc;

    /** 状态 0正常 1停用 */
    private String status;

    /** 注册IP */
    private String registerIp;

    /** 设备指纹哈希（可选） */
    private String deviceHash;

    /** 最近访问时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastAccessTime;

    /** 创建时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeUtc;

    /** 更新时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTimeUtc;

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getEmail()
    {
        return email;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getLevel()
    {
        return level;
    }

    public void setRegisterTime(Date registerTime)
    {
        this.registerTime = registerTime;
    }

    public Date getRegisterTime()
    {
        return registerTime;
    }

    public void setRegisterTimeUtc(Date registerTimeUtc)
    {
        this.registerTimeUtc = registerTimeUtc;
    }

    public Date getRegisterTimeUtc()
    {
        return registerTimeUtc;
    }

    public void setTrialEndTime(Date trialEndTime)
    {
        this.trialEndTime = trialEndTime;
    }

    public Date getTrialEndTime()
    {
        return trialEndTime;
    }

    public void setTrialEndTimeUtc(Date trialEndTimeUtc)
    {
        this.trialEndTimeUtc = trialEndTimeUtc;
    }

    public Date getTrialEndTimeUtc()
    {
        return trialEndTimeUtc;
    }

    public void setTrialExtendHours(Integer trialExtendHours)
    {
        this.trialExtendHours = trialExtendHours;
    }

    public Integer getTrialExtendHours()
    {
        return trialExtendHours;
    }

    public void setPaidUntil(Date paidUntil)
    {
        this.paidUntil = paidUntil;
    }

    public Date getPaidUntil()
    {
        return paidUntil;
    }

    public void setPaidUntilUtc(Date paidUntilUtc)
    {
        this.paidUntilUtc = paidUntilUtc;
    }

    public Date getPaidUntilUtc()
    {
        return paidUntilUtc;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setRegisterIp(String registerIp)
    {
        this.registerIp = registerIp;
    }

    public String getRegisterIp()
    {
        return registerIp;
    }

    public void setDeviceHash(String deviceHash)
    {
        this.deviceHash = deviceHash;
    }

    public String getDeviceHash()
    {
        return deviceHash;
    }

    public void setLastAccessTime(Date lastAccessTime)
    {
        this.lastAccessTime = lastAccessTime;
    }

    public Date getLastAccessTime()
    {
        return lastAccessTime;
    }

    public void setCreateTimeUtc(Date createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }

    public Date getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    public void setUpdateTimeUtc(Date updateTimeUtc)
    {
        this.updateTimeUtc = updateTimeUtc;
    }

    public Date getUpdateTimeUtc()
    {
        return updateTimeUtc;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("email", getEmail())
            .append("level", getLevel())
            .append("registerTime", getRegisterTime())
            .append("registerTimeUtc", getRegisterTimeUtc())
            .append("trialEndTime", getTrialEndTime())
            .append("trialEndTimeUtc", getTrialEndTimeUtc())
            .append("trialExtendHours", getTrialExtendHours())
            .append("paidUntil", getPaidUntil())
            .append("paidUntilUtc", getPaidUntilUtc())
            .append("status", getStatus())
            .append("registerIp", getRegisterIp())
            .append("deviceHash", getDeviceHash())
            .append("lastAccessTime", getLastAccessTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("createTimeUtc", getCreateTimeUtc())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("updateTimeUtc", getUpdateTimeUtc())
            .append("remark", getRemark())
            .toString();
    }
}
