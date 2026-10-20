package com.ruoyi.web.controller.option;

import java.util.Date;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.IOptionResourceService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.utils.OptionJson;

/**
 * 期权网站兼容路径（与 OSM 相同的 /api/member/*）
 * 
 * 全部读接口改为读取定时同步后的 Redis 缓存（未命中时从表回填），不再实时请求 OSM。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/api/member")
public class OptionTodayController extends BaseController
{
    @Autowired
    private IOptionSessionService sessionService;

    @Autowired
    private IOptionFeedCacheService feedCacheService;

    @Autowired
    private IOptionFeedSyncService feedSyncService;

    @Autowired
    private IOptionResourceService resourceService;

    /**
     * 登录 OSM 同步账号（Cookie 存入 Redis，供定时同步使用）
     * 
     * 这是同步账号的上游登录，只有后台管理员能调用，会员调用返回 403（会员登录走若依 /login）
     */
    @PreAuthorize("@ss.hasRole('admin')")
    @Log(title = "OSM会话", businessType = BusinessType.GRANT)
    @RateLimiter(time = 60, count = 5, limitType = LimitType.IP)
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> login(@RequestBody LoginRequest body)
    {
        if (body == null || StringUtils.isEmpty(body.getEmail()) || StringUtils.isEmpty(body.getPassword()))
        {
            throw new ServiceException("email 和 password 必填");
        }
        sessionService.login(body.getEmail(), body.getPassword());
        return Map.of("authenticated", true);
    }

    @GetMapping("/feed")
    public void feed(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        OptionFeedWriter.write(feedCacheService.getView(OptionFeedConstants.GROUP_FULL), request, response);
    }

    @GetMapping("/seller-guide")
    public void sellerGuide(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        read("seller-guide", null, request, response);
    }

    @GetMapping("/entitlement")
    public void entitlement(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        read("entitlement", null, request, response);
    }

    @GetMapping("/decisions")
    public void decisions(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        read("decisions", null, request, response);
    }

    @GetMapping("/management-choices")
    public void managementChoices(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        read("management-choices", null, request, response);
    }

    @GetMapping("/premarket")
    public void premarket(@RequestParam("d") String d, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        read("premarket", d, request, response);
    }

    @GetMapping("/updates")
    public void updates(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        read("updates", null, request, response);
    }

    @GetMapping("/support")
    public void support(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        read("support", null, request, response);
    }

    /**
     * 管理员手动导入一份接口数据（feed 走拆分多表，其余走版本表），并写入 Redis
     */
    @PreAuthorize("@ss.hasRole('admin')")
    @Log(title = "期权接口导入", businessType = BusinessType.IMPORT)
    @PutMapping(value = "/today/snapshot", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void saveSnapshot(@RequestBody String raw, HttpServletResponse response) throws Exception
    {
        // 用 OptionJson（BigDecimal 精确）解析原始请求体，避免 MVC 默认转换丢失数值精度
        SnapshotRequest body;
        try
        {
            body = OptionJson.MAPPER.readValue(raw, SnapshotRequest.class);
        }
        catch (Exception e)
        {
            throw new ServiceException("请求体不是有效的 JSON");
        }
        if (body == null || StringUtils.isEmpty(body.getResourceKey()) || body.getPayload() == null
                || body.getPayload().isNull())
        {
            throw new ServiceException("resourceKey 或 payload 无效");
        }
        String payload = OptionJson.write(body.getPayload());
        if ("feed".equals(body.getResourceKey()))
        {
            feedSyncService.importFeed(payload);
        }
        else
        {
            resourceService.importPayload(body.getResourceKey(), body.getBusinessDate(), payload);
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(payload);
    }

    private void read(String resource, String date, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        OptionResourceController.write(resourceService.getView(resource, date), request, response);
    }

    /**
     * 登录参数
     */
    public static class LoginRequest
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

    /**
     * 导入参数（userId、etag、generatedAt 为兼容旧格式保留，不再使用）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SnapshotRequest
    {
        private String resourceKey;

        private Long userId;

        private String businessDate;

        private JsonNode payload;

        private String etag;

        private Date generatedAt;

        public String getResourceKey()
        {
            return resourceKey;
        }

        public void setResourceKey(String resourceKey)
        {
            this.resourceKey = resourceKey;
        }

        public Long getUserId()
        {
            return userId;
        }

        public void setUserId(Long userId)
        {
            this.userId = userId;
        }

        public String getBusinessDate()
        {
            return businessDate;
        }

        public void setBusinessDate(String businessDate)
        {
            this.businessDate = businessDate;
        }

        public JsonNode getPayload()
        {
            return payload;
        }

        public void setPayload(JsonNode payload)
        {
            this.payload = payload;
        }

        public String getEtag()
        {
            return etag;
        }

        public void setEtag(String etag)
        {
            this.etag = etag;
        }

        public Date getGeneratedAt()
        {
            return generatedAt;
        }

        public void setGeneratedAt(Date generatedAt)
        {
            this.generatedAt = generatedAt;
        }
    }
}
