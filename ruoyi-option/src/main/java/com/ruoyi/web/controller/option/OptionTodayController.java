package com.ruoyi.web.controller.option;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionTodaySnapshot;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionSessionService;
import com.ruoyi.option.service.IOptionTodayService;
import com.ruoyi.option.service.impl.OptionUpstreamService;

/** 今日菜单：登录 OSM 后携带上游会话 Cookie 请求并缓存接口快照。 */
@RestController
@RequestMapping("/api/member")
public class OptionTodayController extends BaseController
{
    private static final String DEFAULT_DATE = "1970-01-01";
    private static final Set<String> RESOURCES = Set.of("feed", "seller-guide", "entitlement", "decisions",
            "management-choices", "premarket", "updates", "support");
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private IOptionTodayService todayService;
    @Autowired private OptionUpstreamService upstreamService;
    @Autowired private IOptionSessionService sessionService;
    @Autowired private IOptionFeedCacheService feedCacheService;

    @Anonymous
    @RateLimiter(time = 60, count = 5, limitType = LimitType.IP)
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Object login(@RequestBody LoginRequest body)
    {
        if (body == null || body.getEmail() == null || body.getPassword() == null
                || body.getEmail().isBlank() || body.getPassword().isBlank())
            throw new IllegalArgumentException("email 和 password 必填");
        sessionService.login(body.getEmail(), body.getPassword());
        return java.util.Map.of("authenticated", true);
    }

    @GetMapping(value = "/feed", produces = MediaType.APPLICATION_JSON_VALUE)
    public void feed(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        // feed 改为读取定时同步后的 Redis 缓存（未命中时从多表拼接），不再实时请求 OSM
        OptionFeedWriter.write(feedCacheService.get(OptionFeedConstants.GROUP_FULL), request, response);
    }

    @GetMapping(value = "/seller-guide", produces = MediaType.APPLICATION_JSON_VALUE)
    public void sellerGuide(HttpServletRequest request, HttpServletResponse response) throws Exception
    { write("seller-guide", null, request, response, false); }

    @GetMapping(value = "/entitlement", produces = MediaType.APPLICATION_JSON_VALUE)
    public void entitlement(HttpServletRequest request, HttpServletResponse response) throws Exception
    { write("entitlement", null, request, response, false); }

    @GetMapping(value = "/decisions", produces = MediaType.APPLICATION_JSON_VALUE)
    public void decisions(HttpServletRequest request, HttpServletResponse response) throws Exception
    { write("decisions", null, request, response, false); }

    @GetMapping(value = "/management-choices", produces = MediaType.APPLICATION_JSON_VALUE)
    public void managementChoices(HttpServletRequest request, HttpServletResponse response) throws Exception
    { write("management-choices", null, request, response, false); }

    @GetMapping(value = "/premarket", produces = MediaType.APPLICATION_JSON_VALUE)
    public void premarket(@RequestParam("d") String date, HttpServletRequest request,
            HttpServletResponse response) throws Exception
    {
        if (!isDate(date)) { response.sendError(HttpServletResponse.SC_BAD_REQUEST, "d must be yyyy-MM-dd"); return; }
        write("premarket", date, request, response, false);
    }

    @GetMapping(value = "/updates", produces = MediaType.APPLICATION_JSON_VALUE)
    public void updates(HttpServletRequest request, HttpServletResponse response) throws Exception
    { write("updates", null, request, response, false); }

    @GetMapping(value = "/support", produces = MediaType.APPLICATION_JSON_VALUE)
    public void support(HttpServletRequest request, HttpServletResponse response) throws Exception
    { write("support", null, request, response, false); }

    /** 管理员专用的本地快照导入入口。 */
    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping(value = "/today/snapshot", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode saveSnapshot(@RequestBody SnapshotRequest body) throws Exception
    {
        if (body == null || !RESOURCES.contains(body.getResourceKey()) || body.getPayload() == null)
            throw new IllegalArgumentException("resourceKey 或 payload 无效");
        String date = "premarket".equals(body.getResourceKey()) ? body.getBusinessDate() : DEFAULT_DATE;
        if (date == null) date = DEFAULT_DATE;
        if (!isDate(date)) throw new IllegalArgumentException("businessDate 必须为 yyyy-MM-dd");
        OptionTodaySnapshot snapshot = new OptionTodaySnapshot();
        snapshot.setResourceKey(body.getResourceKey());
        snapshot.setUserId(body.getUserId() == null ? SecurityUtils.getUserId() : body.getUserId());
        snapshot.setBusinessDate(parseDate(date));
        snapshot.setPayload(objectMapper.writeValueAsString(body.getPayload()));
        snapshot.setSourceEtag(body.getEtag());
        snapshot.setGeneratedAt(body.getGeneratedAt() == null ? new Date() : body.getGeneratedAt());
        snapshot.setCreateBy(getUsername());
        snapshot.setUpdateBy(getUsername());
        snapshot.setRemark("今日接口快照");
        todayService.saveSnapshot(snapshot);
        return body.getPayload();
    }

    private void write(String resource, String date, HttpServletRequest request, HttpServletResponse response,
            boolean supportsEtag) throws Exception
    {
        OptionUpstreamService.UpstreamResponse result = upstreamService.fetch(resource, date, SecurityUtils.getUserId());
        if (supportsEtag && result.getEtag() != null)
        {
            response.setHeader("ETag", result.getEtag());
            if (result.getEtag().equals(request.getHeader("If-None-Match")))
            { response.setStatus(HttpServletResponse.SC_NOT_MODIFIED); return; }
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.getBody());
    }

    private boolean isDate(String value)
    {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try { format.parse(value); return true; } catch (ParseException e) { return false; }
    }

    private Date parseDate(String value) throws ParseException
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(value);
    }

    public static class LoginRequest
    {
        private String email;
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String value) { email = value; }
        public String getPassword() { return password; }
        public void setPassword(String value) { password = value; }
    }

    public static class SnapshotRequest
    {
        private String resourceKey;
        private Long userId;
        private String businessDate;
        private JsonNode payload;
        private String etag;
        private Date generatedAt;
        public String getResourceKey() { return resourceKey; }
        public void setResourceKey(String value) { resourceKey = value; }
        public Long getUserId() { return userId; }
        public void setUserId(Long value) { userId = value; }
        public String getBusinessDate() { return businessDate; }
        public void setBusinessDate(String value) { businessDate = value; }
        public JsonNode getPayload() { return payload; }
        public void setPayload(JsonNode value) { payload = value; }
        public String getEtag() { return etag; }
        public void setEtag(String value) { etag = value; }
        public Date getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Date value) { generatedAt = value; }
    }
}
