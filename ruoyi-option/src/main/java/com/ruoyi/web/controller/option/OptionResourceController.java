package com.ruoyi.web.controller.option;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.option.domain.OptionResourceSync;
import com.ruoyi.option.domain.OptionResourceVersion;
import com.ruoyi.option.enums.OptionResource;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionResourceService;

/**
 * OSM 会员接口（seller-guide、entitlement、decisions、management-choices、premarket、updates、support）
 * 
 * 读接口只读 Redis（未命中时从版本表回填），返回与 OSM 相同的 JSON。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/option")
public class OptionResourceController extends BaseController
{
    @Autowired
    private IOptionResourceService resourceService;

    /**
     * 读取接口数据（premarket 需传 d=yyyy-MM-dd，缺省为美东当天）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/today/{resource}")
    public void today(@PathVariable("resource") String resource, @RequestParam(value = "d", required = false) String d,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        if (OptionResource.of(resource) == null)
        {
            OptionFeedWriter.notFound(response, "不支持的接口：" + resource);
            return;
        }
        write(resourceService.get(resource, d), request, response);
    }

    /**
     * 立即同步单个接口（忽略同步时段）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "期权接口同步", businessType = BusinessType.OTHER)
    @PostMapping("/sync/{resource}")
    public AjaxResult sync(@PathVariable("resource") String resource,
            @RequestParam(value = "d", required = false) String d)
    {
        return success(resourceService.sync(resource, d, true, "MANUAL"));
    }

    /**
     * 立即同步全部 7 个接口
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "期权接口同步", businessType = BusinessType.OTHER)
    @PostMapping("/sync/resource/all")
    public AjaxResult syncAll()
    {
        return success(resourceService.syncAll("MANUAL"));
    }

    /**
     * 同步记录
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/sync/resource/list")
    public TableDataInfo syncList(OptionResourceSync sync)
    {
        startPage();
        List<OptionResourceSync> list = resourceService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 接口内容版本列表
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/sync/resource/{resource}/versions")
    public TableDataInfo versions(@PathVariable("resource") String resource,
            @RequestParam(value = "bizKey", required = false) String bizKey)
    {
        startPage();
        List<OptionResourceVersion> list = resourceService.selectVersionList(resource, bizKey);
        return getDataTable(list);
    }

    /**
     * 指定版本的原始内容
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/sync/resource/{resource}/versions/{versionId}")
    public void version(@PathVariable("resource") String resource, @PathVariable("versionId") Long versionId,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceVersion version = resourceService.selectVersion(resource, versionId);
        if (version == null)
        {
            OptionFeedWriter.notFound(response, "版本不存在");
            return;
        }
        response.setHeader("X-Version-Id", String.valueOf(versionId));
        write(new FeedBody(version.getPayload(), "W/\"" + version.getContentHash().substring(0, 24) + "\"", null,
                null), request, response);
    }

    /**
     * 输出原始 JSON
     */
    static void write(FeedBody body, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        if (body == null)
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"暂无数据，请先登录 OSM 并执行同步\"}");
            return;
        }
        OptionFeedWriter.write(body, request, response);
    }
}
