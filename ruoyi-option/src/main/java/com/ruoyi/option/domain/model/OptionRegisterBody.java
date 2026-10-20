package com.ruoyi.option.domain.model;

/**
 * 会员注册 请求
 * 
 * @author yscandice
 */
public class OptionRegisterBody
{
    /** 邮箱 */
    private String email;

    /** 密码 */
    private String password;

    /** 邮箱验证码 */
    private String emailCode;

    /** 设备指纹哈希（可选） */
    private String deviceHash;

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getEmailCode()
    {
        return emailCode;
    }

    public void setEmailCode(String emailCode)
    {
        this.emailCode = emailCode;
    }

    public String getDeviceHash()
    {
        return deviceHash;
    }

    public void setDeviceHash(String deviceHash)
    {
        this.deviceHash = deviceHash;
    }
}
