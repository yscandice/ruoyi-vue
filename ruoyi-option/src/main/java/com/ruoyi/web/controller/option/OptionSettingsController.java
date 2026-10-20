package com.ruoyi.web.controller.option;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.option.service.IOptionMemberSettingsService;

/**
 * 会员「设置」相关接口（/api/member/*）
 * 
 * 资料、通知偏好、阅读进度都是本人数据（技术文档 4.2、7.3.1–7.3.4）：没有记录时返回默认值，第一次保存才建记录，
 * 保存时带上次读到的 updatedAt，不一致返回 409。改密码、换头像、退出登录复用若依接口（7.3.5）。
 * 
 * 涉及同步账号个人数据、或暂不实现的功能统一固定返回（技术文档 4.1、7.3.6）：
 * 推荐码、浏览器推送、API 密钥、投顾联系方式都不再返回同步账号的数据。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/api/member")
public class OptionSettingsController extends BaseController
{
    @Autowired
    private IOptionMemberSettingsService settingsService;

    @Autowired
    private TokenService tokenService;

    /**
     * 设置页聚合（一次加载）：资料、通知偏好、身份、阅读进度、功能开关
     */
    @GetMapping("/settings")
    public AjaxResult settings()
    {
        return success(settingsService.selectSettings(getUserId()));
    }

    /**
     * 资料
     */
    @GetMapping("/profile")
    public AjaxResult profile()
    {
        return success(Map.of("profile", settingsService.selectProfile(getUserId())));
    }

    /**
     * 修改资料（只传要改的字段，带上次读到的 updatedAt）
     */
    @PutMapping("/profile")
    public AjaxResult updateProfile(@RequestBody(required = false) Map<String, Object> body)
    {
        Map<String, Object> profile = settingsService.updateProfile(getUserId(), body);
        // 昵称改动后同步登录缓存，与若依个人中心一致
        LoginUser loginUser = getLoginUser();
        Object displayName = profile.get("displayName");
        if (displayName != null && !displayName.equals(loginUser.getUser().getNickName()))
        {
            loginUser.getUser().setNickName(String.valueOf(displayName));
            tokenService.setLoginUser(loginUser);
        }
        return success(Map.of("profile", profile));
    }

    /**
     * 通知偏好
     */
    @GetMapping("/notifications")
    public AjaxResult notifications()
    {
        return success(settingsService.selectNotifications(getUserId()));
    }

    /**
     * 修改通知偏好 { prefs: { 只传要改的开关 }, updatedAt }
     */
    @PutMapping("/notifications")
    public AjaxResult updateNotifications(@RequestBody(required = false) Map<String, Object> body)
    {
        return success(settingsService.updateNotifications(getUserId(), body));
    }

    /**
     * 通知偏好恢复默认值
     */
    @PostMapping("/notifications/reset")
    public AjaxResult resetNotifications()
    {
        return success(settingsService.resetNotifications(getUserId()));
    }

    /**
     * 阅读进度（按用户记录；currentSeq 来自同步数据）
     */
    @GetMapping("/updates")
    public AjaxResult updates()
    {
        return success(settingsService.selectUpdateProgress(getUserId()));
    }

    /**
     * 标记已读 { seenSeq }（不能超过 currentSeq，只增不减）
     */
    @PutMapping("/updates/seen")
    public AjaxResult updatesSeen(@RequestBody(required = false) Map<String, Object> body)
    {
        return success(settingsService.markUpdatesSeen(getUserId(), body));
    }
    /**
     * 推荐信息：原来返回同步账号的推荐码（个人数据泄露风险），改为未开放
     */
    @GetMapping("/referrals")
    public AjaxResult referrals()
    {
        return success(disabled());
    }

    /**
     * 浏览器推送：需要自建推送密钥与订阅表，暂不实现
     */
    @GetMapping("/push")
    public AjaxResult push()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", false);
        data.put("eligible", false);
        data.put("subscribed", false);
        data.put("devices", Collections.emptyList());
        return success(data);
    }

    /**
     * API 密钥：不实现（OSM 上也是 403）
     */
    @GetMapping("/api-keys")
    public AjaxResult apiKeys()
    {
        return success(disabled());
    }

    /**
     * 投顾联系方式：不实现（OSM 上也是 403）
     */
    @GetMapping("/advisory-contact")
    public AjaxResult advisoryContact()
    {
        return success(disabled());
    }

    private static Map<String, Object> disabled()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", false);
        return data;
    }
}
