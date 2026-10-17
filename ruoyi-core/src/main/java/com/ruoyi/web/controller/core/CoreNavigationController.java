package com.ruoyi.web.controller.core;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.core.domain.CoreNavigation;
import com.ruoyi.core.service.ICoreNavigationService;

/** AgentDock 左侧导航管理 */
@RestController
@RequestMapping("/core/navigation")
public class CoreNavigationController extends BaseController
{
    private static final Pattern MENU_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,49}$");

    @Autowired
    private ICoreNavigationService navigationService;

    /** 网站获取当前启用的导航菜单 */
    @Anonymous
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<CoreNavigation> list = navigationService.selectEnabledNavigationList();
        return success(list);
    }

    /** 后台分页查询导航菜单 */
    @PreAuthorize("@ss.hasPermi('tool:navigation:list')")
    @GetMapping("/admin/list")
    public TableDataInfo adminList(CoreNavigation navigation)
    {
        startPage();
        List<CoreNavigation> list = navigationService.selectNavigationList(navigation);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('tool:navigation:query')")
    @GetMapping("/{navigationId}")
    public AjaxResult getInfo(@PathVariable Long navigationId)
    {
        return success(navigationService.selectNavigationById(navigationId));
    }

    @Log(title = "站点导航菜单", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('tool:navigation:add')")
    @PostMapping
    public AjaxResult add(@RequestBody CoreNavigation navigation)
    {
        String message = validateNavigation(navigation, false);
        if (message != null)
        {
            return error(message);
        }
        navigation.setCreateBy(getUsername());
        navigation.setStatus(StringUtils.isEmpty(navigation.getStatus()) ? "0" : navigation.getStatus());
        return toAjax(navigationService.insertNavigation(navigation));
    }

    @Log(title = "站点导航菜单", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('tool:navigation:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody CoreNavigation navigation)
    {
        String message = validateNavigation(navigation, true);
        if (message != null)
        {
            return error(message);
        }
        navigation.setUpdateBy(getUsername());
        return toAjax(navigationService.updateNavigation(navigation));
    }

    @Log(title = "站点导航菜单", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('tool:navigation:edit')")
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody CoreNavigation navigation)
    {
        if (navigation.getNavigationId() == null || !isValidStatus(navigation.getStatus()))
        {
            return error("菜单 ID 或状态值无效");
        }
        navigation.setUpdateBy(getUsername());
        return toAjax(navigationService.updateNavigationStatus(navigation));
    }

    @Log(title = "站点导航菜单", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('tool:navigation:remove')")
    @DeleteMapping("/{navigationIds}")
    public AjaxResult remove(@PathVariable Long[] navigationIds)
    {
        return toAjax(navigationService.deleteNavigationByIds(navigationIds));
    }

    private String validateNavigation(CoreNavigation navigation, boolean requireId)
    {
        if (navigation == null || (requireId && navigation.getNavigationId() == null))
        {
            return "菜单信息或菜单 ID 不能为空";
        }
        if (StringUtils.isEmpty(navigation.getMenuKey()) || !MENU_KEY_PATTERN.matcher(navigation.getMenuKey()).matches())
        {
            return "菜单标识须以字母开头，且仅能包含字母、数字、下划线和连字符（最多50位）";
        }
        if (StringUtils.isEmpty(navigation.getMenuName()) || navigation.getMenuName().trim().isEmpty() || navigation.getMenuName().trim().length() > 80)
        {
            return "菜单名称不能为空且不能超过80个字符";
        }
        if (StringUtils.isEmpty(navigation.getIcon()) || navigation.getIcon().trim().isEmpty() || navigation.getIcon().trim().length() > 80)
        {
            return "菜单图标不能为空且不能超过80个字符";
        }
        if (StringUtils.isEmpty(navigation.getRoutePath()) || navigation.getRoutePath().trim().isEmpty() || navigation.getRoutePath().trim().length() > 255)
        {
            return "菜单路由不能为空且不能超过255个字符";
        }
        if (navigation.getOrderNum() == null || navigation.getOrderNum() < 0)
        {
            return "菜单排序必须为大于等于0的整数";
        }
        if ((requireId && navigation.getStatus() == null) || (navigation.getStatus() != null && !isValidStatus(navigation.getStatus())))
        {
            return "菜单状态值无效";
        }
        navigation.setMenuKey(navigation.getMenuKey().trim());
        navigation.setMenuName(navigation.getMenuName().trim());
        navigation.setIcon(navigation.getIcon().trim());
        navigation.setRoutePath(navigation.getRoutePath().trim());
        if (StringUtils.isEmpty(navigation.getMenuType()))
        {
            navigation.setMenuType("menu");
        }
        if (!"menu".equals(navigation.getMenuType()) && !"button".equals(navigation.getMenuType()))
        {
            return "菜单类型只能是 menu 或 button";
        }
        return null;
    }

    private boolean isValidStatus(String status)
    {
        return "0".equals(status) || "1".equals(status);
    }
}
