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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.option.domain.OptionInfoSync;
import com.ruoyi.option.domain.OptionInfoVersion;
import com.ruoyi.option.enums.OptionInfo;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionInfoService;

/**
 * OSM「信息」菜单（盘前简报 / 财报解读 / 札记 + 信息流、推荐信息）
 * 
 * 读接口只读定时同步后的 Redis 缓存（未命中时从表回填），不实时请求 OSM；
 * 信息页公共接口 feed、seller-guide、entitlement、updates、support 沿用 {@link OptionTodayController}；
 * 推荐信息 /api/member/referrals 属于同步账号的个人数据，已下线，改由 {@link OptionSettingsController} 固定返回。
 * 
 * @author yscandice
 */
@RestController
public class OptionInfoController extends BaseController
{
    @Autowired
    private IOptionInfoService infoService;

    /**
     * 信息流（信息菜单角标与最新文章列表）
     */
    @GetMapping("/api/member/info-feed")
    public void infoFeed(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(infoService.getRaw(OptionInfo.INFO_FEED.getKey()), request, response);
    }

    /**
     * 盘前简报页（d=yyyy-MM-dd，缺省为最新一期）
     */
    @GetMapping("/api/member/info/premarket")
    public void premarket(@RequestParam(value = "d", required = false) String d, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(infoService.getPremarket(d), request, response);
    }

    /**
     * 财报解读页（id=财报ID，如 TSM-2026-10-15，缺省为最新一篇）
     */
    @GetMapping("/api/member/info/earnings")
    public void earnings(@RequestParam(value = "id", required = false) String id, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(infoService.getEarnings(id), request, response);
    }

    /**
     * 札记页（全部札记；传 id 时附带该篇）
     */
    @GetMapping("/api/member/info/notes")
    public void notes(@RequestParam(value = "id", required = false) String id, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(infoService.getNotes(id), request, response);
    }

    /**
     * 立即同步单个数据源（premarket 可传 bizKey=日期，earnings 可传 bizKey=财报ID；不传则同步最新并补齐历史）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "期权信息同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/info/{info}")
    public AjaxResult sync(@PathVariable("info") String info,
            @RequestParam(value = "bizKey", required = false) String bizKey)
    {
        return success(infoService.sync(info, bizKey, "MANUAL", getUsername()));
    }

    /**
     * 立即同步信息菜单全部数据源
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "期权信息同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/info-all")
    public AjaxResult syncAll()
    {
        return success(infoService.syncAll("MANUAL", getUsername()));
    }

    /**
     * 同步记录
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/info/list")
    public TableDataInfo syncList(OptionInfoSync sync)
    {
        startPage();
        List<OptionInfoSync> list = infoService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 数据版本列表
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/info/{info}/versions")
    public TableDataInfo versions(@PathVariable("info") String info,
            @RequestParam(value = "bizKey", required = false) String bizKey)
    {
        startPage();
        List<OptionInfoVersion> list = infoService.selectVersionList(info, bizKey);
        return getDataTable(list);
    }

    /**
     * 指定版本的内容
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/info/{info}/versions/{versionId}")
    public void version(@PathVariable("info") String info, @PathVariable("versionId") Long versionId,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionInfoVersion version = infoService.selectVersion(info, versionId);
        if (version == null)
        {
            OptionFeedWriter.notFound(response, "版本不存在");
            return;
        }
        response.setHeader("X-Version-Id", String.valueOf(versionId));
        OptionResourceController.write(new FeedBody(version.getPayload(),
                "W/\"" + version.getContentHash().substring(0, 24) + "\"", null, null), request, response);
    }
}
