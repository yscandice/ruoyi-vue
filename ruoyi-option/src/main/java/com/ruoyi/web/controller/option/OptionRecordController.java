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
import com.ruoyi.option.domain.OptionRecordItem;
import com.ruoyi.option.domain.OptionRecordSync;
import com.ruoyi.option.service.IOptionRecordService;
import com.ruoyi.option.service.IOptionRecordService.RecordQuery;

/**
 * OSM「战绩」菜单（战绩明细 / 每日结算净额 / 按标的 / 战绩统计 / 账户模拟）
 * 
 * 读接口只读定时同步后的 Redis 缓存（未命中时从表回填），不实时请求 OSM；
 * 战绩页没有专属 JSON 接口，数据来自 /api/member/feed（performance、history）与 /api/member/decisions。
 * 
 * @author yscandice
 */
@RestController
public class OptionRecordController extends BaseController
{
    @Autowired
    private IOptionRecordService recordService;

    /**
     * 战绩菜单汇总（交易日、批次、各类型条数、全部/本月/本周统计、同步状态）
     */
    @GetMapping("/api/member/record")
    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(recordService.getSummary(), request, response);
    }

    /**
     * 战绩菜单数据（kind：trade 战绩明细 / daily 每日结算净额 / symbol 按标的 / stat 战绩统计 / account 账户模拟）
     * 
     * range（all 全部 / month 本月）作用于 trade、daily、symbol；scope（all 全部正式信号 / adopted 我跟了的）作用于
     * trade、daily；granularity（day / week / month）作用于 daily；symbol 作用于 trade、symbol；outcome 作用于 trade
     */
    @GetMapping("/api/member/record/{kind}")
    public void kind(@PathVariable("kind") String kind,
            @RequestParam(value = "range", required = false) String range,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "granularity", required = false) String granularity,
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "outcome", required = false) String outcome, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(
                recordService.getKind(kind, new RecordQuery(range, scope, granularity, symbol, outcome)), request,
                response);
    }

    /**
     * 立即同步战绩菜单（忽略交易时段，强制重算）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "战绩菜单同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/record")
    public AjaxResult sync()
    {
        return success(recordService.sync(true, "MANUAL", SecurityUtils.getUsername()));
    }

    /**
     * 查询战绩菜单同步记录
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/record/list")
    public TableDataInfo list(OptionRecordSync sync)
    {
        startPage();
        List<OptionRecordSync> list = recordService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 查询条目版本列表（不含内容；lastSyncId 传最新批次即为当前内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/record/{kind}/items")
    public TableDataInfo items(@PathVariable("kind") String kind, OptionRecordItem item)
    {
        startPage();
        List<OptionRecordItem> list = recordService.selectItemList(kind, item);
        return getDataTable(list);
    }

    /**
     * 查询条目版本详情（含内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/record/{kind}/items/{versionId}")
    public AjaxResult item(@PathVariable("kind") String kind, @PathVariable("versionId") Long versionId)
    {
        return success(recordService.selectItem(kind, versionId));
    }
}
