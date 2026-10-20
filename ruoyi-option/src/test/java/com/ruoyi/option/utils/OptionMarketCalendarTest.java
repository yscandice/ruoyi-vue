package com.ruoyi.option.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.option.domain.OptionMarketDay;
import com.ruoyi.option.domain.OptionReleaseWindow;

/**
 * 交易日历与盘后放出窗口计算（技术文档 3.2、5.3、11）
 * 
 * 日历数据直接解析 sql/ry_option_access.sql 的初始化语句，测试的就是上线用的那份数据。
 * 
 * @author yscandice
 */
class OptionMarketCalendarTest
{
    private static final int BUFFER = 10;

    private static OptionMarketCalendar calendar;

    @BeforeAll
    static void loadCalendar() throws IOException
    {
        Path sql = Path.of("..", "sql", "ry_option_access.sql");
        String text = Files.readString(sql, StandardCharsets.UTF_8);
        Pattern row = Pattern.compile(
                "union all select '(\\d{4}-\\d{2}-\\d{2})', '([01])', (null|'[\\d:]+'), (null|'[\\d:]+'), '([01])', '([^']*)'");
        Map<Integer, Map<LocalDate, OptionMarketDay>> years = new HashMap<>();
        Matcher m = row.matcher(text);
        int count = 0;
        while (m.find())
        {
            OptionMarketDay day = new OptionMarketDay();
            day.setTradeDate(LocalDate.parse(m.group(1)));
            day.setTrading(m.group(2));
            day.setOpenTime(unquote(m.group(3)));
            day.setCloseTime(unquote(m.group(4)));
            day.setEarlyClose(m.group(5));
            day.setHolidayName(m.group(6));
            years.computeIfAbsent(day.getTradeDate().getYear(), y -> new HashMap<>()).put(day.getTradeDate(), day);
            count++;
        }
        assertEquals(37, count, "初始化脚本应有 2025–2027 年 31 个休市日 + 6 个提前收盘日");
        calendar = OptionMarketCalendar.of(y -> years.getOrDefault(y, Map.of()));
    }

    private static String unquote(String v)
    {
        return "null".equals(v) ? null : v.substring(1, v.length() - 1);
    }

    private static LocalDate d(String date)
    {
        return LocalDate.parse(date);
    }

    private static ZonedDateTime et(String dateTime)
    {
        return java.time.LocalDateTime.parse(dateTime).atZone(OptionMarketCalendar.MARKET_ZONE);
    }

    private static String beijing(ZonedDateTime time)
    {
        return time.withZoneSameInstant(OptionMarketCalendar.BEIJING_ZONE).toLocalDateTime().toString();
    }

    // ------------------------------------------------------------------ 节假日

    @Test
    @DisplayName("节假日、补休、周末不是交易日；普通工作日是交易日")
    void holidays()
    {
        assertFalse(calendar.isTradingDay(d("2026-11-26")), "感恩节");
        assertFalse(calendar.isTradingDay(d("2026-07-03")), "独立日补休（7/4 是周六）");
        assertFalse(calendar.isTradingDay(d("2027-12-24")), "圣诞节补休（12/25 是周六）");
        assertFalse(calendar.isTradingDay(d("2025-01-09")), "2025 全国哀悼日");
        assertFalse(calendar.isTradingDay(d("2026-10-24")), "周六");
        assertFalse(calendar.isTradingDay(d("2026-10-25")), "周日");
        assertTrue(calendar.isTradingDay(d("2026-07-02")));
        assertTrue(calendar.isTradingDay(d("2026-10-20")));
        assertTrue(calendar.isTradingDay(d("2027-12-31")), "2028 元旦是周六，NYSE 不在 12/31 补休");
    }

