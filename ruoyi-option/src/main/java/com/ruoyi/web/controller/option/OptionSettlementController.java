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
import com.ruoyi.option.domain.OptionSettlementItem;
import com.ruoyi.option.domain.OptionSettlementSync;
import com.ruoyi.option.service.IOptionSettlementService;
import com.ruoyi.option.service.IOptionSettlementService.SettlementQuery;

/**
 * OSM「结算」菜单（已结算 / 结算单 / 结算统计）
 * 
 * 读接口只读定时同步后的 Redis 缓存（未命中时从表回填），不实时请求 OSM；
 * 结算页没有专属接口，数据全部来自 /api/member/feed（沿用 {@link OptionTodayController}）。
 * 
 * @author yscandice
 */
@RestController
public class OptionSettlementController extends BaseController
{
    @Autowired
    private IOptionSettlementService settlementService;

    /**
     * 结算菜单汇总（交易日、批次、各类型条数、结算统计、同步状态）
     */
    @GetMapping("/api/member/settlement")
    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(settlementService.getSummary(), request, response);
    }

    /**
     * 结算菜单数据（kind：record 已结算 / card 结算单 / stat 结算统计）
     * 
     * symbol 按标的或公司名过滤；range（week 本周 / month 本月 / all 全部 / custom 自定义，配合 from、to）
     * 与 sort（settled 按结算日 / issued 按发报日）仅作用于 record
     */
    @GetMapping("/api/member/settlement/{kind}")
    public void kind(@PathVariable("kind") String kind,
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "range", required = false) String range,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "sort", required = false) String sort, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(settlementService.getKind(kind, new SettlementQuery(symbol, range, from, to, sort)),
                request, response);
    }

    /**
     * 立即同步结算菜单（忽略交易时段，强制重算）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "结算菜单同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/settlement")
    public AjaxResult sync()
    {
        return success(settlementService.sync(true, "MANUAL", SecurityUtils.getUsername()));
    }

    /**
     * 查询结算菜单同步记录
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/settlement/list")
    public TableDataInfo list(OptionSettlementSync sync)
    {
        startPage();
        List<OptionSettlementSync> list = settlementService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 查询条目版本列表（不含内容；lastSyncId 传最新批次即为当前内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/settlement/{kind}/items")
    public TableDataInfo items(@PathVariable("kind") String kind, OptionSettlementItem item)
    {
        startPage();
        List<OptionSettlementItem> list = settlementService.selectItemList(kind, item);
        return getDataTable(list);
    }

    /**
     * 查询条目版本详情（含内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/settlement/{kind}/items/{versionId}")
    public AjaxResult item(@PathVariable("kind") String kind, @PathVariable("versionId") Long versionId)
    {
        return success(settlementService.selectItem(kind, versionId));
    }
}
