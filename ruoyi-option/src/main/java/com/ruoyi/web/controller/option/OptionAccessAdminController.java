package com.ruoyi.web.controller.option;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * 会员数据权限 后台管理（盘后视图放出预览；策略、冻结记录在后续步骤补充）
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
