package com.ruoyi.option.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionMarketDay;

/**
 * 美股交易日历（例外日） 数据层（option_market_calendar）
 * 
 * @author yscandice
 */
public interface OptionMarketDayMapper
{
    /**
     * 查询美股交易日历（例外日）
     * 
     * @param tradeDate 主键
     * @return 美股交易日历（例外日）
     */
    public OptionMarketDay selectOptionMarketDayById(LocalDate tradeDate);

    /**
     * 查询美股交易日历（例外日）列表
     * 
     * @param optionMarketDay 查询条件
     * @return 美股交易日历（例外日）集合
     */
    public List<OptionMarketDay> selectOptionMarketDayList(OptionMarketDay optionMarketDay);

    /**
     * 查询日期区间内登记的例外日（节假日 / 提前收盘日）
     * 
     * @param beginDate 开始日期（含）
     * @param endDate 结束日期（含）
     * @return 例外日集合
     */
    public List<OptionMarketDay> selectOptionMarketDayByRange(@Param("beginDate") LocalDate beginDate, @Param("endDate") LocalDate endDate);

    /**
     * 新增美股交易日历（例外日）
     * 
     * @param optionMarketDay 美股交易日历（例外日）
     * @return 结果
     */
    public int insertOptionMarketDay(OptionMarketDay optionMarketDay);

    /**
     * 修改美股交易日历（例外日）
     * 
     * @param optionMarketDay 美股交易日历（例外日）
     * @return 结果
     */
    public int updateOptionMarketDay(OptionMarketDay optionMarketDay);

    /**
     * 删除美股交易日历（例外日）
     * 
     * @param tradeDate 主键
     * @return 结果
     */
    public int deleteOptionMarketDayById(LocalDate tradeDate);

    /**
     * 批量删除美股交易日历（例外日）
     * 
     * @param tradeDates 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionMarketDayByIds(LocalDate[] tradeDates);
}
