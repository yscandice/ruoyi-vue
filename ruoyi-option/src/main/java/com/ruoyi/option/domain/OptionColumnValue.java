package com.ruoyi.option.domain;

/**
 * 动态摘要列（列名只来自 {@link com.ruoyi.option.enums.OptionResource}）
 * 
 * @author yscandice
 */
public class OptionColumnValue
{
    /** 列名 */
    private String column;

    /** 值 */
    private String value;

    public OptionColumnValue()
    {
    }

    public OptionColumnValue(String column, String value)
    {
        this.column = column;
        this.value = value;
    }

    public String getColumn()
    {
        return column;
    }

    public void setColumn(String column)
    {
        this.column = column;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }
}
