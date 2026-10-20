package com.ruoyi.option.domain;

import java.io.Serializable;
import java.util.Date;
import com.ruoyi.option.enums.OptionAccessLevel;

/**
 * 会员身份计算依据（缓存在 option:member:access:{userId}）
 * 
 * 缓存的是判断依据而不是最终结论：读取时按当前时间重新比较 paidUntil / trialEndTime，
 * 体验期一到，即使缓存还没过期，下一次请求也会切到盘后版（技术文档 3.1）。
 * 
 * @author yscandice
 */
public class OptionAccessIdentity implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 若依用户ID */
    private Long userId;

    /** 是否内部人员（角色含 admin / option_staff） */
    private boolean staff;

    /** 是否停用（档案停用、sys_user 停用 / 删除 / 不存在） */
    private boolean disabled;

    /** 是否有会员档案 */
    private boolean member;

    /** 注册时间 */
    private Date registerTime;

    /** 体验期结束时间 */
    private Date trialEndTime;

    /** 付费到期时间 */
    private Date paidUntil;

    /** 计算时间 */
    private Date resolvedAt;

    /**
     * 按 3.1 优先级判断身份：STAFF > DISABLED > PAID > TRIAL > DELAYED
     * 
     * @param now 当前时间
     * @return 身份
     */
    public OptionAccessLevel levelAt(Date now)
    {
        if (staff)
        {
            return OptionAccessLevel.STAFF;
        }
        if (disabled)
        {
            return OptionAccessLevel.DISABLED;
        }
        if (paidUntil != null && paidUntil.after(now))
        {
            return OptionAccessLevel.PAID;
        }
        if (trialEndTime != null && now.before(trialEndTime))
        {
            return OptionAccessLevel.TRIAL;
        }
        return OptionAccessLevel.DELAYED;
    }

    /**
     * 体验期剩余秒数（已到期为 0）
     */
    public long trialRemainingSeconds(Date now)
    {
        return trialEndTime == null ? 0 : Math.max(0, (trialEndTime.getTime() - now.getTime()) / 1000);
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public boolean isStaff()
    {
        return staff;
    }

    public void setStaff(boolean staff)
    {
        this.staff = staff;
    }

    public boolean isDisabled()
    {
        return disabled;
    }

    public void setDisabled(boolean disabled)
    {
        this.disabled = disabled;
    }

    public boolean isMember()
    {
        return member;
    }

    public void setMember(boolean member)
    {
        this.member = member;
    }

    public Date getRegisterTime()
    {
        return registerTime;
    }

    public void setRegisterTime(Date registerTime)
    {
        this.registerTime = registerTime;
    }

    public Date getTrialEndTime()
    {
        return trialEndTime;
    }

    public void setTrialEndTime(Date trialEndTime)
    {
        this.trialEndTime = trialEndTime;
    }

    public Date getPaidUntil()
    {
        return paidUntil;
    }

    public void setPaidUntil(Date paidUntil)
    {
        this.paidUntil = paidUntil;
    }

    public Date getResolvedAt()
    {
        return resolvedAt;
    }

    public void setResolvedAt(Date resolvedAt)
    {
        this.resolvedAt = resolvedAt;
    }
}
