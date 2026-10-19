package com.ruoyi.option.enums;

/**
 * OSM「跟踪」菜单标记类型（页面 /member/tracks 的四类标记），一个标记接口对应一张表
 * 
 * @author yscandice
 */
public enum OptionFollowKind
{
    SIGNAL("signal", "卖方信号", "seller_signal", "option_follow_signal"),
    MIDTERM("midterm", "中期信号", "midterm_signal", "option_follow_midterm"),
    BUYER("buyer", "买方信号", "buyer_signal", "option_follow_buyer"),
    MANAGE("manage", "动态管理", "dynamic_management", "option_follow_manage");

    private final String key;

    private final String label;

    private final String cardKind;

    private final String tableName;

    OptionFollowKind(String key, String label, String cardKind, String tableName)
    {
        this.key = key;
        this.label = label;
        this.cardKind = cardKind;
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

    public String getCardKind()
    {
        return cardKind;
    }

    public String getTableName()
    {
        return tableName;
    }

    /**
     * 根据标记类型标识查找
     * 
     * @param key 标识，如 signal
     * @return 枚举，未找到返回 null
     */
    public static OptionFollowKind of(String key)
    {
        for (OptionFollowKind item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }
}
