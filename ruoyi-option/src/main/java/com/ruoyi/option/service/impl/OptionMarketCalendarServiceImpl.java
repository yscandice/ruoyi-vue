package com.ruoyi.option.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.domain.OptionMarketDay;
import com.ruoyi.option.mapper.OptionMarketDayMapper;
import com.ruoyi.option.service.IOptionMarketCalendarService;

/**
 * 美股交易日历 服务层实现
 * 
 * 读取顺序：进程内缓存（60 秒）→ Redis option:market:calendar:{year}（1 天）→ 数据库；修改后两级缓存一起清除。
 * 
 * @author yscandice
 */
@Service
public class OptionMarketCalendarServiceImpl implements IOptionMarketCalendarService
{
    private static final Logger log = LoggerFactory.getLogger(OptionMarketCalendarServiceImpl.class);

    /** 进程内缓存时长（毫秒）：日历会被每个会员请求用到，避免频繁访问 Redis */
    private static final long LOCAL_TTL_MILLIS = 60_000L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<Integer, LocalEntry> localCache = new ConcurrentHashMap<>();

    @Autowired
    private OptionMarketDayMapper marketDayMapper;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<OptionMarketDay> selectOptionMarketDayList(OptionMarketDay day)
    {
        Object year = day.getParams().get("year");
        if (year != null && StringUtils.isNotEmpty(year.toString()))
        {
            int y = parseYear(year.toString());
            day.getParams().put("beginTime", y + "-01-01");
            day.getParams().put("endTime", y + "-12-31");
        }
        return marketDayMapper.selectOptionMarketDayList(day);
    }

    @Override
    public OptionMarketDay selectOptionMarketDayByDate(LocalDate tradeDate)
    {
        return marketDayMapper.selectOptionMarketDayById(tradeDate);
    }

    @Override
    public Map<LocalDate, OptionMarketDay> selectYearExceptions(int year)
    {
        LocalEntry entry = localCache.get(year);
        if (entry != null && entry.expiresAt > System.currentTimeMillis())
        {
            return entry.days;
        }
        Map<LocalDate, OptionMarketDay> days = loadYear(year);
        localCache.put(year, new LocalEntry(days, System.currentTimeMillis() + LOCAL_TTL_MILLIS));
        return days;
    }

    @Override
    public int insertOptionMarketDay(OptionMarketDay day)
    {
        normalize(day);
        if (marketDayMapper.selectOptionMarketDayById(day.getTradeDate()) != null)
        {
            throw new ServiceException("新增失败，" + day.getTradeDate() + " 已登记");
        }
        int rows = marketDayMapper.insertOptionMarketDay(day);
        clearCache(day.getTradeDate().getYear());
        return rows;
    }

    @Override
    public int updateOptionMarketDay(OptionMarketDay day)
    {
        normalize(day);
        if (marketDayMapper.selectOptionMarketDayById(day.getTradeDate()) == null)
        {
            throw new ServiceException("修改失败，" + day.getTradeDate() + " 未登记");
        }
        int rows = marketDayMapper.updateOptionMarketDay(day);
        clearCache(day.getTradeDate().getYear());
        return rows;
    }

    @Override
    public int deleteOptionMarketDayByDates(LocalDate[] tradeDates)
    {
        if (tradeDates == null || tradeDates.length == 0)
        {
            return 0;
        }
        int rows = marketDayMapper.deleteOptionMarketDayByIds(tradeDates);
        Set<Integer> years = new HashSet<>();
        for (LocalDate date : tradeDates)
        {
            years.add(date.getYear());
        }
        years.forEach(this::clearCache);
        return rows;
    }

    @Override
    public void clearCache(int year)
    {
        localCache.remove(year);
        redisCache.deleteObject(OptionAccessConstants.MARKET_CALENDAR_KEY + year);
    }

