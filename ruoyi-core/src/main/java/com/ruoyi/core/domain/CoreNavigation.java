package com.ruoyi.core.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** AgentDock 左侧导航项 */
public class CoreNavigation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long navigationId;
    private String menuKey;
    private String menuName;
    private String icon;
    private String routePath;
    private Integer orderNum;
    private String menuType;
    private String status;

    public Long getNavigationId() { return navigationId; }
    public void setNavigationId(Long navigationId) { this.navigationId = navigationId; }
    public String getMenuKey() { return menuKey; }
    public void setMenuKey(String menuKey) { this.menuKey = menuKey; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public String getMenuType() { return menuType; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