    @Test
    @DisplayName("previousSession / nextSession 跳过周末、节假日，并能跨年")
    void sessions()
    {
        assertEquals(d("2026-10-16"), calendar.previousSession(d("2026-10-19"), 1), "周一的上一个交易日是上周五");
        assertEquals(d("2026-11-25"), calendar.previousSession(d("2026-11-27"), 1), "感恩节次日的上一个交易日是周三");
        assertEquals(d("2026-01-16"), calendar.previousSession(d("2026-01-20"), 1), "MLK 周一休市");
        assertEquals(d("2025-12-31"), calendar.previousSession(d("2026-01-02"), 1), "跨年，元旦休市");
        assertEquals(d("2026-10-15"), calendar.previousSession(d("2026-10-19"), 2));
        assertEquals(d("2026-10-19"), calendar.previousSession(d("2026-10-19"), 0), "n = 0 返回当天");
        assertEquals(d("2026-12-28"), calendar.nextSession(d("2026-12-24")), "圣诞节 + 周末");
        assertEquals(d("2026-11-27"), calendar.nextSession(d("2026-11-25")), "跳过感恩节");
        assertEquals(d("2027-01-04"), calendar.nextSession(d("2026-12-31")), "跨年，元旦 + 周末");
        assertThrows(ServiceException.class, () -> calendar.previousSession(d("2026-10-19"), -1));
    }

    // ------------------------------------------------------------------ 提前收盘

    @Test
    @DisplayName("提前收盘日 13:00 收盘，冻结点 13:10；feed 同步到 13:05")
    void earlyClose()
    {
        assertTrue(calendar.isEarlyClose(d("2026-11-27")));
        assertEquals(LocalTime.of(13, 0), calendar.closeTime(d("2026-11-27")));
        assertEquals(LocalTime.of(16, 0), calendar.closeTime(d("2026-11-25")));
        assertEquals(et("2026-11-27T13:10"), calendar.closeCutoff(d("2026-11-27"), BUFFER));
        assertTrue(calendar.isFeedSyncWindow(et("2026-11-27T13:04")));
        assertFalse(calendar.isFeedSyncWindow(et("2026-11-27T13:06")), "提前收盘日 13:05 后停止同步");
        assertTrue(calendar.isFeedSyncWindow(et("2026-11-25T16:04")));
        assertFalse(calendar.isFeedSyncWindow(et("2026-11-25T16:05")));
        assertFalse(calendar.isFeedSyncWindow(et("2026-11-26T11:00")), "感恩节不同步");
        assertThrows(ServiceException.class, () -> calendar.closeCutoff(d("2026-11-26"), BUFFER), "休市日没有冻结点");
    }

    // ------------------------------------------------------------------ 夏令时

    @Test
    @DisplayName("夏令时：冻结点北京时间次日 04:10；冬令时：次日 05:10")
    void daylightSaving()
    {
        // 2026 年夏令时：3/8 开始，11/1 结束
        assertEquals("2026-03-07T05:10", beijing(calendar.closeCutoff(d("2026-03-06"), BUFFER)), "3/6 周五仍是冬令时");
        assertEquals("2026-03-10T04:10", beijing(calendar.closeCutoff(d("2026-03-09"), BUFFER)), "3/9 周一已是夏令时");
        assertEquals("2026-10-31T04:10", beijing(calendar.closeCutoff(d("2026-10-30"), BUFFER)), "10/30 周五仍是夏令时");
        assertEquals("2026-11-03T05:10", beijing(calendar.closeCutoff(d("2026-11-02"), BUFFER)), "11/2 周一已是冬令时");
        // 跨切换周末的放出：周五数据在周一 16:10（冬令时）放出
        OptionReleaseWindow w = calendar.releaseWindow(et("2026-11-02T17:00"), 1, BUFFER);
        assertEquals(d("2026-10-30"), w.getDataTradeDate());
        assertEquals("2026-10-31 04:10:00", w.getDataAsOfBeijing(), "数据截止仍按夏令时");
        assertEquals("2026-11-03 05:10:00", w.getReleasedAtBeijing(), "放出时间按冬令时");
        // 2027 年夏令时：3/14 开始
        assertEquals("2027-03-13T05:10", beijing(calendar.closeCutoff(d("2027-03-12"), BUFFER)));
        assertEquals("2027-03-16T04:10", beijing(calendar.closeCutoff(d("2027-03-15"), BUFFER)));
    }

