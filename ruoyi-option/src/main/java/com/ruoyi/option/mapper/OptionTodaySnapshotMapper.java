package com.ruoyi.option.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.option.domain.OptionTodaySnapshot;

@Mapper
public interface OptionTodaySnapshotMapper
{
    OptionTodaySnapshot selectSnapshot(@Param("resourceKey") String resourceKey,
            @Param("userId") Long userId, @Param("businessDate") String businessDate);
    int upsertSnapshot(OptionTodaySnapshot snapshot);
}
