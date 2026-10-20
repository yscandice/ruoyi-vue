package com.ruoyi.option.enums;

/**
 * 会员接口的数据视图，见技术文档 3.5、3.6
 * 
 * @author yscandice
 */
public enum OptionDataView
{
    /** 实时视图：读取同步任务写入的实时 Redis 键 */
    REALTIME("REALTIME", "实时"),

    /** 盘后视图：读取收盘冻结后放出的 option:delayed:* 键 */
    DELAYED("DELAYED", "盘后"),

    /** 拒绝访问 */
    DENY("DENY", "拒绝"),

    /** 本人数据（如「设置」），不走同步数据 */
    SELF("SELF", "本人数据");

    private final String code;

    private final String label;

    OptionDataView(String code, String label)
    {
        this.code = code;
        this.label = label;
    }

    public String getCode()
    {
        return code;
    }

    public String getLabel()
    {
        return label;
    }

    /**
     * 根据视图代码查找
     * 
     * @param code 视图代码
     * @return 视图，未匹配返回 null
     */
    public static OptionDataView of(String code)
    {
        for (OptionDataView view : values())
        {
            if (view.code.equalsIgnoreCase(code))
            {
                return view;
            }
        }
        return null;
    }
}
