package com.ruoyi.option.enums;

/**
 * OSM「学堂」菜单数据类型（页面 /academy），一个数据接口对应一张表
 * 
 * 学堂没有专属 JSON 接口，课程正文随页面路由服务端渲染下发，同步时抓取页面 HTML 解析为结构化数据
 * 
 * @author yscandice
 */
public enum OptionAcademyKind
{
    PAGE("page", "栏目页", "option_academy_page"),

    LESSON("lesson", "课程", "option_academy_lesson"),

    QA("qa", "一周问答", "option_academy_qa"),

    GLOSSARY("glossary", "术语速查", "option_academy_glossary");

    private final String key;

    private final String label;

    private final String tableName;

    OptionAcademyKind(String key, String label, String tableName)
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
     * @param key 标识，如 lesson
     * @return 枚举，未找到返回 null
     */
    public static OptionAcademyKind of(String key)
    {
        for (OptionAcademyKind item : values())
        {
            if (item.key.equals(key))
            {
                return item;
            }
        }
        return null;
    }
}
