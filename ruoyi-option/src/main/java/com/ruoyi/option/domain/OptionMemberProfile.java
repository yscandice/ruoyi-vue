package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 会员资料扩展 option_member_profile
 * 
 * @author yscandice
 */
public class OptionMemberProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 若依用户ID */
    private Long userId;

    /** 关注标的 ["AAPL","TSLA"] */
    private String watchSymbols;

    /** 策略偏好 {"seller":true,"midterm":true,"buyer":false} */
    private String strategyPreferences;

    /** 强提醒 1开 0关 */
    private String strongAlerts;

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

    public void setWatchSymbols(String watchSymbols)
    {
        this.watchSymbols = watchSymbols;
    }

    public String getWatchSymbols()
    {
        return watchSymbols;
    }

    public void setStrategyPreferences(String strategyPreferences)
    {
        this.strategyPreferences = strategyPreferences;
    }

    public String getStrategyPreferences()
    {
        return strategyPreferences;
    }

    public void setStrongAlerts(String strongAlerts)
    {
        this.strongAlerts = strongAlerts;
    }

    public String getStrongAlerts()
    {
        return strongAlerts;
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
            .append("watchSymbols", getWatchSymbols())
            .append("strategyPreferences", getStrategyPreferences())
            .append("strongAlerts", getStrongAlerts())
            .append("version", getVersion())
            .append("createTime", getCreateTime())
            .append("createTimeUtc", getCreateTimeUtc())
            .append("updateTime", getUpdateTime())
            .append("updateTimeUtc", getUpdateTimeUtc())
            .toString();
    }
}
