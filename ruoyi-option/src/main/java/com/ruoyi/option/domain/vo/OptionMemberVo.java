package com.ruoyi.option.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 会员管理列表对象（技术文档 9.3.2 OptionMemberVo），同时作为列表筛选条件
 * 
 * 筛选：email（模糊）、level、status、registerIp；params.beginTime / endTime 注册时间范围，
 * params.beginTrialTime / endTrialTime 体验到期范围
 * 
 * @author yscandice
 */
public class OptionMemberVo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 若依用户ID */
    @Excel(name = "用户ID", cellType = ColumnType.NUMERIC)
    private Long userId;

    /** 邮箱（登录账号） */
    @Excel(name = "邮箱")
    private String email;

    /** 昵称 */
    @Excel(name = "昵称")
    private String nickName;

    /** 头像 */
    private String avatar;

    /** 身份（按 3.1 规则实时计算） */
    @Excel(name = "身份", readConverterExp = "STAFF=内部人员,DISABLED=停用,PAID=付费,TRIAL=体验期,DELAYED=盘后版")
    private String level;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 注册时间（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "注册时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date registerTime;

    /** 体验到期（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "体验到期", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date trialEndTime;

    /** 体验期剩余秒数 */
    @Excel(name = "体验剩余秒数", cellType = ColumnType.NUMERIC)
    private Long trialRemainingSeconds;

    /** 累计延长/缩短小时数 */
    @Excel(name = "累计调整小时", cellType = ColumnType.NUMERIC)
    private Integer trialExtendHours;

    /** 付费到期（东八区） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "付费到期", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date paidUntil;

    /** 最近访问时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最近访问", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastAccessTime;

    /** 注册IP */
    @Excel(name = "注册IP")
    private String registerIp;

    /** 是否在线 */
    @Excel(name = "在线", readConverterExp = "true=在线,false=离线")
    private Boolean online;

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

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getLevel()
    {
        return level;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setRegisterTime(Date registerTime)
    {
        this.registerTime = registerTime;
    }

    public Date getRegisterTime()
    {
        return registerTime;
    }

    public void setTrialEndTime(Date trialEndTime)
    {
        this.trialEndTime = trialEndTime;
    }

    public Date getTrialEndTime()
    {
        return trialEndTime;
    }

    public void setTrialRemainingSeconds(Long trialRemainingSeconds)
    {
        this.trialRemainingSeconds = trialRemainingSeconds;
    }

    public Long getTrialRemainingSeconds()
    {
        return trialRemainingSeconds;
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

    public void setLastAccessTime(Date lastAccessTime)
    {
        this.lastAccessTime = lastAccessTime;
    }

    public Date getLastAccessTime()
    {
        return lastAccessTime;
    }

    public void setRegisterIp(String registerIp)
    {
        this.registerIp = registerIp;
    }

    public String getRegisterIp()
    {
        return registerIp;
    }

    public void setOnline(Boolean online)
    {
        this.online = online;
    }

    public Boolean getOnline()
    {
        return online;
    }
}
