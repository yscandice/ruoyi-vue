package com.ruoyi.option.service;

import java.util.Map;
import com.ruoyi.option.domain.model.OptionRegisterBody;
import com.ruoyi.option.domain.model.OptionRegisterCodeBody;

/**
 * 会员注册 服务层（技术文档 6、7.1.1、7.1.2、9.3.1）
 * 
 * @author yscandice
 */
public interface IOptionMemberRegisterService
{
    /**
     * 发送注册邮箱验证码：校验图形码 → 注册开关 / 域名黑名单 → 已注册 → 限频 → 发送邮件
     * 
     * @param body 邮箱、图形验证码
     * @return resendAfter / expiresIn
     */
    public Map<String, Object> sendCode(OptionRegisterCodeBody body);

    /**
     * 注册：校验邮箱验证码、邮箱唯一、密码强度、IP 限频 → 一个事务建 sys_user / sys_user_role / option_member_access → 签发 token
     * 
     * @param body 邮箱、密码、邮箱验证码、设备指纹
     * @return token 与 access
     */
    public Map<String, Object> register(OptionRegisterBody body);
}
