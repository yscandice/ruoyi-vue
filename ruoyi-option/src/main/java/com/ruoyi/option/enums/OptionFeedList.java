package com.ruoyi.option.enums;

import java.util.List;

/**
 * feed 中按条目拆分存储的数组字段
 * 
 * @author yscandice
 */
public enum OptionFeedList
{
    HISTORY("history", "option_feed_history", "id"),
    MANAGEMENT_CARDS("managementCards", "option_feed_management", "id"),
    PROTECTIONS("protections", "option_feed_protection", "id"),
    SENT_PROTECTIONS("sentProtections", "option_feed_protection", "id"),
    SETTLEMENT_CARDS("settlementCards", "option_feed_settlement", "signal_id"),
    ORDER_INTENTS("orderIntents", "option_feed_order_intent", "signal_id"),
    MIDTERM_SHADOW_CARDS("midtermShadowCards", "option_feed_midterm", "id"),
    MIDTERM_SETTLED_CARDS("midtermSettledCards", "option_feed_midterm", "id"),
    SIGNALS("signals", "option_feed_signal", "id"),
    BUYER_SIGNALS("buyerSignals", "option_feed_signal", "id"),
    BUYER_ALERTS("buyerAlerts", "option_feed_signal", "id"),
    EVENTS("events", "option_feed_event", null);

    /** 可查询列的候选来源字段（按优先级） */
    public static final List<String> SYMBOL_FIELDS = List.of("symbol");

    public static final List<String> SIGNAL_FIELDS = List.of("signalId", "signal_id");

    public static final List<String> STATE_FIELDS = List.of("state", "status");

    public static final List<String> KIND_FIELDS = List.of("kind", "category", "tier", "event_type");

    public static final List<String> DATE_FIELDS = List.of("etDate", "openedEtDay", "expiryDate", "expiry",
            "occurred_at", "emitted_at", "firstConfirmedAtEt");

    /** feed 中的字段名，同时作为 list_type 存储 */
    private final String field;

    /** 存储表名 */
    private final String tableName;

    /** 条目主键字段，为空时使用内容哈希 */
    private final String idField;

    OptionFeedList(String field, String tableName, String idField)
    {
        this.field = field;
        this.tableName = tableName;
        this.idField = idField;
    }

    public String getField()
    {
        return field;
    }

    public String getTableName()
    {
        return tableName;
    }

    public String getIdField()
    {
        return idField;
    }

    /**
     * 根据 feed 字段名查找
     * 
     * @param field 字段名
     * @return 枚举，未找到返回 null
     */
    public static OptionFeedList ofField(String field)
    {
        for (OptionFeedList item : values())
        {
            if (item.field.equals(field))
            {
                return item;
            }
        }
        return null;
    }
}
