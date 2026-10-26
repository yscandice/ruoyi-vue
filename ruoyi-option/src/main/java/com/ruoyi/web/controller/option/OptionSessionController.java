package com.ruoyi.web.controller.option;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.service.IOptionSessionService;

/**
 * OSM 会话管理（全局单个会话）
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/option/session")
public class OptionSessionController extends BaseController
{
    @Autowired
    private IOptionSessionService sessionService;

    /**
     * 登录 OSM，Cookie 存入 Redis
     */
    @PreAuthorize("@ss.hasPermi('option:session:login')")
    @Log(title = "OSM会话", businessType = BusinessType.GRANT)
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody body)
    {
        if (body == null || StringUtils.isEmpty(body.getEmail()) || StringUtils.isEmpty(body.getPassword()))
        {
            return error("email 和 password 必填");
        }
        int ttl = sessionService.login(body.getEmail(), body.getPassword());
        return success().put("expireSeconds", ttl);
    }

    /**
     * 退出 OSM 会话
     */
    @PreAuthorize("@ss.hasPermi('option:session:login')")
    @Log(title = "OSM会话", businessType = BusinessType.FORCE)
    @DeleteMapping("/logout")
    public AjaxResult logout()
    {
        sessionService.logout();
        return success();
    }

    /**
     * 会话与同步状态（仅后台「OSM 会话」菜单权限可查）
     */
    @PreAuthorize("@ss.hasPermi('option:session:status')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(sessionService.getStatus());
    }

    /**
     * 登录参数
     */
    public static class LoginBody
    {
        private String email;

        private String password;

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
    }
}
