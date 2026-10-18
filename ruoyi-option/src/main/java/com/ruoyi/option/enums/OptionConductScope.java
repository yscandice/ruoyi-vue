package com.ruoyi.option.enums;

/**
 * OSM「进行」菜单栏目（页面 /member?view=active 的信号范围筛选），一个栏目对应一张表
 * 
 * @author yscandice
 */
public enum OptionConductScope
{
    ACTIVE("active", "进行中", "option_conduct_active"),
    PROTECTION("protection", "保护窗口", "option_conduct_protection"),
    MANAGE("manage", "动态管理", "option_conduct_manage"),
    EXPIRED("expired", "已到期", "option_conduct_expired"),
    HISTORY("history", "历史信号", "option_conduct_history");

    private final String key;

    private final String label;

    private final String tableName;

    OptionConductScope(String key, String label, String tableName)
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
     * 根据栏目标识查找
     * 
     * @param key 栏目标识，如 active
     * @return 枚举，未找到返回 null
     */
    public static OptionConductScope of(String key)
    {
        for (OptionConductScope item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }
}
