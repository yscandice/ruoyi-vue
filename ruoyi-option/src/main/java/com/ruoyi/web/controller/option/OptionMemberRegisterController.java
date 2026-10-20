package com.ruoyi.web.controller.option;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.option.domain.model.OptionRegisterBody;
import com.ruoyi.option.domain.model.OptionRegisterCodeBody;
import com.ruoyi.option.service.IOptionMemberRegisterService;

/**
 * 网站会员注册（邮箱验证码）
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/api/member/register")
public class OptionMemberRegisterController extends BaseController
{
    @Autowired
    private IOptionMemberRegisterService registerService;

    /**
     * 发送注册邮箱验证码
     */
    @Anonymous
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/code")
    public AjaxResult sendCode(@RequestBody OptionRegisterCodeBody body)
    {
        return AjaxResult.success("验证码已发送，请查收邮件", registerService.sendCode(body));
    }

    /**
     * 邮箱注册，成功后直接登录
     */
    @Anonymous
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping
    public AjaxResult register(@RequestBody OptionRegisterBody body)
    {
        Map<String, Object> data = registerService.register(body);
        AjaxResult ajax = AjaxResult.success("注册成功", data);
        ajax.put(Constants.TOKEN, data.get(Constants.TOKEN));
        return ajax;
    }
}
