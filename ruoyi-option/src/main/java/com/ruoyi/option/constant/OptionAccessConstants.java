package com.ruoyi.option.constant;

/**
 * 会员数据权限（体验期 / 盘后可见）常量：参数键、Redis 键
 * 
 * @author yscandice
 */
public class OptionAccessConstants
{
    /** 参数：体验时长（小时） */
    public static final String CONFIG_TRIAL_HOURS = "option.access.trialHours";

    /** 参数：盘后版滞后的交易日数 */
    public static final String CONFIG_DELAYED_LAG_SESSIONS = "option.access.delayedLagSessions";

    /** 参数：收盘后多少分钟冻结并放出 */
    public static final String CONFIG_CLOSE_BUFFER_MINUTES = "option.access.closeBufferMinutes";

    /** 参数：还没有冻结数据时的处理 EMPTY / REALTIME */
    public static final String CONFIG_DELAYED_FALLBACK = "option.access.delayedFallback";

    /** 参数：冻结数据保留天数 */
    public static final String CONFIG_SNAPSHOT_KEEP_DAYS = "option.access.snapshotKeepDays";

    /** 参数：数据权限总开关 */
    public static final String CONFIG_ENABLED = "option.access.enabled";

    /** 默认值：滞后 1 个交易日 */
    public static final int DEFAULT_DELAYED_LAG_SESSIONS = 1;

    /** 默认值：收盘后 10 分钟 */
    public static final int DEFAULT_CLOSE_BUFFER_MINUTES = 10;

    /** Redis：交易日历缓存（按年），值为当年登记的例外日 JSON */
    public static final String MARKET_CALENDAR_KEY = "option:market:calendar:";

    /** 交易日历缓存时长（小时） */
    public static final int MARKET_CALENDAR_TTL_HOURS = 24;

    private OptionAccessConstants()
    {
    }
}
