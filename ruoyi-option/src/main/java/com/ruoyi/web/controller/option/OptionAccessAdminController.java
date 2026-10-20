package com.ruoyi.web.controller.option;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.domain.OptionCloseSnapshot;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionCloseSnapshotService;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * 会员数据权限 后台管理（盘后视图放出预览、会员身份缓存、冻结记录；策略管理在步骤 6 补充）
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/option/access")
public class OptionAccessAdminController extends BaseController
{
    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm[:ss]");

    @Autowired
    private IOptionAccessService accessService;

    @Autowired
    private IOptionCloseSnapshotService closeSnapshotService;

    /**
     * 冻结记录列表
     */
    @PreAuthorize("@ss.hasPermi('option:access:list')")
    @GetMapping("/snapshot/list")
    public TableDataInfo snapshotList(OptionCloseSnapshot snapshot)
    {
        startPage();
        List<OptionCloseSnapshot> list = closeSnapshotService.selectOptionCloseSnapshotList(snapshot);
        return getDataTable(list);
    }

    /**
     * 盘后视图状态：最近一次冻结、当前放出记录、各菜单实时键与盘后键数量对比
     */
    @PreAuthorize("@ss.hasPermi('option:access:list')")
    @GetMapping("/snapshot/status")
    public AjaxResult snapshotStatus()
    {
        return success(closeSnapshotService.selectViewStatus());
    }

    /**
     * 冻结记录详情（含各菜单键数量与存储字节数）
     */
    @PreAuthorize("@ss.hasPermi('option:access:list')")
    @GetMapping("/snapshot/{snapshotId}")
    public AjaxResult snapshotInfo(@PathVariable Long snapshotId)
    {
        return success(closeSnapshotService.selectOptionCloseSnapshotById(snapshotId));
    }

    /**
     * 手动冻结当天 { "force": false }：已冻结或未到收盘冻结点时需要 force = true
     */
    @PreAuthorize("@ss.hasPermi('option:access:edit')")
    @Log(title = "盘后视图冻结", businessType = BusinessType.OTHER)
    @PostMapping("/snapshot/freeze")
    public AjaxResult freeze(@RequestBody(required = false) Map<String, Object> body)
    {
        boolean force = body != null && Boolean.parseBoolean(String.valueOf(body.get("force")));
        return success(closeSnapshotService.freeze(force, getUsername()));
    }

    /**
     * 手动放出指定冻结记录（首次上线、补放）
     */
    @PreAuthorize("@ss.hasPermi('option:access:edit')")
    @Log(title = "盘后视图放出", businessType = BusinessType.OTHER)
    @PostMapping("/snapshot/{snapshotId}/release")
    public AjaxResult release(@PathVariable Long snapshotId)
    {
        return success(closeSnapshotService.release(snapshotId));
    }

    /**
     * 用指定冻结记录回填 Redis 盘后视图
     */
    @PreAuthorize("@ss.hasPermi('option:access:edit')")
    @Log(title = "盘后视图恢复", businessType = BusinessType.OTHER)
    @PostMapping("/snapshot/{snapshotId}/restore")
    public AjaxResult restore(@PathVariable Long snapshotId)
    {
        return success(closeSnapshotService.restore(snapshotId));
    }

    /**
     * 预览某个时间点盘后版看到的交易日、放出时间与下次放出时间
     * 
     * @param at 时间点：ISO 8601 带偏移（如 2026-10-20T10:00:00-04:00），或不带偏移（如 2026-10-20 10:00）按 zone 解析；为空取当前时间
     * @param zone at 不带偏移时使用的时区，默认 America/New_York
     */
    @PreAuthorize("@ss.hasPermi('option:access:list')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam(value = "at", required = false) String at,
            @RequestParam(value = "zone", required = false) String zone)
    {
        return success(accessService.resolveReleaseWindow(parseTime(at, zone)));
    }

    /**
     * 删除会员身份缓存：直接改库（如 trial_end_time）后调用，会员的下一次请求立即按新身份返回
     * 
     * @param userIds 用户ID，多个用英文逗号分隔
     */
    @PreAuthorize("@ss.hasPermi('option:access:edit')")
    @Log(title = "会员身份缓存", businessType = BusinessType.CLEAN)
    @DeleteMapping("/cache/{userIds}")
    public AjaxResult clearCache(@PathVariable Long[] userIds)
    {
        accessService.clearAccessCache(userIds);
        return success();
    }

    private static ZonedDateTime parseTime(String at, String zone)
    {
        ZoneId zoneId;
        try
        {
            zoneId = StringUtils.isEmpty(zone) ? OptionMarketCalendar.MARKET_ZONE : ZoneId.of(zone.trim());
        }
        catch (Exception e)
        {
            throw new ServiceException("时区格式错误：" + zone);
        }
        if (StringUtils.isEmpty(at))
        {
            return ZonedDateTime.now(zoneId);
        }
        // 查询串里未编码的 “+08:00” 会变成空格，先还原，再把日期与时间之间的空格换成 T
        String value = at.trim().replaceFirst("(\\d{2}:\\d{2}(:\\d{2})?) (\\d{2}:\\d{2})$", "$1+$3").replace(' ', 'T');
        try
        {
            return OffsetDateTime.parse(value).toZonedDateTime();
        }
        catch (DateTimeParseException ignored)
        {
            // 不带偏移，按 zone 解析
        }
        try
        {
            return LocalDateTime.parse(at.trim(), LOCAL_FORMAT).atZone(zoneId);
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("时间格式错误，示例：2026-10-20T10:00:00-04:00 或 2026-10-20 10:00");
        }
    }
}
