package com.ruoyi.option.enums;

/**
 * 会员身份（access level），按优先级从高到低判断，见技术文档 3.1
 * 
 * @author yscandice
 */
public enum OptionAccessLevel
{
    STAFF("STAFF", "内部人员", OptionDataView.REALTIME),

    DISABLED("DISABLED", "停用", OptionDataView.DENY),

    PAID("PAID", "付费", OptionDataView.REALTIME),

    TRIAL("TRIAL", "体验期", OptionDataView.REALTIME),

    DELAYED("DELAYED", "盘后版", OptionDataView.DELAYED);

    private final String code;

    private final String label;

    /** 该身份默认看到的数据视图（具体菜单还要叠加 option_access_policy 策略） */
    private final OptionDataView defaultView;

    OptionAccessLevel(String code, String label, OptionDataView defaultView)
    {
        this.code = code;
        this.label = label;
        this.defaultView = defaultView;
    }

    public String getCode()
    {
        return code;
    }

    public String getLabel()
    {
        return label;
    }

    public OptionDataView getDefaultView()
    {
        return defaultView;
    }

    /**
     * 是否看实时数据（体验期 / 付费 / 内部人员）
     */
    public boolean isRealtime()
    {
        return defaultView == OptionDataView.REALTIME;
    }

    /**
     * 根据身份代码查找
     * 
     * @param code 身份代码
     * @return 身份，未匹配返回 null
     */
    public static OptionAccessLevel of(String code)
    {
        for (OptionAccessLevel level : values())
        {
            if (level.code.equalsIgnoreCase(code))
            {
                return level;
            }
        }
        return null;
    }
}
