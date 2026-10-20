package com.ruoyi.option.service.impl;

import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.option.domain.OptionReleaseWindow;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * 会员数据权限 服务层实现
 * 
 * @author yscandice
 */
@Service
public class OptionAccessServiceImpl implements IOptionAccessService
{
    @Autowired
    private OptionMarketCalendar marketCalendar;

    @Override
    public OptionReleaseWindow resolveReleaseWindow(ZonedDateTime at)
    {
        return marketCalendar.releaseWindow(at == null ? ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE) : at);
    }
}