    /**
     * Redis 未命中时查库，并以 JSON 文本写回 Redis
     */
    private Map<LocalDate, OptionMarketDay> loadYear(int year)
    {
        String key = OptionAccessConstants.MARKET_CALENDAR_KEY + year;
        try
        {
            String cached = redisCache.getCacheObject(key);
            if (StringUtils.isNotEmpty(cached))
            {
                return fromJson(cached);
            }
        }
        catch (Exception e)
        {
            log.warn("读取交易日历缓存失败，改为查库：{}", e.getMessage());
        }
        List<OptionMarketDay> list = marketDayMapper.selectOptionMarketDayByRange(LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
        Map<LocalDate, OptionMarketDay> days = new LinkedHashMap<>();
        for (OptionMarketDay day : list)
        {
            days.put(day.getTradeDate(), day);
        }
        try
        {
            redisCache.setCacheObject(key, toJson(list), OptionAccessConstants.MARKET_CALENDAR_TTL_HOURS,
                    TimeUnit.HOURS);
        }
        catch (Exception e)
        {
            log.warn("写入交易日历缓存失败：{}", e.getMessage());
        }
        return Collections.unmodifiableMap(days);
    }

    private static String toJson(List<OptionMarketDay> list)
    {
        JSONArray array = new JSONArray();
        for (OptionMarketDay day : list)
        {
            JSONObject item = new JSONObject();
            item.put("tradeDate", day.getTradeDate().toString());
            item.put("trading", day.getTrading());
            item.put("openTime", day.getOpenTime());
            item.put("closeTime", day.getCloseTime());
            item.put("earlyClose", day.getEarlyClose());
            item.put("holidayName", day.getHolidayName());
            array.add(item);
        }
        return array.toJSONString();
    }

    private static Map<LocalDate, OptionMarketDay> fromJson(String text)
    {
        Map<LocalDate, OptionMarketDay> days = new LinkedHashMap<>();
        JSONArray array = JSON.parseArray(text);
        for (int i = 0; i < array.size(); i++)
        {
            JSONObject item = array.getJSONObject(i);
            OptionMarketDay day = new OptionMarketDay();
            day.setTradeDate(LocalDate.parse(item.getString("tradeDate")));
            day.setTrading(item.getString("trading"));
            day.setOpenTime(item.getString("openTime"));
            day.setCloseTime(item.getString("closeTime"));
            day.setEarlyClose(item.getString("earlyClose"));
            day.setHolidayName(item.getString("holidayName"));
            days.put(day.getTradeDate(), day);
        }
        return Collections.unmodifiableMap(days);
    }

    /**
     * 校验并规范化：休市日不能提前收盘；提前收盘日默认 13:00；时间统一 HH:mm:ss
     */
    private static void normalize(OptionMarketDay day)
    {
        if (day.getTradeDate() == null)
        {
            throw new ServiceException("日期不能为空");
        }
        String trading = StringUtils.isEmpty(day.getTrading()) ? "1" : day.getTrading();
        String early = StringUtils.isEmpty(day.getEarlyClose()) ? "0" : day.getEarlyClose();
        if (!"0".equals(trading) && !"1".equals(trading))
        {
            throw new ServiceException("是否交易日只能为 0 或 1");
        }
        if (!"0".equals(early) && !"1".equals(early))
        {
            throw new ServiceException("是否提前收盘只能为 0 或 1");
        }
        if ("0".equals(trading) && "1".equals(early))
        {
            throw new ServiceException("休市日不能设置为提前收盘");
        }
        day.setTrading(trading);
        day.setEarlyClose(early);
        day.setOpenTime(formatTime(day.getOpenTime(), "09:30:00"));
        day.setCloseTime(formatTime(day.getCloseTime(), "1".equals(early) ? "13:00:00" : "16:00:00"));
        if (!LocalTime.parse(day.getOpenTime()).isBefore(LocalTime.parse(day.getCloseTime())))
        {
            throw new ServiceException("开盘时间必须早于收盘时间");
        }
    }

    private static String formatTime(String value, String defaultValue)
    {
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        String v = value.trim();
        try
        {
            return LocalTime.parse(v.length() == 5 ? v + ":00" : v).format(TIME_FORMAT);
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("时间格式应为 HH:mm 或 HH:mm:ss：" + value);
        }
    }

    private static int parseYear(String value)
    {
        try
        {
            int year = Integer.parseInt(value.trim());
            if (year < 2000 || year > 2100)
            {
                throw new NumberFormatException();
            }
            return year;
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("年份格式错误：" + value);
        }
    }

    private static final class LocalEntry
    {
        private final Map<LocalDate, OptionMarketDay> days;

        private final long expiresAt;

        private LocalEntry(Map<LocalDate, OptionMarketDay> days, long expiresAt)
        {
            this.days = days;
            this.expiresAt = expiresAt;
        }
    }
}
