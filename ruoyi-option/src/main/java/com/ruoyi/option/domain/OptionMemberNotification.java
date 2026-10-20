package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 会员通知偏好 option_member_notification
 * 
 * @author yscandice
 */
public class OptionMemberNotification extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 若依用户ID */
    private Long userId;

    /** 新信号 */
    private String newSignal;

    /** 保护窗口 */
    private String protectionWindow;

    /** 止盈窗口 */
    private String takeProfitWindow;

    /** 结算 */
    private String settlement;

    /** 每周战绩 */
    private String weeklyScorecard;

    /** 产品更新 */
    private String productUpdate;

    /** 静默提醒 */
    private String silentReminder;

    /** 动态管理止损 */
    private String managementStop;

    /** 只提醒已采纳的信号 */
    private String manageAdoptedOnly;

    /** 乐观锁版本 */
    private Integer version;

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

    public void setNewSignal(String newSignal)
    {
        this.newSignal = newSignal;
    }

    public String getNewSignal()
    {
        return newSignal;
    }

    public void setProtectionWindow(String protectionWindow)
    {
        this.protectionWindow = protectionWindow;
    }

    public String getProtectionWindow()
    {
        return protectionWindow;
    }

    public void setTakeProfitWindow(String takeProfitWindow)
    {
        this.takeProfitWindow = takeProfitWindow;
    }

    public String getTakeProfitWindow()
    {
        return takeProfitWindow;
    }

    public void setSettlement(String settlement)
    {
        this.settlement = settlement;
    }

    public String getSettlement()
    {
        return settlement;
    }

    public void setWeeklyScorecard(String weeklyScorecard)
    {
        this.weeklyScorecard = weeklyScorecard;
    }

    public String getWeeklyScorecard()
    {
        return weeklyScorecard;
    }

    public void setProductUpdate(String productUpdate)
    {
        this.productUpdate = productUpdate;
    }

    public String getProductUpdate()
    {
        return productUpdate;
    }

    public void setSilentReminder(String silentReminder)
    {
        this.silentReminder = silentReminder;
    }

    public String getSilentReminder()
    {
        return silentReminder;
    }

    public void setManagementStop(String managementStop)
    {
        this.managementStop = managementStop;
    }

    public String getManagementStop()
    {
        return managementStop;
    }

    public void setManageAdoptedOnly(String manageAdoptedOnly)
    {
        this.manageAdoptedOnly = manageAdoptedOnly;
    }

    public String getManageAdoptedOnly()
    {
        return manageAdoptedOnly;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getVersion()
    {
        return version;
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
            .append("newSignal", getNewSignal())
            .append("protectionWindow", getProtectionWindow())
            .append("takeProfitWindow", getTakeProfitWindow())
            .append("settlement", getSettlement())
            .append("weeklyScorecard", getWeeklyScorecard())
            .append("productUpdate", getProductUpdate())
            .append("silentReminder", getSilentReminder())
            .append("managementStop", getManagementStop())
            .append("manageAdoptedOnly", getManageAdoptedOnly())
            .append("version", getVersion())
            .append("createTime", getCreateTime())
            .append("createTimeUtc", getCreateTimeUtc())
            .append("updateTime", getUpdateTime())
            .append("updateTimeUtc", getUpdateTimeUtc())
            .toString();
    }
}
