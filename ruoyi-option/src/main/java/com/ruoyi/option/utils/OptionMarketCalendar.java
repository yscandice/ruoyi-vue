package com.ruoyi.option.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.domain.OptionMarketDay;
import com.ruoyi.option.domain.OptionReleaseWindow;
import com.ruoyi.option.service.IOptionMarketCalendarService;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 美股交易日历与交易时段（America/New_York，夏令时由时区自动处理）
 * 
 * 交易日历读取 option_market_calendar（只登记例外日：节假日、提前收盘日），
 * 未登记的周一至周五按正常交易日 09:30–16:00，周末不交易。
 * 
 * feed 同步时段：交易日开盘至收盘后 5 分钟（含收盘后补拉一次）；
 * 收盘冻结点 closeCutoff(T) = T 的收盘时间 + option.access.closeBufferMinutes（默认 10 分钟）。
 * 
 * @author yscandice
 */
@Component
public class OptionMarketCalendar
{
    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    public static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    public static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 30);

    public static final LocalTime DEFAULT_CLOSE = LocalTime.of(16, 0);

    public static final LocalTime EARLY_CLOSE = LocalTime.of(13, 0);

    private static final LocalTime PREMARKET_OPEN = LocalTime.of(4, 0);

    /** 收盘后补拉一次的分钟数（16:00 收盘即同步到 16:05） */
    private static final int FINAL_PULL_MINUTES = 5;

    /** 向前 / 向后查找交易日的最大天数（防止日历数据异常时死循环） */
    private static final int MAX_SCAN_DAYS = 400;

    @Autowired
    private IOptionMarketCalendarService calendarService;

    @Autowired
    private ISysConfigService configService;

    /** 测试或离线计算时替换的日历来源（年份 → 当年例外日） */
    private Function<Integer, Map<LocalDate, OptionMarketDay>> yearLoader;

    public OptionMarketCalendar()
    {
    }

    /**
     * 使用指定日历来源创建（不依赖数据库，用于单元测试）
     * 
     * @param yearLoader 年份 → 当年例外日
     * @return 交易日历
     */
    public static OptionMarketCalendar of(Function<Integer, Map<LocalDate, OptionMarketDay>> yearLoader)
    {
        OptionMarketCalendar calendar = new OptionMarketCalendar();
        calendar.yearLoader = yearLoader;
        return calendar;
    }

    // ------------------------------------------------------------------ 交易时段（原有能力）

    /**
     * 当前是否处于 feed 同步时段
     * 
     * @return 结果
     */
    public boolean isFeedSyncWindow()
    {
        return isFeedSyncWindow(ZonedDateTime.now(MARKET_ZONE));
    }

    /**
     * 指定时间是否处于 feed 同步时段（交易日开盘至收盘后 5 分钟，提前收盘日按当天收盘时间）
     * 
     * @param time 时间（任意时区）
     * @return 结果
     */
    public boolean isFeedSyncWindow(ZonedDateTime time)
    {
        ZonedDateTime et = time.withZoneSameInstant(MARKET_ZONE);
        LocalDate day = et.toLocalDate();
        if (!isTradingDay(day))
        {
            return false;
        }
        LocalTime now = et.toLocalTime();
        return !now.isBefore(openTime(day)) && now.isBefore(closeTime(day).plusMinutes(FINAL_PULL_MINUTES));
    }

    /**
     * 当前是否处于美东盘前 04:00 至开盘（交易日）
     * 
     * @return 结果
     */
    public boolean isPremarketWindow()
    {
        ZonedDateTime et = ZonedDateTime.now(MARKET_ZONE);
        LocalDate day = et.toLocalDate();
        if (!isTradingDay(day))
        {
            return false;
        }
        LocalTime now = et.toLocalTime();
        return !now.isBefore(PREMARKET_OPEN) && now.isBefore(openTime(day));
    }

    /**
     * 指定时间是否处于盘中（交易日开盘至收盘）
     * 
     * @param time 时间（任意时区）
     * @return 结果
     */
    public boolean isMarketOpen(ZonedDateTime time)
    {
        ZonedDateTime et = time.withZoneSameInstant(MARKET_ZONE);
        LocalDate day = et.toLocalDate();
        if (!isTradingDay(day))
        {
            return false;
        }
        LocalTime now = et.toLocalTime();
        return !now.isBefore(openTime(day)) && now.isBefore(closeTime(day));
    }

    /**
     * 是否交易日（非周末、非节假日）
     * 
     * @param time 时间（任意时区，按美东日期判断）
     * @return 结果
     */
    public boolean isTradingDay(ZonedDateTime time)
    {
        return isTradingDay(time.withZoneSameInstant(MARKET_ZONE).toLocalDate());
    }

    /**
     * 是否交易日（非周末、非节假日）
     * 
     * @param day 美东日期
     * @return 结果
     */
    public boolean isTradingDay(LocalDate day)
    {
        DayOfWeek week = day.getDayOfWeek();
        if (week == DayOfWeek.SATURDAY || week == DayOfWeek.SUNDAY)
        {
            return false;
        }
        OptionMarketDay exception = exception(day);
        return exception == null || !"0".equals(exception.getTrading());
    }

    /**
     * 是否提前收盘日
     * 
     * @param day 美东日期
     * @return 结果
     */
    public boolean isEarlyClose(LocalDate day)
    {
        OptionMarketDay exception = exception(day);
        return isTradingDay(day) && exception != null && "1".equals(exception.getEarlyClose());
    }

    /**
     * 节假日名称（未登记返回 null）
     * 
     * @param day 美东日期
     * @return 名称
     */
    public String holidayName(LocalDate day)
    {
        OptionMarketDay exception = exception(day);
        return exception == null ? null : exception.getHolidayName();
    }

    /**
     * 开盘时间（美东）
     * 
     * @param day 交易日
     * @return 开盘时间
     */
    public LocalTime openTime(LocalDate day)
    {
        OptionMarketDay exception = exception(day);
        LocalTime time = exception == null ? null : parseTime(exception.getOpenTime());
        return time == null ? DEFAULT_OPEN : time;
    }

    /**
     * 收盘时间（美东）：提前收盘日按日历表 close_time（未填按 13:00），其余 16:00
     * 
     * @param day 交易日
     * @return 收盘时间
     */
    public LocalTime closeTime(LocalDate day)
    {
        OptionMarketDay exception = exception(day);
        if (exception == null)
        {
            return DEFAULT_CLOSE;
        }
        LocalTime time = parseTime(exception.getCloseTime());
        if ("1".equals(exception.getEarlyClose()))
        {
            return time == null ? EARLY_CLOSE : time;
        }
        return time == null ? DEFAULT_CLOSE : time;
    }

    /**
     * 美东当天日期 yyyy-MM-dd
     * 
     * @return 日期
     */
    public String todayEt()
    {
        return ZonedDateTime.now(MARKET_ZONE).toLocalDate().toString();
    }

    // ------------------------------------------------------------------ 收盘冻结 / 放出计算（数据权限）

    /**
     * 收盘冻结点 close(T)：收盘时间 + 参数 option.access.closeBufferMinutes
     * 
     * @param day 交易日（美东）
     * @return 冻结点（美东时区）
     */
    public ZonedDateTime closeCutoff(LocalDate day)
    {
        return closeCutoff(day, closeBufferMinutes());
    }

    /**
     * 收盘冻结点 close(T)：收盘时间 + 缓冲分钟
     * 
     * @param day 交易日（美东）
     * @param bufferMinutes 缓冲分钟
     * @return 冻结点（美东时区）
     */
    public ZonedDateTime closeCutoff(LocalDate day, int bufferMinutes)
    {
        if (!isTradingDay(day))
        {
            throw new ServiceException(day + " 不是交易日，没有收盘冻结点");
        }
        return ZonedDateTime.of(day, closeTime(day), MARKET_ZONE).plusMinutes(bufferMinutes);
    }

    /**
     * 往前数 n 个交易日（不含当天）；n = 0 时返回当天
     * 
     * @param day 起点日期（美东）
     * @param n 交易日数
     * @return 交易日
     */
    public LocalDate previousSession(LocalDate day, int n)
    {
        if (n < 0)
        {
            throw new ServiceException("交易日数不能为负数");
        }
        LocalDate cursor = day;
        int found = 0;
        int scanned = 0;
        while (found < n)
        {
            cursor = cursor.minusDays(1);
            if (++scanned > MAX_SCAN_DAYS * Math.max(1, n))
            {
                throw new ServiceException("交易日历数据异常：" + day + " 之前找不到交易日");
            }
            if (isTradingDay(cursor))
            {
                found++;
            }
        }
        return cursor;
    }

    /**
     * 下一个交易日（不含当天）
     * 
     * @param day 起点日期（美东）
     * @return 交易日
     */
    public LocalDate nextSession(LocalDate day)
    {
        LocalDate cursor = day;
        for (int i = 0; i < MAX_SCAN_DAYS; i++)
        {
            cursor = cursor.plusDays(1);
            if (isTradingDay(cursor))
            {
                return cursor;
            }
        }
        throw new ServiceException("交易日历数据异常：" + day + " 之后找不到交易日");
    }

    /**
     * 最近一个“收盘冻结点 ≤ 指定时间”的交易日（即最近一次冻结 / 放出发生的交易日）
     * 
     * @param time 时间（任意时区）
     * @param bufferMinutes 缓冲分钟
     * @return 交易日（美东）
     */
    public LocalDate latestClosedSession(ZonedDateTime time, int bufferMinutes)
    {
        LocalDate day = time.withZoneSameInstant(MARKET_ZONE).toLocalDate();
        if (isTradingDay(day) && !closeCutoff(day, bufferMinutes).isAfter(time))
        {
            return day;
        }
        return previousSession(day, 1);
    }

    /**
     * 计算指定时间点盘后版用户看到的数据（参数取 sys_config）
     * 
     * @param time 时间（任意时区）
     * @return 放出窗口
     */
    public OptionReleaseWindow releaseWindow(ZonedDateTime time)
    {
        return releaseWindow(time, delayedLagSessions(), closeBufferMinutes());
    }

    /**
     * 计算指定时间点盘后版用户看到的数据
     * 
     * <pre>
     * R = 最近一个 close(R) ≤ time 的交易日；D = R 往前数 lag 个交易日
     * dataAsOf = close(D)，releasedAt = close(R)，nextReleaseAt = close(R 的下一个交易日)
     * </pre>
     * 
     * @param time 时间（任意时区）
     * @param lagSessions 滞后交易日数（1 = 收盘后放出前一个交易日；0 = 放出当天）
     * @param bufferMinutes 收盘缓冲分钟
     * @return 放出窗口
     */
    public OptionReleaseWindow releaseWindow(ZonedDateTime time, int lagSessions, int bufferMinutes)
    {
        LocalDate release = latestClosedSession(time, bufferMinutes);
        LocalDate data = previousSession(release, lagSessions);
        LocalDate next = nextSession(release);

        OptionReleaseWindow window = new OptionReleaseWindow();
        window.setAt(time);
        window.setLagSessions(lagSessions);
        window.setCloseBufferMinutes(bufferMinutes);
        window.setTradingDay(isTradingDay(time));
        window.setMarketOpen(isMarketOpen(time));
        window.setReleaseTradeDate(release);
        window.setDataTradeDate(data);
        window.setDataAsOf(closeCutoff(data, bufferMinutes));
        window.setReleasedAt(closeCutoff(release, bufferMinutes));
        window.setNextReleaseTradeDate(next);
        window.setNextDataTradeDate(previousSession(next, lagSessions));
        window.setNextReleaseAt(closeCutoff(next, bufferMinutes));
        return window;
    }

    /**
     * 参数：收盘缓冲分钟（默认 10）
     */
    public int closeBufferMinutes()
    {
        return intConfig(OptionAccessConstants.CONFIG_CLOSE_BUFFER_MINUTES,
                OptionAccessConstants.DEFAULT_CLOSE_BUFFER_MINUTES);
    }

    /**
     * 参数：盘后版滞后交易日数（默认 1）
     */
    public int delayedLagSessions()
    {
        return intConfig(OptionAccessConstants.CONFIG_DELAYED_LAG_SESSIONS,
                OptionAccessConstants.DEFAULT_DELAYED_LAG_SESSIONS);
    }

    // ------------------------------------------------------------------ 内部

    private OptionMarketDay exception(LocalDate day)
    {
        Map<LocalDate, OptionMarketDay> year = yearLoader != null ? yearLoader.apply(day.getYear())
                : calendarService.selectYearExceptions(day.getYear());
        return year == null ? null : year.getOrDefault(day, null);
    }

    private int intConfig(String key, int defaultValue)
    {
        if (configService == null)
        {
            return defaultValue;
        }
        String value = configService.selectConfigByKey(key);
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        try
        {
            int parsed = Integer.parseInt(value.trim());
            return parsed < 0 ? defaultValue : parsed;
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private static LocalTime parseTime(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        return LocalTime.parse(value.trim().length() == 5 ? value.trim() + ":00" : value.trim());
    }

    /**
     * 空日历（测试用）
     */
    public static Map<LocalDate, OptionMarketDay> noExceptions(Integer year)
    {
        return Collections.emptyMap();
    }
}
