package com.ruoyi.option.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;

/**
 * OSM 上游接口客户端
 * 
 * @author yscandice
 */
@Component
public class OsmClient
{
    private static final Pattern MAX_AGE = Pattern.compile("(?i)(?:^|;)\\s*max-age\\s*=\\s*(\\d+)");

    private static final String USER_AGENT = "Mozilla/5.0 (RuoYi option sync)";

    @Value("${OPTION_LOGIN_BASE_URL:https://osmtrade.com}")
    private String baseUrl;

    @Autowired
    @Qualifier("optionRestTemplate")
    private RestTemplate restTemplate;

    /**
     * 登录 OSM，返回会话 Cookie
     * 
     * @param email 账号
     * @param password 密码
     * @return 会话
     */
    public OsmSession login(String email, String password)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("email", email, "password", password), headers);
        ResponseEntity<String> response;
        try
        {
            response = restTemplate.exchange(baseUrl + OptionFeedConstants.PATH_LOGIN, HttpMethod.POST, request,
                    String.class);
        }
        catch (HttpStatusCodeException e)
        {
            throw new ServiceException("OSM 登录失败（HTTP " + e.getStatusCode().value() + "）");
        }
        catch (ResourceAccessException e)
        {
            throw new ServiceException("OSM 登录请求超时或网络不可达");
        }
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookie = cookieHeader(setCookies);
        if (StringUtils.isEmpty(cookie))
        {
            throw new ServiceException("OSM 登录未返回会话 Cookie");
        }
        return new OsmSession(cookie, cookieTtl(setCookies));
    }

    /**
     * 请求 feed（支持 If-None-Match 与 gzip）
     * 
     * @param cookie 会话 Cookie
     * @param etag 上次的 ETag，可为空
     * @return 响应（status 为 401/403 时表示会话失效）
     */
    public OsmResponse getFeed(String cookie, String etag)
    {
        return get(OptionFeedConstants.PATH_FEED, cookie, etag);
    }

    /**
     * 请求会员 GET 接口（支持 If-None-Match 与 gzip）
     * 
     * @param pathAndQuery 路径（可带查询参数）
     * @param cookie 会话 Cookie
     * @param etag 上次的 ETag，可为空
     * @return 响应（status 为 401/403 时表示会话失效）
     */
    public OsmResponse get(String pathAndQuery, String cookie, String etag)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.REFERER, baseUrl + "/member/home");
        if (StringUtils.isNotEmpty(etag))
        {
            headers.set(HttpHeaders.IF_NONE_MATCH, etag);
        }
        try
        {
            ResponseEntity<byte[]> response = restTemplate.exchange(java.net.URI.create(baseUrl + pathAndQuery),
                    HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            HttpHeaders h = response.getHeaders();
            byte[] raw = response.getBody();
            String body = raw == null ? null : decode(raw, h.getFirst(HttpHeaders.CONTENT_ENCODING));
            return new OsmResponse(response.getStatusCode().value(), body, h.getETag(),
                    h.getFirst("X-Feed-Generated-At"), raw == null ? 0 : raw.length, null);
        }
        catch (HttpStatusCodeException e)
        {
            String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
            return new OsmResponse(e.getStatusCode().value(), null, null, null, 0, retryAfter);
        }
        catch (ResourceAccessException e)
        {
            throw new ServiceException("OSM 接口请求超时或网络不可达：" + pathAndQuery);
        }
    }

    private String decode(byte[] raw, String encoding)
    {
        boolean gzip = "gzip".equalsIgnoreCase(encoding)
                || (raw.length > 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b);
        if (!gzip)
        {
            return new String(raw, StandardCharsets.UTF_8);
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw)))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new ServiceException("OSM 响应解压失败");
        }
    }

    private String cookieHeader(List<String> setCookies)
    {
        if (setCookies == null || setCookies.isEmpty())
        {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (String value : setCookies)
        {
            int end = value.indexOf(';');
            String pair = (end < 0 ? value : value.substring(0, end)).trim();
            if (!pair.isEmpty())
            {
                pairs.add(pair);
            }
        }
        return String.join("; ", pairs);
    }

    private int cookieTtl(List<String> setCookies)
    {
        int ttl = OptionFeedConstants.MAX_COOKIE_TTL_SECONDS;
        for (String value : setCookies)
        {
            Matcher matcher = MAX_AGE.matcher(value);
            if (matcher.find())
            {
                try
                {
                    ttl = Math.min(ttl, Integer.parseInt(matcher.group(1)));
                }
                catch (NumberFormatException ignored)
                {
                    // 保持默认值
                }
            }
        }
        return Math.max(60, ttl);
    }

    /**
     * 登录结果
     */
    public record OsmSession(String cookie, int ttlSeconds)
    {
    }

    /**
     * feed 响应
     */
    public record OsmResponse(int status, String body, String etag, String generatedAt, int transferBytes,
            String retryAfter)
    {
    }
}
