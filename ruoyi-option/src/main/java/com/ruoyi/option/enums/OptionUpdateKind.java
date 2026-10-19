package com.ruoyi.option.enums;

/**
 * OSM「更新」菜单数据类型（页面 /member/updates「功能更新」），一个数据接口对应一张表
 * 
 * 页面调用的会员 API 中只有 /api/member/updates（阅读进度 seenSeq/currentSeq/unseen，已由 option_res_update 同步），
 * 更新文章列表随页面路由服务端渲染下发，同步时抓取页面 HTML 解析为结构化数据
 * 
 * @author yscandice
 */
public enum OptionUpdateKind
{
    PAGE("page", "页头", "option_update_page"),

    ENTRY("entry", "更新条目", "option_update_entry");

    private final String key;

    private final String label;

    private final String tableName;

    OptionUpdateKind(String key, String label, String tableName)
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
     * @param key 标识，如 entry
     * @return 枚举，未找到返回 null
     */
    public static OptionUpdateKind of(String key)
    {
        for (OptionUpdateKind item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }
}
