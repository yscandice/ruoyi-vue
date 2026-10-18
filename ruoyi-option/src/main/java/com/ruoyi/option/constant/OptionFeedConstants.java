package com.ruoyi.option.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 期权 feed 同步常量
 * 
 * @author yscandice
 */
public class OptionFeedConstants
{
    /** OSM 会话 Cookie（与旧接口共用） */
    public static final String COOKIE_KEY = "option:login:cookie";

    /** 当前生效的 feed 版本元数据 */
    public static final String CURRENT_KEY = "option:feed:current";

    /** feed 数据前缀：option:feed:data:{syncId}:{group} */
    public static final String DATA_KEY_PREFIX = "option:feed:data:";

    /** 同步状态 */
    public static final String SYNC_STATUS_KEY = "option:feed:sync:status";

    /** 同步互斥锁 */
    public static final String SYNC_LOCK_KEY = "option:feed:sync:lock";

    /** Redis 未命中回填锁 */
    public static final String REBUILD_LOCK_KEY = "option:feed:rebuild:lock";

    /** 完整 feed 的分组名 */
    public static final String GROUP_FULL = "full";

    /** 未识别字段的分组名 */
    public static final String GROUP_EXTRA = "extra";

    /** 数据 key 过期时间（秒） */
    public static final long DATA_TTL_SECONDS = 24 * 60 * 60;

    /** 旧版本数据在新版本发布后保留的时间（秒） */
    public static final long OLD_DATA_TTL_SECONDS = 120;

    /** 同步锁过期时间（秒） */
    public static final long SYNC_LOCK_SECONDS = 120;

    /** Cookie 最长保存时间（秒），上游 Max-Age 为 14 天 */
    public static final int MAX_COOKIE_TTL_SECONDS = 14 * 24 * 60 * 60;

    /** 交易日历：节假日配置键（逗号分隔 yyyy-MM-dd） */
    public static final String HOLIDAY_CONFIG_KEY = "option.market.holidays";

    /** 上游接口路径 */
    public static final String PATH_LOGIN = "/api/member/login";

    public static final String PATH_FEED = "/api/member/feed";

    /** 状态：正常 */
    public static final String STATE_OK = "OK";

    /** 状态：暂停（会话失效，需要手动重新登录） */
    public static final String STATE_PAUSED = "PAUSED";

    /** 状态：异常 */
    public static final String STATE_ERROR = "ERROR";

    /** 只是时间戳变化、不参与内容哈希的数组条目字段（存入 volatile_data 原地更新） */
    public static final Set<String> VOLATILE_ITEM_FIELDS = Set.of("lastUpdatedAtUtc");

    /** 按后缀识别的易变字段：*UpdatedAt（时间戳）、*AgeSeconds（每次轮询递增的计数） */
    public static final List<String> VOLATILE_ITEM_SUFFIXES = List.of("UpdatedAt", "AgeSeconds");

    /**
     * 是否为易变字段
     * 
     * @param field 条目字段名
     * @return 结果
     */
    public static boolean isVolatileItemField(String field)
    {
        if (VOLATILE_ITEM_FIELDS.contains(field))
        {
            return true;
        }
        for (String suffix : VOLATILE_ITEM_SUFFIXES)
        {
            if (field.endsWith(suffix))
            {
                return true;
            }
        }
        return false;
    }

    /** 前端分组：分组名 -> 顶层字段 */
    public static final Map<String, List<String>> GROUPS;

    static
    {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("meta", List.of("source_id", "status", "mode", "schema_version", "generated_at", "polledAt",
                "serverReceivedAt", "ageSeconds", "etDay", "marketEtDay", "marketPhase", "usingPriorTradingDay",
                "historyFingerprint"));
        groups.put("desk", List.of("todayDesk", "narrativeSignal", "brain", "publicIntel"));
        groups.put("signal", List.of("signals", "buyerSignals", "buyerAlerts", "signalGroups", "signalNews",
                "voiceFeed"));
        groups.put("management", List.of("managementCards", "protections", "sentProtections", "midtermShadowCards",
                "midtermSettledCards", "buyerLifecycle", "buyerExpiryCloses", "buyerTimedExits", "withdrawnCards"));
        groups.put("settlement", List.of("settlementCards", "orderIntents"));
        groups.put("history", List.of("history", "events", "performance", "scan"));
        groups.put("health", List.of("sourceHealth", "systemStatus", "terminalHealth"));
        groups.put("session", List.of("access"));
        GROUPS = Collections.unmodifiableMap(groups);
    }

    /**
     * 查询字段所属分组
     * 
     * @param field 顶层字段
     * @return 分组名
     */
    public static String groupOf(String field)
    {
        for (Map.Entry<String, List<String>> entry : GROUPS.entrySet())
        {
            if (entry.getValue().contains(field))
            {
                return entry.getKey();
            }
        }
        return GROUP_EXTRA;
    }
}
