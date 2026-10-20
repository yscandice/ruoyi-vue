package com.ruoyi.option.domain;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * 盘后视图放出窗口：某个时间点盘后版用户看到的交易日、放出时间与下次放出时间（技术文档 3.2）
 * 
 * <pre>
 * releaseTradeDate R = 最近一个“收盘冻结点 ≤ 当前时间”的交易日（R 收盘时冻结 R、放出 R-lag）
 * dataTradeDate    D = R 往前数 lag 个交易日（lag = 0 时 D = R）
 * dataAsOf           = close(D)，releasedAt = close(R)，nextReleaseAt = close(R 的下一个交易日)
 * </pre>
 * 时间字段对外统一输出 ISO 8601（带时区偏移），另附北京时间，便于核对。
 * 
 * @author yscandice
 */
public class OptionReleaseWindow
{
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final DateTimeFormatter BEIJING = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 计算时间点 */
    private ZonedDateTime at;

    /** 滞后交易日数 */
    private int lagSessions;

    /** 收盘缓冲分钟 */
    private int closeBufferMinutes;

    /** 计算时间点（美东日期）是否交易日 */
    private boolean tradingDay;

    /** 计算时间点是否处于盘中（开盘至收盘） */
    private boolean marketOpen;

    /** 最近一次冻结的交易日 R（R 收盘时冻结 R 的数据并放出 D） */
    private LocalDate releaseTradeDate;

    /** 盘后版用户看到的数据交易日 D */
    private LocalDate dataTradeDate;

    /** 数据截止时间 close(D) */
    private ZonedDateTime dataAsOf;

    /** 这份数据的放出时间 close(R) */
    private ZonedDateTime releasedAt;

    /** 下一次放出的交易日（该交易日收盘后放出） */
    private LocalDate nextReleaseTradeDate;

    /** 下一次放出时放出的数据交易日 */
    private LocalDate nextDataTradeDate;

    /** 下一次放出时间 close(下一个交易日) */
    private ZonedDateTime nextReleaseAt;

    @JsonIgnore
    public ZonedDateTime getAtTime()
    {
        return at;
    }

    public void setAt(ZonedDateTime at)
    {
        this.at = at;
    }

    public String getAt()
    {
        return iso(at);
    }

    public String getAtEt()
    {
        return iso(at == null ? null : at.withZoneSameInstant(OptionMarketCalendar.MARKET_ZONE));
    }

    public String getAtBeijing()
    {
        return beijing(at);
    }

    public int getLagSessions()
    {
        return lagSessions;
    }

    public void setLagSessions(int lagSessions)
    {
        this.lagSessions = lagSessions;
    }

    public int getCloseBufferMinutes()
    {
        return closeBufferMinutes;
    }

    public void setCloseBufferMinutes(int closeBufferMinutes)
    {
        this.closeBufferMinutes = closeBufferMinutes;
    }

    public boolean isTradingDay()
    {
        return tradingDay;
    }

    public void setTradingDay(boolean tradingDay)
    {
        this.tradingDay = tradingDay;
    }

    public boolean isMarketOpen()
    {
        return marketOpen;
    }

    public void setMarketOpen(boolean marketOpen)
    {
        this.marketOpen = marketOpen;
    }

    public LocalDate getReleaseTradeDate()
    {
        return releaseTradeDate;
    }

    public void setReleaseTradeDate(LocalDate releaseTradeDate)
    {
        this.releaseTradeDate = releaseTradeDate;
    }

    public LocalDate getDataTradeDate()
    {
        return dataTradeDate;
    }

    public void setDataTradeDate(LocalDate dataTradeDate)
    {
        this.dataTradeDate = dataTradeDate;
    }

    @JsonIgnore
    public ZonedDateTime getDataAsOfTime()
    {
        return dataAsOf;
    }

    public void setDataAsOf(ZonedDateTime dataAsOf)
    {
        this.dataAsOf = dataAsOf;
    }

    public String getDataAsOf()
    {
        return iso(dataAsOf);
    }

    public String getDataAsOfBeijing()
    {
        return beijing(dataAsOf);
    }

    @JsonIgnore
    public ZonedDateTime getReleasedAtTime()
    {
        return releasedAt;
    }

    public void setReleasedAt(ZonedDateTime releasedAt)
    {
        this.releasedAt = releasedAt;
    }

    public String getReleasedAt()
    {
        return iso(releasedAt);
    }

    public String getReleasedAtBeijing()
    {
        return beijing(releasedAt);
    }

    public LocalDate getNextReleaseTradeDate()
    {
        return nextReleaseTradeDate;
    }

    public void setNextReleaseTradeDate(LocalDate nextReleaseTradeDate)
    {
        this.nextReleaseTradeDate = nextReleaseTradeDate;
    }

    public LocalDate getNextDataTradeDate()
    {
        return nextDataTradeDate;
    }

    public void setNextDataTradeDate(LocalDate nextDataTradeDate)
    {
        this.nextDataTradeDate = nextDataTradeDate;
    }

    @JsonIgnore
    public ZonedDateTime getNextReleaseAtTime()
    {
        return nextReleaseAt;
    }

    public void setNextReleaseAt(ZonedDateTime nextReleaseAt)
    {
        this.nextReleaseAt = nextReleaseAt;
    }

    public String getNextReleaseAt()
    {
        return iso(nextReleaseAt);
    }

    public String getNextReleaseAtBeijing()
    {
        return beijing(nextReleaseAt);
    }

    private static String iso(ZonedDateTime time)
    {
        return time == null ? null : time.format(ISO);
    }

    private static String beijing(ZonedDateTime time)
    {
        return time == null ? null : time.withZoneSameInstant(OptionMarketCalendar.BEIJING_ZONE).format(BEIJING);
    }
}
