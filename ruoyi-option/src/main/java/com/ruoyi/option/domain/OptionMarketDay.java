package com.ruoyi.option.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 美股交易日历（例外日） option_market_calendar
 * 
 * @author yscandice
 */
public class OptionMarketDay extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日期（美东） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date tradeDate;

    /** 是否交易日 1是 0否 */
    private String trading;

    /** 开盘时间（美东） */
    private String openTime;

    /** 收盘时间（美东），提前收盘日为 13:00 */
    private String closeTime;

    /** 是否提前收盘 1是 0否 */
    private String earlyClose;

    /** 节假日名称 */
    private String holidayName;

    public void setTradeDate(Date tradeDate)
    {
        this.tradeDate = tradeDate;
    }

    public Date getTradeDate()
    {
        return tradeDate;
    }

    public void setTrading(String trading)
    {
        this.trading = trading;
    }

    public String getTrading()
    {
        return trading;
    }

    public void setOpenTime(String openTime)
    {
        this.openTime = openTime;
    }

    public String getOpenTime()
    {
        return openTime;
    }

    public void setCloseTime(String closeTime)
    {
        this.closeTime = closeTime;
    }

    public String getCloseTime()
    {
        return closeTime;
    }

    public void setEarlyClose(String earlyClose)
    {
        this.earlyClose = earlyClose;
    }

    public String getEarlyClose()
    {
        return earlyClose;
    }

    public void setHolidayName(String holidayName)
    {
        this.holidayName = holidayName;
    }

    public String getHolidayName()
    {
        return holidayName;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("tradeDate", getTradeDate())
            .append("trading", getTrading())
            .append("openTime", getOpenTime())
            .append("closeTime", getCloseTime())
            .append("earlyClose", getEarlyClose())
            .append("holidayName", getHolidayName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