    // ------------------------------------------------------------------ 技术文档 3.2 的 4 个示例

    @Test
    @DisplayName("示例 1：周二 10:00（盘中）→ 上周五数据，周一 16:10 放出，下次周二 16:10")
    void example1TuesdayIntraday()
    {
        OptionReleaseWindow w = calendar.releaseWindow(et("2026-10-20T10:00"), 1, BUFFER);
        assertEquals(d("2026-10-16"), w.getDataTradeDate());
        assertEquals(et("2026-10-19T16:10"), w.getReleasedAtTime());
        assertEquals(et("2026-10-20T16:10"), w.getNextReleaseAtTime());
        assertEquals(d("2026-10-19"), w.getNextDataTradeDate(), "下次放出周一数据");
        assertTrue(w.isMarketOpen());
        assertEquals("2026-10-16T16:10:00-04:00", w.getDataAsOf());
    }

    @Test
    @DisplayName("示例 2：周二 16:30（盘后）→ 周一数据，周二 16:10 放出，下次周三 16:10")
    void example2TuesdayAfterClose()
    {
        OptionReleaseWindow w = calendar.releaseWindow(et("2026-10-20T16:30"), 1, BUFFER);
        assertEquals(d("2026-10-19"), w.getDataTradeDate());
        assertEquals(et("2026-10-20T16:10"), w.getReleasedAtTime());
        assertEquals(et("2026-10-21T16:10"), w.getNextReleaseAtTime());
        assertEquals(d("2026-10-20"), w.getNextDataTradeDate(), "下次放出周二数据");
        assertFalse(w.isMarketOpen());
    }

    @Test
    @DisplayName("示例 3：周六 12:00 → 周四数据，周五 16:10 放出，下次周一 16:10")
    void example3Saturday()
    {
        OptionReleaseWindow w = calendar.releaseWindow(et("2026-10-24T12:00"), 1, BUFFER);
        assertEquals(d("2026-10-22"), w.getDataTradeDate());
        assertEquals(et("2026-10-23T16:10"), w.getReleasedAtTime());
        assertEquals(et("2026-10-26T16:10"), w.getNextReleaseAtTime());
        assertEquals(d("2026-10-23"), w.getNextDataTradeDate(), "下次放出周五数据");
        assertFalse(w.isTradingDay());
    }

    @Test
    @DisplayName("示例 4：感恩节次日 14:00（13:00 提前收盘）→ 周三数据，当天 13:10 放出，下次周一 16:10")
    void example4DayAfterThanksgiving()
    {
        OptionReleaseWindow w = calendar.releaseWindow(et("2026-11-27T14:00"), 1, BUFFER);
        assertEquals(d("2026-11-25"), w.getDataTradeDate(), "周四感恩节休市");
        assertEquals(et("2026-11-27T13:10"), w.getReleasedAtTime());
        assertEquals(et("2026-11-30T16:10"), w.getNextReleaseAtTime());
        assertEquals(d("2026-11-27"), w.getNextDataTradeDate(), "下次放出周五数据");
    }

    @Test
    @DisplayName("冻结点边界：16:09 还是旧数据，16:10 切换")
    void cutoffBoundary()
    {
        assertEquals(d("2026-10-16"), calendar.releaseWindow(et("2026-10-20T16:09:59"), 1, BUFFER).getDataTradeDate());
        assertEquals(d("2026-10-19"), calendar.releaseWindow(et("2026-10-20T16:10"), 1, BUFFER).getDataTradeDate());
    }

    @Test
    @DisplayName("delayedLagSessions = 0：收盘后放出当天数据")
    void lagZero()
    {
        OptionReleaseWindow w = calendar.releaseWindow(et("2026-10-20T16:30"), 0, BUFFER);
        assertEquals(d("2026-10-20"), w.getDataTradeDate());
        assertEquals(w.getReleasedAtTime(), w.getDataAsOfTime());
        assertEquals(d("2026-10-19"), calendar.releaseWindow(et("2026-10-20T10:00"), 0, BUFFER).getDataTradeDate(),
                "盘中看到上一个交易日（周一）");
    }
}
