package com.ruoyi.web.controller.option;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 会员「设置」相关接口（/api/member/*）
 * 
 * 本期先下线涉及同步账号个人数据、或暂不实现的功能，统一固定返回（技术文档 4.1、7.3.6）：
 * 推荐码、浏览器推送、API 密钥、投顾联系方式都不再返回同步账号的数据。
 * 资料、通知偏好、阅读进度等接口在步骤 9 补充。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/api/member")
public class OptionSettingsController extends BaseController
{
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
