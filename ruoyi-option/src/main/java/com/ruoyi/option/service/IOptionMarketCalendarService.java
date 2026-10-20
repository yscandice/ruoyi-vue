package com.ruoyi.option.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionMarketDay;

/**
 * 美股交易日历 服务层（只登记例外日：节假日、提前收盘日）
 * 
 * @author yscandice
 */
public interface IOptionMarketCalendarService
{
    /**
     * 查询交易日历例外日列表
     * 
     * @param day 查询条件（params.year 按年过滤）
     * @return 例外日集合
     */
    public List<OptionMarketDay> selectOptionMarketDayList(OptionMarketDay day);

    /**
     * 查询某一天的登记
     * 
     * @param tradeDate 日期（美东）
     * @return 例外日
     */
    public OptionMarketDay selectOptionMarketDayByDate(LocalDate tradeDate);

    /**
     * 查询某一年的例外日（带缓存 option:market:calendar:{year}），供 OptionMarketCalendar 使用
     * 
     * @param year 年份
     * @return 日期 → 例外日
     */
    public Map<LocalDate, OptionMarketDay> selectYearExceptions(int year);

    /**
     * 新增例外日
     * 
     * @param day 例外日
     * @return 结果
     */
    public int insertOptionMarketDay(OptionMarketDay day);

    /**
     * 修改例外日
     * 
     * @param day 例外日
     * @return 结果
     */
    public int updateOptionMarketDay(OptionMarketDay day);

    /**
     * 批量删除例外日
     * 
     * @param tradeDates 日期集合
     * @return 结果
     */
    public int deleteOptionMarketDayByDates(LocalDate[] tradeDates);

    /**
     * 清除某一年的日历缓存
     * 
     * @param year 年份
     */
    public void clearCache(int year);
}
