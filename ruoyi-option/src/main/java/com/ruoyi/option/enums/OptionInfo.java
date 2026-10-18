package com.ruoyi.option.enums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OSM「信息」菜单数据源（盘前简报 / 财报解读 / 札记 / 信息流 / 推荐信息）
 * 
 * <pre>
 * JSON  ：会员 JSON 接口，整份响应存一行（biz_key = -）
 * PAGE  ：页面路由（RSC 内嵌组件 props），按业务键（日期 / 财报ID）一行，biz_key = - 为列表索引
 * BUNDLE：札记列表随页面脚本下发，按札记ID一行，biz_key = - 为列表索引
 * columns：摘要列（列名, JSON Pointer, 最大长度）；以 #size 结尾表示数组长度
 * </pre>
 * 
 * @author yscandice
 */
public enum OptionInfo
{
    INFO_FEED("info-feed", Source.JSON, "/api/member/info-feed", null, null, "option_info_feed",
            cols("item_count", "/items#size", 16, "unread", "/unread", 16, "latest_key", "/items/0/key", 64,
                    "latest_title", "/items/0/title", 200, "latest_day", "/items/0/day", 16)),
    REFERRALS("referrals", Source.JSON, "/api/member/referrals", null, null, "option_info_referral",
            cols("reward_mode", "/referral/rewardMode", 32, "registered_count", "/referral/registeredCount", 16,
                    "paid_count", "/referral/paidCount", 16, "invited_count", "/referral/invited#size", 16,
                    "coupon_count", "/referral/coupons#size", 16)),
    PREMARKET("premarket", Source.PAGE, "/member/premarket", "d", "brief", "option_info_premarket",
            cols("trade_date", "/brief/date", 16, "date_label", "/brief/dateLabel", 32, "headline",
                    "/brief/headline", 500, "access_level", "/access", 16, "block_count", "/brief/blocks#size", 16,
                    "story_count", "/brief/stories#size", 16)),
    EARNINGS("earnings", Source.PAGE, "/member/earnings", "id", "piece", "option_info_earnings",
            cols("ticker", "/piece/ticker", 16, "company", "/piece/company", 64, "period", "/piece/period", 32,
                    "report_date", "/piece/reportDate", 16, "session", "/piece/session", 16, "headline",
                    "/piece/headline", 500)),
    NOTES("notes", Source.BUNDLE, "/member/notes", "id", "noteId", "option_info_note",
            cols("kind", "/kind", 32, "title", "/title", 200, "note_date", "/date", 16, "summary", "/summary", 500,
                    "image_url", "/image", 255, "cover_url", "/cover", 255));

    /** 数据来源 */
    public enum Source
    {
        /** 会员 JSON 接口 */
        JSON,
        /** 页面路由 RSC */
        PAGE,
        /** 页面脚本 */
        BUNDLE
    }

    /** 摘要列定义 */
    public record Column(String name, String pointer, int maxLength)
    {
    }

    /** 列表索引 / 不区分业务键时使用的 biz_key */
    public static final String INDEX_KEY = "-";

    private final String key;

    private final Source source;

    private final String path;

    private final String param;

    private final String marker;

    private final String tableName;

    private final List<Column> columns;

    OptionInfo(String key, Source source, String path, String param, String marker, String tableName,
            List<Column> columns)
    {
        this.key = key;
        this.source = source;
        this.path = path;
        this.param = param;
        this.marker = marker;
        this.tableName = tableName;
        this.columns = columns;
    }

    public String getKey()
    {
        return key;
    }

    public Source getSource()
    {
        return source;
    }

    public String getPath()
    {
        return path;
    }

    /** 页面查询参数名（d / id） */
    public String getParam()
    {
        return param;
    }

    /** 页面组件 props 中标识数据的字段名 */
    public String getMarker()
    {
        return marker;
    }

    public String getTableName()
    {
        return tableName;
    }

    public List<Column> getColumns()
    {
        return columns;
    }

    /**
     * 根据数据源名查找
     * 
     * @param key 数据源名，如 earnings
     * @return 枚举，未找到返回 null
     */
    public static OptionInfo of(String key)
    {
        for (OptionInfo item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }

    private static List<Column> cols(Object... triples)
    {
        List<Column> list = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 3)
        {
            list.add(new Column((String) triples[i], (String) triples[i + 1], (Integer) triples[i + 2]));
        }
        return Collections.unmodifiableList(list);
    }
}
