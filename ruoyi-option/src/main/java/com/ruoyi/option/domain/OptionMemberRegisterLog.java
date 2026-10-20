package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 会员注册与风控日志 option_member_register_log
 * 
 * @author yscandice
 */
public class OptionMemberRegisterLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long logId;

    /** 邮箱（小写） */
    private String email;

    /** SEND_CODE/REGISTER */
    private String action;

    /** SUCCESS/REJECTED/FAILED */
    private String result;

    /** 拒绝原因（限频/黑名单域名/验证码错误等） */
    private String reason;

    /** IP */
    private String ip;

    /** 设备指纹哈希 */
    private String deviceHash;

    /** 创建时间（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeUtc;

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public Long getLogId()
    {
        return logId;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getEmail()
    {
        return email;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getAction()
    {
        return action;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getResult()
    {
        return result;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getReason()
    {
        return reason;
    }

    public void setIp(String ip)
    {
        this.ip = ip;
    }

    public String getIp()
    {
        return ip;
    }

    public void setDeviceHash(String deviceHash)
    {
        this.deviceHash = deviceHash;
    }

    public String getDeviceHash()
    {
        return deviceHash;
    }

    public void setCreateTimeUtc(Date createTimeUtc)
    {
        this.createTimeUtc = createTimeUtc;
    }

    public Date getCreateTimeUtc()
    {
        return createTimeUtc;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("email", getEmail())
            .append("action", getAction())
            .append("result", getResult())
            .append("reason", getReason())
            .append("ip", getIp())
            .append("deviceHash", getDeviceHash())
            .append("createTime", getCreateTime())
            .append("createTimeUtc", getCreateTimeUtc())
            .toString();
    }
}
