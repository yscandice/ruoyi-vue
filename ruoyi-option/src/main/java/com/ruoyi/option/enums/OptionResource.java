package com.ruoyi.option.enums;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OSM 会员接口资源（feed 之外的 7 个接口）
 * 
 * columns：表中的摘要列 -> 取值路径（JSON Pointer；以 #size 结尾表示数组长度）
 * 
 * @author yscandice
 */
public enum OptionResource
{
    SELLER_GUIDE("seller-guide", "/api/member/seller-guide", "option_res_seller_guide", SyncWindow.ANY,
            cols("completed_version", "/completedVersion", "current_version", "/currentVersion", "pending", "/pending",
                    "completed_at", "/completedAt")),
    ENTITLEMENT("entitlement", "/api/member/entitlement", "option_res_entitlement", SyncWindow.ANY,
            cols("tier", "/access/tier", "plan_code", "/access/planCode", "subscription_status",
                    "/access/subscriptionStatus", "valid_until", "/access/validUntil")),
    DECISIONS("decisions", "/api/member/decisions", "option_res_decision", SyncWindow.TRADING,
            cols("latest_count", "/latest#size", "event_count", "/events#size")),
    MANAGEMENT_CHOICES("management-choices", "/api/member/management-choices", "option_res_management_choice",
            SyncWindow.TRADING, cols("latest_count", "/latest#size", "event_count", "/events#size")),
    PREMARKET("premarket", "/api/member/premarket", "option_res_premarket", SyncWindow.PREMARKET,
            cols("latest", "/latest", "received_at", "/receivedAt", "access_level", "/access")),
    UPDATES("updates", "/api/member/updates", "option_res_update", SyncWindow.ANY,
            cols("seen_seq", "/seenSeq", "current_seq", "/currentSeq", "unseen", "/unseen")),
    SUPPORT("support", "/api/member/support", "option_res_support", SyncWindow.ANY,
            cols("mode", "/mode", "unread", "/unread", "message_count", "/messages#size"));

    /** 同步时段 */
    public enum SyncWindow
    {
        /** 任意时间 */
        ANY,
        /** 美东交易时段 09:30-16:05 */
        TRADING,
        /** 美东盘前 04:00-09:30 */
        PREMARKET
    }

    /** 不区分业务键时使用的 biz_key */
    public static final String DEFAULT_BIZ_KEY = "-";

    private final String key;

    private final String path;

    private final String tableName;

    private final SyncWindow window;

    private final Map<String, String> columns;

    OptionResource(String key, String path, String tableName, SyncWindow window, Map<String, String> columns)
    {
        this.key = key;
        this.path = path;
        this.tableName = tableName;
        this.window = window;
        this.columns = columns;
    }

    public String getKey()
    {
        return key;
    }

    public String getPath()
    {
        return path;
    }

    public String getTableName()
    {
        return tableName;
    }

    public SyncWindow getWindow()
    {
        return window;
    }

    public Map<String, String> getColumns()
    {
        return columns;
    }

    /**
     * 是否按日期区分（premarket?d=）
     */
    public boolean isDated()
    {
        return this == PREMARKET;
    }

    /**
     * 根据接口名查找
     * 
     * @param key 接口名，如 seller-guide
     * @return 枚举，未找到返回 null
     */
    public static OptionResource of(String key)
    {
        for (OptionResource item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }

    private static Map<String, String> cols(String... pairs)
    {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
        {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
