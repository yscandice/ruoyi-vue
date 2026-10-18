package com.ruoyi.option.utils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 美股交易时段判断（America/New_York）
 * 
 * 交易日 09:30 至 16:05（含收盘后补拉一次），周末与 sys_config 中配置的节假日不同步。
 * 
 * @author yscandice
 */
@Component
public class OptionMarketCalendar
{
    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private static final LocalTime OPEN = LocalTime.of(9, 30);

    private static final LocalTime CLOSE_WITH_FINAL_PULL = LocalTime.of(16, 5);

    @Autowired
    private ISysConfigService configService;

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
     * 指定时间是否处于 feed 同步时段
     * 
     * @param time 时间（任意时区）
     * @return 结果
     */
    public boolean isFeedSyncWindow(ZonedDateTime time)
    {
        ZonedDateTime et = time.withZoneSameInstant(MARKET_ZONE);
        DayOfWeek day = et.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || isHoliday(et))
        {
            return false;
        }
        LocalTime now = et.toLocalTime();
        return !now.isBefore(OPEN) && now.isBefore(CLOSE_WITH_FINAL_PULL);
    }

    private boolean isHoliday(ZonedDateTime et)
    {
        String holidays = configService.selectConfigByKey(OptionFeedConstants.HOLIDAY_CONFIG_KEY);
        if (StringUtils.isEmpty(holidays))
        {
            return false;
        }
        String today = et.toLocalDate().toString();
        return Arrays.stream(holidays.split(",")).map(String::trim).anyMatch(today::equals);
    }
}
