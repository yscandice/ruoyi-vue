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
import com.ruoyi.option.domain.OptionFollowItem;
import com.ruoyi.option.domain.OptionFollowSync;
import com.ruoyi.option.service.IOptionFollowService;

/**
 * OSM「跟踪」菜单（我的跟踪 / MY MARKS：卖方信号 / 中期信号 / 买方信号 / 动态管理 标记）
 * 
 * 读接口只读定时同步后的 Redis 缓存（未命中时从表回填），不实时请求 OSM；
 * 跟踪页公共接口 feed、seller-guide、entitlement、decisions、management-choices、updates、support 沿用 {@link OptionTodayController}。
 * 
 * @author yscandice
 */
@RestController
public class OptionFollowController extends BaseController
{
    @Autowired
    private IOptionFollowService followService;

    /**
     * 跟踪菜单汇总（交易日、批次、各类标记条数、标记计数、「跟了」参考盈亏、同步状态）
     */
    @GetMapping("/api/member/follow")
    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(followService.getSummary(), request, response);
    }

    /**
     * 跟踪标记列表（kind：signal 卖方信号 / midterm 中期信号 / buyer 买方信号 / manage 动态管理 / all 全部；
     * symbol 可选按标的过滤，mark 可选 ADOPT_INTENT 跟了 / OBSERVE 观察 / SKIP 否决）
     */
    @GetMapping("/api/member/follow/{kind}")
    public void kind(@PathVariable("kind") String kind,
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "mark", required = false) String mark, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(followService.getKind(kind, symbol, mark), request, response);
    }

    /**
     * 立即同步跟踪菜单（忽略交易时段，强制重算标记）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "跟踪菜单同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/follow")
    public AjaxResult sync()
    {
        return success(followService.sync(true, "MANUAL", SecurityUtils.getUsername()));
    }

    /**
     * 查询跟踪菜单同步记录
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/follow/list")
    public TableDataInfo list(OptionFollowSync sync)
    {
        startPage();
        List<OptionFollowSync> list = followService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 查询标记版本列表（不含内容；lastSyncId 传最新批次即为当前标记）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/follow/{kind}/items")
    public TableDataInfo items(@PathVariable("kind") String kind, OptionFollowItem item)
    {
        startPage();
        List<OptionFollowItem> list = followService.selectItemList(kind, item);
        return getDataTable(list);
    }

    /**
     * 查询标记版本详情（含内容）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/follow/{kind}/items/{versionId}")
    public AjaxResult item(@PathVariable("kind") String kind, @PathVariable("versionId") Long versionId)
    {
        return success(followService.selectItem(kind, versionId));
    }
}
