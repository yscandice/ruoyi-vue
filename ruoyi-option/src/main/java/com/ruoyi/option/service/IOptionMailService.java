package com.ruoyi.option.service;

/**
 * 会员邮件 服务层（技术文档 9.3.5）
 * 
 * @author yscandice
 */
public interface IOptionMailService
{
    /**
     * 发送注册邮箱验证码（异步线程发送，最多等待 8 秒拿结果；超时视为已提交，失败只记日志）
     * 
     * @param email 收件邮箱
     * @param code 验证码
     * @param expireMinutes 有效分钟数
     * @return 是否发送成功（或已提交仍在发送）
     */
    public boolean sendRegisterCode(String email, String code, int expireMinutes);

    /**
     * 发送 HTML 邮件（同步，供连通性测试）
     * 
     * @param to 收件人
     * @param subject 主题
     * @param html 正文
     */
    public void sendHtml(String to, String subject, String html);
}
