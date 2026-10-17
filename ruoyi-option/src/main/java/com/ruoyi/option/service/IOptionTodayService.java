package com.ruoyi.option.service;

import com.ruoyi.option.domain.OptionTodaySnapshot;

public interface IOptionTodayService
{
    OptionTodaySnapshot getSnapshot(String resourceKey, Long userId, String businessDate);
    int saveSnapshot(OptionTodaySnapshot snapshot);
}
