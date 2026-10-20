package com.ruoyi.option.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.enums.OptionDataView;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionMemberAdminService;
import com.ruoyi.option.utils.OptionAccessContext;

/**
 * 会员数据权限拦截器（技术文档 3.5）：/api/member/** 计算身份与菜单视图，写入 OptionAccessContext
 * 
 * <pre>
 * 总开关 option.access.enabled 关闭 → 直接放行，不写上下文、不加响应头（行为与接入前一致）
 * DISABLED / 菜单策略 DENY        → 返回 403
 * 其余                             → 写上下文，加响应头 X-Option-Data-View（盘后加 X-Option-Data-Trade-Date）
 * </pre>
 * 
 * @author yscandice
 */
@Component
public class OptionAccessInterceptor implements HandlerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(OptionAccessInterceptor.class);

    /** 响应头：数据视图 */
    public static final String HEADER_VIEW = "X-Option-Data-View";

    /** 响应头：盘后数据交易日 */
    public static final String HEADER_TRADE_DATE = "X-Option-Data-Trade-Date";

    /** 响应头：盘后数据可能过期 */
    public static final String HEADER_STALE = "X-Option-Data-Stale";

    @Autowired
    private IOptionAccessService accessService;

    @Autowired
    private IOptionMemberAdminService memberAdminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        OptionAccessContext.clear();
        // 会员最近访问时间（与总开关无关；每个用户最多 5 分钟写一次库）
        touchLastAccess();
        if (!accessService.isEnabled())
        {
            return true;
        }
        Long userId = currentUserId();
        if (userId == null)
        {
            // 未登录由 Spring Security 处理，这里不介入
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        OptionAccessContext context;
        try
        {
            context = accessService.buildContext(userId, path);
        }
        catch (Exception e)
        {
            // 身份算不出来时不放出任何会员数据
            log.error("计算会员数据权限失败 userId={} path={}", userId, path, e);
            deny(response, "数据权限校验失败，请稍后重试");
            return false;
        }
        if (context.getLevel() == OptionAccessLevel.DISABLED)
        {
            deny(response, "账号已停用，请联系管理员");
            return false;
        }
        if (context.getView() == OptionDataView.DENY)
        {
            deny(response, "当前身份无权访问该菜单");
            return false;
        }
        OptionAccessContext.set(context);
        response.setHeader(HEADER_VIEW, context.getView().getCode());
        if (context.getView() == OptionDataView.DELAYED)
        {
            if (context.getDataTradeDate() != null)
            {
                response.setHeader(HEADER_TRADE_DATE, context.getDataTradeDate());
            }
            if (context.isStale())
            {
                response.setHeader(HEADER_STALE, "true");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        OptionAccessContext.clear();
    }

    private static Long currentUserId()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser)
        {
            return loginUser.getUserId();
        }
        return null;
    }

    private static void deny(HttpServletResponse response, String message)
    {
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(HttpStatus.FORBIDDEN, message)));
    }

    /**
     * 只记录会员角色（option_member）的访问
     */
    private void touchLastAccess()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser && loginUser.getUser() != null
                && loginUser.getUser().getRoles() != null && loginUser.getUser().getRoles().stream()
                        .anyMatch(role -> OptionAccessConstants.ROLE_MEMBER.equals(role.getRoleKey())))
        {
            memberAdminService.touchLastAccess(loginUser.getUserId());
        }
    }
}
