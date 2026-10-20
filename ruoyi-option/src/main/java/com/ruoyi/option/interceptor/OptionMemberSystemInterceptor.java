package com.ruoyi.option.interceptor;

import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionAccessConstants;

/**
 * 会员访问若依系统接口的白名单（技术文档 7.3.5）
 * 
 * 会员角色 option_member 只放行改密码、上传头像（退出登录 /logout 不在 /system 下）；
 * 其余 /system/** 一律 403，避免会员通过个人中心修改邮箱、手机号等账号信息。
 * 
 * @author yscandice
 */
@Component
public class OptionMemberSystemInterceptor implements HandlerInterceptor
{
    /** 方法 + 路径 */
    private static final Set<String> MEMBER_ALLOWED = Set.of(
            "PUT /system/user/profile/updatePwd",
            "POST /system/user/profile/avatar");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)
                || !isMember(loginUser.getUser()))
        {
            return true;
        }
        String path = StringUtils.removeStart(request.getRequestURI(), request.getContextPath());
        if (MEMBER_ALLOWED.contains(request.getMethod() + " " + path))
        {
            return true;
        }
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(HttpStatus.FORBIDDEN, "会员账号无权访问该接口")));
        return false;
    }

    /**
     * 只有会员角色（非管理员）才受限
     */
    private static boolean isMember(SysUser user)
    {
        if (user == null || user.isAdmin() || user.getRoles() == null)
        {
            return false;
        }
        return user.getRoles().stream().anyMatch(role -> OptionAccessConstants.ROLE_MEMBER.equals(role.getRoleKey()));
    }
}
