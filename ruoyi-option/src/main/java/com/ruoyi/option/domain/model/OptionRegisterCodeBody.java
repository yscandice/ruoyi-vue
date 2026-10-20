package com.ruoyi.option.domain.model;

/**
 * 发送注册邮箱验证码 请求
 * 
 * @author yscandice
 */
public class OptionRegisterCodeBody
{
    /** 邮箱 */
    private String email;

    /** 图形验证码 */
    private String code;

    /** 图形验证码 uuid */
    private String uuid;

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getUuid()
    {
        return uuid;
    }

    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }
}
