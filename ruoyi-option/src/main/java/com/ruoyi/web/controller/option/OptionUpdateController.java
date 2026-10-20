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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.option.domain.OptionUpdateItem;
import com.ruoyi.option.domain.OptionUpdateSync;
import com.ruoyi.option.service.IOptionUpdateService;

/**
 * OSM「更新」菜单（/member/updates 功能更新）
 * 
 * 阅读进度按用户记录，见 {@link OptionSettingsController}：GET /api/member/updates、PUT /api/member/updates/seen
 * 
 * 前端只读（Redis，带 ETag）：
 *   GET /api/member/updates/summary            汇总（页头、条目数、日期范围、按月 / 目标链接分布、最新 5 条、
 *                                              阅读进度 progress、同步状态 status）
 *   GET /api/member/updates/{kind}             类型列表（page 页头 / entry 更新条目），
 *                                              entry 可选 keyword 关键字、month 月份 yyyy-MM、target 目标链接前缀过滤
 *   GET /api/member/updates/{kind}/{key}       单条完整内容，如 entry/2026-10-19-roll-safer、page/updates
 * 管理：
 *   POST /option/sync/update                   立即同步
 *   GET  /option/sync/update/list              同步流水
 *   GET  /option/sync/update/{kind}/items      条目版本
 *   GET  /option/sync/update/{kind}/items/{versionId}  条目版本详情（含内容）
 * 
 * @author yscandice
 */
@RestController
public class OptionUpdateController extends BaseController
{
    @Autowired
    private IOptionUpdateService updateService;

    /**
     * 更新汇总
     */
    @GetMapping("/api/member/updates/summary")
    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(updateService.getSummary(), request, response);
    }

    /**
     * 类型列表
     */
    @GetMapping("/api/member/updates/{kind}")
    public void kind(@PathVariable("kind") String kind,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "target", required = false) String target, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(updateService.getKind(kind, keyword, month, target), request, response);
    }

    /**
     * 单条完整内容
     */
    @GetMapping("/api/member/updates/{kind}/{key}")
    public void item(@PathVariable("kind") String kind, @PathVariable("key") String key,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(updateService.getItem(kind, key), request, response);
    }

    /**
     * 立即同步更新菜单
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "更新菜单同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/update")
    public AjaxResult sync()
    {
        return success(updateService.sync(true, "MANUAL", SecurityUtils.getUsername()));
    }

    /**
     * 同步流水
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/update/list")
    public TableDataInfo list(OptionUpdateSync sync)
    {
        startPage();
        List<OptionUpdateSync> list = updateService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 条目版本列表
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/update/{kind}/items")
    public TableDataInfo items(@PathVariable("kind") String kind, OptionUpdateItem item)
    {
        startPage();
        List<OptionUpdateItem> list = updateService.selectItemList(kind, item);
        return getDataTable(list);
    }

    /**
     * 条目版本详情
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/update/{kind}/items/{versionId}")
    public AjaxResult version(@PathVariable("kind") String kind, @PathVariable("versionId") Long versionId)
    {
        return success(updateService.selectItem(kind, versionId));
    }
}
