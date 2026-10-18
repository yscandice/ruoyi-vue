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
import com.ruoyi.option.domain.OptionConductItem;
import com.ruoyi.option.domain.OptionConductSync;
import com.ruoyi.option.service.IOptionConductService;

/**
 * OSM「进行」菜单（进行中 / 保护窗口 / 动态管理 / 已到期 / 历史信号）
 * 
 * 读接口只读定时同步后的 Redis 缓存（未命中时从表回填），不实时请求 OSM；
 * 进行页公共接口 feed、seller-guide、entitlement、decisions、management-choices、updates、support 沿用 {@link OptionTodayController}。
 * 
 * @author yscandice
 */
@RestController
public class OptionConductController extends BaseController
{
    @Autowired
    private IOptionConductService conductService;

    /**
     * 进行菜单汇总（交易日、批次、各栏目条数、同步状态）
     */
    @GetMapping("/api/member/conduct")
    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(conductService.getSummary(), request, response);
    }

    /**
     * 进行菜单栏目（scope：active 进行中 / protection 保护窗口 / manage 动态管理 / expired 已到期 / history 历史信号；
     * symbol 可选，按标的或公司名过滤）
     */
    @GetMapping("/api/member/conduct/{scope}")
    public void scope(@PathVariable("scope") String scope,
            @RequestParam(value = "symbol", required = false) String symbol, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(conductService.getScope(scope, symbol), request, response);
    }

    /**
     * 立即同步进行菜单（忽略交易时段，强制重算栏目）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "进行菜单同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/conduct")
    public AjaxResult sync()
    {
        return success(conductService.sync(true, "MANUAL", SecurityUtils.getUsername()));
    }

    /**
     * 查询进行菜单同步记录
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/conduct/list")
    public TableDataInfo list(OptionConductSync sync)
    {
        startPage();
        List<OptionConductSync> list = conductService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 查询栏目条目版本列表（不含内容；lastSyncId 传最新批次即为当前栏目内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/conduct/{scope}/items")
    public TableDataInfo items(@PathVariable("scope") String scope, OptionConductItem item)
    {
        startPage();
        List<OptionConductItem> list = conductService.selectItemList(scope, item);
        return getDataTable(list);
    }

    /**
     * 查询栏目条目版本详情（含内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/conduct/{scope}/items/{versionId}")
    public AjaxResult item(@PathVariable("scope") String scope, @PathVariable("versionId") Long versionId)
    {
        return success(conductService.selectItem(scope, versionId));
    }
}
