package com.ruoyi.core.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.core.domain.CoreNavigation;
import com.ruoyi.core.mapper.CoreNavigationMapper;
import com.ruoyi.core.service.ICoreNavigationService;

/** AgentDock 导航服务 */
@Service
public class CoreNavigationServiceImpl implements ICoreNavigationService
{
    @Autowired
    private CoreNavigationMapper navigationMapper;

    @Override
    public List<CoreNavigation> selectNavigationList(CoreNavigation navigation)
    {
        return navigationMapper.selectNavigationList(navigation);
    }

    @Override
    public List<CoreNavigation> selectEnabledNavigationList()
    {
        return navigationMapper.selectEnabledNavigationList();
    }

    @Override
    public CoreNavigation selectNavigationById(Long navigationId)
    {
        return navigationMapper.selectNavigationById(navigationId);
    }

    @Override
    public int insertNavigation(CoreNavigation navigation)
    {
        if (navigationMapper.selectNavigationByKey(navigation.getMenuKey()) != null)
        {
            throw new ServiceException("菜单标识已存在");
        }
        return navigationMapper.insertNavigation(navigation);
    }

    @Override
    public int updateNavigation(CoreNavigation navigation)
    {
        CoreNavigation existing = navigationMapper.selectNavigationByKey(navigation.getMenuKey());
        if (existing != null && !existing.getNavigationId().equals(navigation.getNavigationId()))
        {
            throw new ServiceException("菜单标识已存在");
        }
        return navigationMapper.updateNavigation(navigation);
    }

    @Override
    public int updateNavigationStatus(CoreNavigation navigation)
    {
        return navigationMapper.updateNavigationStatus(navigation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteNavigationByIds(Long[] navigationIds)
    {
        int rows = 0;
        for (Long navigationId : navigationIds)
        {
            rows += navigationMapper.deleteNavigationById(navigationId);
        }
        return rows;
    }
}
