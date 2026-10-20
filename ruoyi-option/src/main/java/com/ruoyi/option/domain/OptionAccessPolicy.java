package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 会员菜单数据权限策略 option_access_policy
 * 
 * @author yscandice
 */
public class OptionAccessPolicy extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 策略ID */
    private Long policyId;

    /** 菜单 today/info/conduct/follow/settlement/record/academy/update/settings */
    private String resourceCode;

    /** 菜单名称 */
    private String resourceName;

    /** 会员接口路径匹配（Ant 风格，多个用英文逗号分隔） */
    private String pathPattern;

    /** 实时视图 Redis 前缀（多个用英文逗号分隔），冻结用 */
    private String redisPrefix;

    /** 体验期/付费 视图 REALTIME/DENY */
    private String realtimeMode;

    /** 盘后版视图 CLOSE_SNAPSHOT/REALTIME/DENY/SELF */
    private String delayedMode;

    /** 匹配顺序 */
    private Integer orderNum;

    /** 状态 0启用 1停用 */
    private String status;

    /** 创建时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeUtc;

    /** 更新时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTimeUtc;

    public void setPolicyId(Long policyId)
    {
        this.policyId = policyId;
    }

    public Long getPolicyId()
    {
        return policyId;
    }

    public void setResourceCode(String resourceCode)
    {
        this.resourceCode = resourceCode;
    }

    public String getResourceCode()
    {
        return resourceCode;
    }

    public void setResourceName(String resourceName)
    {
        this.resourceName = resourceName;
    }

    public String getResourceName()
    {
        return resourceName;
    }

    public void setPathPattern(String pathPattern)
    {
        this.pathPattern = pathPattern;
    }

    public String getPathPattern()
    {
        return pathPattern;
    }

    public void setRedisPrefix(String redisPrefix)
    {
        this.redisPrefix = redisPrefix;
    }

    public String getRedisPrefix()
    {
        return redisPrefix;
    }

    public void setRealtimeMode(String realtimeMode)
    {
        this.realtimeMode = realtimeMode;
    }

    public String getRealtimeMode()
    {
        return realtimeMode;
    }

    public void setDelayedMode(String delayedMode)
    {
        this.delayedMode = delayedMode;
    }

    public String getDelayedMode()
    {
        return delayedMode;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
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
            .append("policyId", getPolicyId())
            .append("resourceCode", getResourceCode())
            .append("resourceName", getResourceName())
            .append("pathPattern", getPathPattern())
            .append("redisPrefix", getRedisPrefix())
            .append("realtimeMode", getRealtimeMode())
            .append("delayedMode", getDelayedMode())
            .append("orderNum", getOrderNum())
            .append("status", getStatus())
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
