package com.ruoyi.core.service;

import java.util.List;
import com.ruoyi.core.domain.CoreNavigation;

/** AgentDock 导航服务接口 */
public interface ICoreNavigationService
{
    List<CoreNavigation> selectNavigationList(CoreNavigation navigation);
    List<CoreNavigation> selectEnabledNavigationList();
    CoreNavigation selectNavigationById(Long navigationId);
    int insertNavigation(CoreNavigation navigation);
    int updateNavigation(CoreNavigation navigation);
    int updateNavigationStatus(CoreNavigation navigation);
    int deleteNavigationByIds(Long[] navigationIds);
}
