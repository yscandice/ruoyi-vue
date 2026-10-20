package com.ruoyi.web.controller.option;

import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.option.domain.OptionMarketDay;
import com.ruoyi.option.service.IOptionMarketCalendarService;

/**
 * 美股交易日历（只维护例外日：节假日 / 提前收盘日；未登记的工作日按正常交易日）
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/option/market/calendar")
public class OptionMarketCalendarController extends BaseController
{
    @Autowired
    private IOptionMarketCalendarService calendarService;

    /**
     * 查询交易日历（按年：year=2026）
     */
    @PreAuthorize("@ss.hasPermi('option:market:list')")
    @GetMapping("/list")
    public TableDataInfo list(OptionMarketDay day, @RequestParam(value = "year", required = false) Integer year)
    {
        if (year != null)
        {
            day.getParams().put("year", String.valueOf(year));
        }
        startPage();
        List<OptionMarketDay> list = calendarService.selectOptionMarketDayList(day);
        return getDataTable(list);
    }

    /**
     * 查询某一天的登记
     */
    @PreAuthorize("@ss.hasPermi('option:market:list')")
    @GetMapping("/{tradeDate}")
    public AjaxResult getInfo(@PathVariable("tradeDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate)
    {
        return success(calendarService.selectOptionMarketDayByDate(tradeDate));
    }

    /**
     * 新增节假日 / 提前收盘日
     */
    @PreAuthorize("@ss.hasPermi('option:market:edit')")
    @Log(title = "交易日历", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OptionMarketDay day)
    {
        day.setCreateBy(getUsername());
        return toAjax(calendarService.insertOptionMarketDay(day));
    }

    /**
     * 修改节假日 / 提前收盘日
     */
    @PreAuthorize("@ss.hasPermi('option:market:edit')")
    @Log(title = "交易日历", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OptionMarketDay day)
    {
        day.setUpdateBy(getUsername());
        return toAjax(calendarService.updateOptionMarketDay(day));
    }

    /**
     * 删除节假日 / 提前收盘日（多个日期用英文逗号分隔）
     */
    @PreAuthorize("@ss.hasPermi('option:market:edit')")
    @Log(title = "交易日历", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tradeDates}")
    public AjaxResult remove(@PathVariable("tradeDates") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate[] tradeDates)
    {
        return toAjax(calendarService.deleteOptionMarketDayByDates(tradeDates));
    }
}
