package com.ruoyi.option.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.option.domain.OptionTodaySnapshot;
import com.ruoyi.option.mapper.OptionTodaySnapshotMapper;
import com.ruoyi.option.service.IOptionTodayService;

@Service
public class OptionTodayServiceImpl implements IOptionTodayService
{
    @Autowired private OptionTodaySnapshotMapper snapshotMapper;

    @Override
    public OptionTodaySnapshot getSnapshot(String resourceKey, Long userId, String businessDate)
    {
        return snapshotMapper.selectSnapshot(resourceKey, userId, businessDate);
    }

    @Override
    public int saveSnapshot(OptionTodaySnapshot snapshot)
    {
        return snapshotMapper.upsertSnapshot(snapshot);
    }
}
