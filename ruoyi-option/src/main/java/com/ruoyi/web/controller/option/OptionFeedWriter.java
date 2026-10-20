package com.ruoyi.web.controller.option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.utils.OptionAccessContext;

/**
 * 以与 OSM 相同的方式输出 feed 原始 JSON（ETag / 304 / X-Feed-Generated-At）
 * 
 * @author yscandice
 */
public class OptionFeedWriter
{
    /** 响应头：盘后视图暂无数据，返回空结构 */
    public static final String HEADER_EMPTY = "X-Option-Data-Empty";

    private OptionFeedWriter()
    {
    }

    /**
     * 输出 feed
     * 
     * @param feed 内容，为空时返回 503
     * @param request 请求
     * @param response 响应
     */
    public static void write(FeedBody feed, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-cache");
        if (feed == null)
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("{\"error\":\"feed 暂无数据，请先登录 OSM 并执行同步\"}");
            return;
        }
        OptionAccessContext context = OptionAccessContext.get();
        if (context != null && context.isEmpty())
        {
            // 盘后视图暂无数据，返回的是空结构（delayedFallback = EMPTY）
            response.setHeader(HEADER_EMPTY, "true");
        }
        response.setHeader("ETag", feed.etag());
        if (feed.syncId() != null)
        {
            response.setHeader("X-Feed-Sync-Id", String.valueOf(feed.syncId()));
        }
        if (feed.generatedAt() != null)
        {
            response.setHeader("X-Feed-Generated-At", feed.generatedAt());
        }
        if (feed.etag().equals(request.getHeader("If-None-Match")))
        {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        response.getWriter().write(feed.body());
    }

    /**
     * 输出 404
     * 
     * @param response 响应
     * @param message 说明
     */
    public static void notFound(HttpServletResponse response, String message) throws IOException
    {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "") + "\"}");
    }
}
