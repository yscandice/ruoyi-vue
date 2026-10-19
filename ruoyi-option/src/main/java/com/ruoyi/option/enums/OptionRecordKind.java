package com.ruoyi.option.enums;

/**
 * OSM「战绩」菜单数据类型（页面 /member/scorecard），一个数据接口对应一张表
 * 
 * @author yscandice
 */
public enum OptionRecordKind
{
    TRADE("trade", "战绩明细", "option_record_trade"),

    DAILY("daily", "每日结算净额", "option_record_daily"),

    SYMBOL("symbol", "按标的", "option_record_symbol"),

    STAT("stat", "战绩统计", "option_record_stat"),

    ACCOUNT("account", "账户模拟", "option_record_account");

    private final String key;

    private final String label;

    private final String tableName;

    OptionRecordKind(String key, String label, String tableName)
    {
        this.key = key;
        this.label = label;
        this.tableName = tableName;
    }

    public String getKey()
    {
        return key;
    }

    public String getLabel()
    {
        return label;
    }

    public String getTableName()
    {
        return tableName;
    }

    /**
     * 根据类型标识查找
     * 
     * @param key 标识，如 trade
     * @return 枚举，未找到返回 null
     */
    public static OptionRecordKind of(String key)
    {
        for (OptionRecordKind item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }
}
