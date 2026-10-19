package com.ruoyi.option.enums;

/**
 * OSM「结算」菜单数据类型（页面 /member/settlement），一个数据接口对应一张表
 * 
 * @author yscandice
 */
public enum OptionSettlementKind
{
    RECORD("record", "已结算", "option_settlement_record"),
    CARD("card", "结算单", "option_settlement_card"),
    STAT("stat", "结算统计", "option_settlement_stat");

    private final String key;

    private final String label;

    private final String tableName;

    OptionSettlementKind(String key, String label, String tableName)
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
     * @param key 标识，如 record
     * @return 枚举，未找到返回 null
     */
    public static OptionSettlementKind of(String key)
    {
        for (OptionSettlementKind item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }
}
