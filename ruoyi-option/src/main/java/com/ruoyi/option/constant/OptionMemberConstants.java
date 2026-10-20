package com.ruoyi.option.constant;

/**
 * 会员注册与防滥用常量（技术文档 6.2、5.10、5.11）
 * 
 * @author yscandice
 */
public class OptionMemberConstants
{
    private OptionMemberConstants()
    {
    }

    /** 请求过于频繁（若依 HttpStatus 未定义 429） */
    public static final int TOO_MANY_REQUESTS = 429;

    /** 参数：是否开放会员注册 */
    public static final String CONFIG_REGISTER_ENABLED = "option.member.registerEnabled";

    /** 参数：同一 IP 每天最多注册数 */
    public static final String CONFIG_REGISTER_IP_DAILY_LIMIT = "option.member.registerIpDailyLimit";

    /** 参数：禁止注册的邮箱域名（英文逗号分隔） */
    public static final String CONFIG_BLOCKED_EMAIL_DOMAINS = "option.member.blockedEmailDomains";

    /** 同一 IP 每天最多注册数默认值 */
    public static final int DEFAULT_REGISTER_IP_DAILY_LIMIT = 3;

    /** 注册邮箱验证码（存哈希与错误次数），10 分钟 */
    public static final String CODE_KEY = "option:member:code:";

    /** 重发间隔，60 秒 */
    public static final String CODE_LIMIT_KEY = "option:member:code:limit:";

    /** 防重复提交注册 */
    public static final String REGISTER_LOCK_KEY = "option:member:register:lock:";

    /** 验证码有效期（秒） */
    public static final int CODE_EXPIRE_SECONDS = 600;

    /** 重发间隔（秒） */
    public static final int CODE_RESEND_SECONDS = 60;

    /** 验证码最多校验错误次数 */
    public static final int CODE_MAX_FAILS = 5;

    /** 验证码位数 */
    public static final int CODE_LENGTH = 6;

    /** 同一邮箱每天最多发码次数 */
    public static final int CODE_EMAIL_DAILY_LIMIT = 10;

    /** 同一 IP 每天最多发码次数 */
    public static final int CODE_IP_DAILY_LIMIT = 20;

    /** 同一设备只给一次体验期的天数 */
    public static final int DEVICE_TRIAL_DAYS = 30;

    /** 邮箱最大长度（sys_user.user_name / email 为 50） */
    public static final int EMAIL_MAX_LENGTH = 50;

    /** 密码长度 */
    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PASSWORD_MAX_LENGTH = 20;

    /** 注册日志动作 */
    public static final String ACTION_SEND_CODE = "SEND_CODE";

    public static final String ACTION_REGISTER = "REGISTER";

    /** 注册日志结果 */
    public static final String RESULT_SUCCESS = "SUCCESS";

    public static final String RESULT_REJECTED = "REJECTED";

    public static final String RESULT_FAILED = "FAILED";

    /** 参数：通知偏好默认值（JSON，技术文档 4.2） */
    public static final String CONFIG_NOTIFY_DEFAULTS = "option.settings.notifyDefaults";

    /** 设置缓存（资料扩展、通知偏好、阅读进度），30 分钟，保存时删除 */
    public static final String SETTINGS_KEY = "option:member:settings:";

    public static final int SETTINGS_EXPIRE_MINUTES = 30;

    /** 通知开关（字段名沿用 OSM notifications.prefs，顺序即返回顺序） */
    public static final String[] NOTIFY_KEYS = { "new_signal", "protection_window", "take_profit_window", "settlement",
            "weekly_scorecard", "product_update", "silent_reminder", "management_stop", "manage_adopted_only" };

    /** 默认关闭的通知开关（参数缺失或格式错误时使用） */
    public static final String[] NOTIFY_DEFAULT_OFF = { "silent_reminder", "management_stop", "manage_adopted_only" };

    /** 策略偏好白名单 */
    public static final String[] STRATEGY_KEYS = { "seller", "midterm", "buyer" };

    /** 默认开启的策略偏好 */
    public static final String[] STRATEGY_DEFAULT_ON = { "seller", "midterm" };

    /** 关注标的：大写字母、数字、点，1–10 位 */
    public static final String WATCH_SYMBOL_PATTERN = "^[A-Z0-9.]{1,10}$";

    /** 关注标的最多个数 */
    public static final int WATCH_SYMBOL_MAX = 50;

    /** 显示名称长度（sys_user.nick_name） */
    public static final int DISPLAY_NAME_MAX_LENGTH = 30;

    /** 最近访问时间写库间隔：每个用户最多 5 分钟写一次 */
    public static final String LAST_ACCESS_KEY = "option:member:lastAccess:";

    public static final int LAST_ACCESS_INTERVAL_MINUTES = 5;
}
