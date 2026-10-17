package com.ruoyi.core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.core.domain.CoreNavigation;

/** AgentDock 导航数据层 */
@Mapper
public interface CoreNavigationMapper
{
    List<CoreNavigation> selectNavigationList(CoreNavigation navigation);
    List<CoreNavigation> selectEnabledNavigationList();
    CoreNavigation selectNavigationById(Long navigationId);
    CoreNavigation selectNavigationByKey(String menuKey);
    int insertNavigation(CoreNavigation navigation);
    int updateNavigation(CoreNavigation navigation);
    int updateNavigationStatus(CoreNavigation navigation);
    int deleteNavigationById(Long navigationId);
}
