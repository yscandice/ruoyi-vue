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

    /** Redis：会员身份计算结果 option:member:access:{userId} */
    public static final String MEMBER_ACCESS_KEY = "option:member:access:";

    /** 会员身份缓存最长时长（秒），并且不超过体验期 / 付费期剩余时长 */
    public static final int MEMBER_ACCESS_TTL_SECONDS = 300;

    /** 若依角色：超级管理员 */
    public static final String ROLE_ADMIN = "admin";

    /** 若依角色：期权内部人员 */
    public static final String ROLE_STAFF = "option_staff";

    /** 若依角色：网站会员 */
    public static final String ROLE_MEMBER = "option_member";

    /** 策略：盘后版看收盘冻结数据 */
    public static final String MODE_CLOSE_SNAPSHOT = "CLOSE_SNAPSHOT";

    /** 默认值：冻结数据保留 30 天 */
    public static final int DEFAULT_SNAPSHOT_KEEP_DAYS = 30;

    /** Redis：盘后视图前缀，option:conduct:active → option:delayed:conduct:active */
    public static final String DELAYED_PREFIX = "option:delayed:";

    /** Redis：放出时的临时前缀，全部写完后 RENAME 为正式前缀 */
    public static final String DELAYED_TMP_PREFIX = "option:delayed:tmp:";

    /** Redis：冻结任务锁（冻结 / 放出 / 恢复共用） */
    public static final String FREEZE_LOCK_KEY = "option:access:freeze:lock";

    /** 冻结任务锁时长（分钟） */
    public static final int FREEZE_LOCK_MINUTES = 10;

    /** Redis：最近一次冻结状态 */
    public static final String FREEZE_STATUS_KEY = "option:access:freeze:status";

    /** Redis：当前放出的冻结记录 {tradeDate, snapshotId, releasedAt} */
    public static final String RELEASE_CURRENT_KEY = "option:access:release:current";

    /** 冻结结果：成功 / 部分成功 / 失败 */
    public static final String RESULT_SUCCESS = "SUCCESS";

    public static final String RESULT_PARTIAL = "PARTIAL";

    public static final String RESULT_FAILED = "FAILED";

    /** 放出状态：已冻结未放出 / 已放出 / 已被后续放出替代 */
    public static final String STATUS_FROZEN = "FROZEN";

    public static final String STATUS_RELEASED = "RELEASED";

    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 触发方式：定时任务 / 手动 */
    public static final String TRIGGER_JOB = "JOB";

    public static final String TRIGGER_MANUAL = "MANUAL";

    /** feed 的当前批次指针与批次数据前缀（冻结时只取当前批次） */
    public static final String FEED_PREFIX = "option:feed:";

    public static final String FEED_CURRENT_KEY = "option:feed:current";

    public static final String FEED_DATA_PREFIX = "option:feed:data:";

    /** 冻结时跳过的键：同步状态、锁等运行键，以及同步账号的个人数据（推荐码） */
    public static final String[] FREEZE_SKIP_SEGMENTS = { ":sync:", ":rebuild:" };

    public static final String[] FREEZE_SKIP_SUFFIXES = { ":lock" };

    public static final String[] FREEZE_SKIP_PREFIXES = { "option:info:referrals" };

    /** 各菜单同步状态键后缀：{前缀}sync:status */
    public static final String SYNC_STATUS_SUFFIX = "sync:status";

    private OptionAccessConstants()
    {
    }

    /** 数据权限拦截路径 */
    public static final String ACCESS_PATH_PATTERN = "/api/member/**";

    /** 数据权限拦截排除路径（注册、当前身份） */
    public static final String[] ACCESS_EXCLUDE_PATTERNS = { "/api/member/register/**", "/api/member/access" };
}
