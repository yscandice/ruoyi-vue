package com.ruoyi.option.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.html.EscapeUtil;
import com.ruoyi.option.service.IOptionMailService;

/**
 * 会员邮件 服务层实现：JavaMailSender 发 HTML 邮件，发件人 = MAIL_USERNAME + option.mail.from-name
 * 
 * @author yscandice
 */
@Service
public class OptionMailServiceImpl implements IOptionMailService
{
    private static final Logger log = LoggerFactory.getLogger(OptionMailServiceImpl.class);

    /** 接口等待发送结果的最长秒数 */
    private static final int WAIT_SECONDS = 8;

    @Autowired
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${option.mail.from-name:期权信号}")
    private String fromName;

    @Override
    public boolean sendRegisterCode(String email, String code, int expireMinutes)
    {
        String subject = "【" + fromName + "】注册验证码 " + code;
        String html = "<div style=\"font-family:Arial,'PingFang SC','Microsoft YaHei',sans-serif;max-width:520px;"
                + "margin:0 auto;padding:24px;border:1px solid #eee;border-radius:8px;color:#333\">"
                + "<h2 style=\"margin:0 0 16px\">" + EscapeUtil.escape(fromName) + "</h2>"
                + "<p>您正在注册会员账号，验证码为：</p>"
                + "<p style=\"font-size:32px;font-weight:bold;letter-spacing:6px;margin:16px 0;color:#1677ff\">" + code + "</p>"
                + "<p>验证码 " + expireMinutes + " 分钟内有效，请勿泄露给他人。</p>"
                + "<p style=\"color:#999;font-size:12px;margin-top:24px\">如果不是您本人操作，请忽略此邮件。</p></div>";
        Future<?> future = executor.submit(() -> sendHtml(email, subject, html));
        try
        {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            return true;
        }
        catch (TimeoutException e)
        {
            log.warn("注册验证码邮件 {} 秒内未发送完成，继续在后台发送：{}", WAIT_SECONDS, mask(email));
            return true;
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("注册验证码邮件发送失败 {}: {}", mask(email), cause.getMessage());
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void sendHtml(String to, String subject, String html)
    {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || StringUtils.isEmpty(username))
        {
            throw new ServiceException("邮件服务未配置（MAIL_USERNAME / MAIL_PASSWORD）");
        }
        try
        {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(username, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("邮件已发送 {} {}", mask(to), subject.replaceAll("\\d{6}", "******"));
        }
        catch (Exception e)
        {
            // 只记异常信息，不打印账号授权码
            throw new ServiceException("邮件发送失败：" + e.getMessage());
        }
    }

    /**
     * 日志里的邮箱脱敏：ab***@qq.com
     */
    static String mask(String email)
    {
        if (email == null || email.indexOf('@') < 1)
        {
            return email;
        }
        int at = email.indexOf('@');
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
