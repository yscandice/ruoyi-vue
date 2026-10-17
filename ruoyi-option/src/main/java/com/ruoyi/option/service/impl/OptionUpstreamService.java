package com.ruoyi.option.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.option.domain.OptionTodaySnapshot;
import com.ruoyi.option.service.IOptionTodayService;

/** 负责 OSM 登录、Redis 会话缓存以及带 Cookie 的今日接口代理。 */
@Service
public class OptionUpstreamService
{
    public static final String COOKIE_KEY = "option:login:cookie";
    private static final int DEFAULT_COOKIE_TTL_SECONDS = 6 * 60 * 60;
    private static final int MAX_SNAPSHOT_CHARACTERS = 12_000_000;
    private static final Logger log = LoggerFactory.getLogger(OptionUpstreamService.class);
    private static final Pattern MAX_AGE = Pattern.compile("(?i)(?:^|;)\\s*max-age\\s*=\\s*(\\d+)");
    private static final Map<String, String> PATHS = Map.of(
            "feed", "/api/member/feed",
            "seller-guide", "/api/member/seller-guide",
            "entitlement", "/api/member/entitlement",
            "decisions", "/api/member/decisions",
            "management-choices", "/api/member/management-choices",
            "premarket", "/api/member/premarket",
            "updates", "/api/member/updates",
            "support", "/api/member/support");

    @Value("${OPTION_LOGIN_BASE_URL:https://osmtrade.com}")
    private String baseUrl;
    @Autowired private RedisCache redisCache;
    @Autowired private IOptionTodayService todayService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public void login(String email, String password)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("email", email, "password", password), headers);
        ResponseEntity<String> response;
        try
        {
            response = restTemplate.exchange(baseUrl + "/api/member/login", HttpMethod.POST, request, String.class);
        }
        catch (HttpStatusCodeException e)
        {
            throw new ResponseStatusException(e.getStatusCode(), "OSM 登录失败");
        }
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookie = cookieHeader(setCookies);
        if (!response.getStatusCode().is2xxSuccessful() || cookie.isBlank())
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "上游登录未返回会话 Cookie");
        int ttl = cookieTtl(setCookies);
        redisCache.setCacheObject(COOKIE_KEY, cookie, ttl, TimeUnit.SECONDS);
    }

    public UpstreamResponse fetch(String resource, String date, Long localUserId)
    {
        String path = PATHS.get(resource);
        if (path == null) throw new IllegalArgumentException("不支持的今日接口");
        String cookie = redisCache.getCacheObject(COOKIE_KEY);
        if (cookie == null || cookie.isBlank())
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "请先通过 OSM 登录接口建立上游会话");

        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl).path(path);
        if ("premarket".equals(resource)) uri.queryParam("d", date);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.REFERER, baseUrl + "/member/home");
        ResponseEntity<String> response;
        try
        {
            response = restTemplate.exchange(uri.build().encode().toUri(), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
        }
        catch (HttpStatusCodeException e)
        {
            throw new ResponseStatusException(e.getStatusCode(), "OSM 今日接口请求失败");
        }
        String body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank())
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "OSM 今日接口未返回有效数据");
        try
        {
            if (!objectMapper.readTree(body).isObject() && !objectMapper.readTree(body).isArray())
                throw new IllegalArgumentException("非 JSON 对象响应");
        }
        catch (Exception e)
        {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "OSM 今日接口返回内容不是有效 JSON");
        }

        Date generatedAt = parseGeneratedAt(response.getHeaders().getFirst("X-Feed-Generated-At"));
        OptionTodaySnapshot snapshot = new OptionTodaySnapshot();
        snapshot.setResourceKey(resource);
        snapshot.setUserId(localUserId);
        snapshot.setBusinessDate(parseBusinessDate("premarket".equals(resource) ? date : "1970-01-01"));
        snapshot.setPayload(body);
        snapshot.setSourceEtag(response.getHeaders().getETag());
        snapshot.setGeneratedAt(generatedAt);
        snapshot.setCreateBy("osm-upstream");
        snapshot.setUpdateBy("osm-upstream");
        snapshot.setRemark("OSM 上游接口快照");
        if (body.length() <= MAX_SNAPSHOT_CHARACTERS)
        {
            todayService.saveSnapshot(snapshot);
        }
        else
        {
            log.warn("Skipped snapshot persistence for resource {} because the response is too large ({} characters)", resource, body.length());
        }
        return new UpstreamResponse(body, response.getHeaders().getETag());
    }

    private String cookieHeader(List<String> setCookies)
    {
        if (setCookies == null || setCookies.isEmpty()) return "";
        List<String> pairs = new ArrayList<>();
        for (String value : setCookies)
        {
            int end = value.indexOf(';');
            String pair = (end < 0 ? value : value.substring(0, end)).trim();
            if (!pair.isEmpty()) pairs.add(pair);
        }
        return String.join("; ", pairs);
    }

    private int cookieTtl(List<String> setCookies)
    {
        int ttl = DEFAULT_COOKIE_TTL_SECONDS;
        if (setCookies == null) return ttl;
        for (String value : setCookies)
        {
            Matcher matcher = MAX_AGE.matcher(value);
            if (matcher.find())
            {
                try { ttl = Math.min(ttl, Integer.parseInt(matcher.group(1))); }
                catch (NumberFormatException ignored) { }
            }
        }
        return Math.max(1, ttl);
    }

    private Date parseGeneratedAt(String value)
    {
        try { return value == null ? new Date() : Date.from(Instant.parse(value)); }
        catch (Exception ignored) { return new Date(); }
    }

    private Date parseBusinessDate(String value)
    {
        try { return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(value); }
        catch (Exception ignored) { return new Date(0); }
    }

    public static class UpstreamResponse
    {
        private final String body;
        private final String etag;
        public UpstreamResponse(String body, String etag) { this.body = body; this.etag = etag; }
        public String getBody() { return body; }
        public String getEtag() { return etag; }
    }
}
