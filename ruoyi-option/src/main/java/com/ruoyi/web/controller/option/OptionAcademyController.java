package com.ruoyi.web.controller.option;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.option.domain.OptionAcademyItem;
import com.ruoyi.option.domain.OptionAcademySync;
import com.ruoyi.option.service.IOptionAcademyService;

/**
 * OSM「学堂」菜单
 * 
 * 前端只读（Redis，带 ETag）：
 *   GET /api/member/academy                    汇总（书目、课程清单、最新一期问答、各类型条数、同步状态）
 *   GET /api/member/academy/{kind}             类型列表（page 栏目页 / lesson 课程 / qa 一周问答 / glossary 术语速查），
 *                                              可选 keyword 关键字过滤；列表不含正文 HTML
 *   GET /api/member/academy/{kind}/{key}       单条完整内容（含正文 HTML 与纯文本），
 *                                              如 lesson/workbench、qa/1、glossary/卖方价差、page/home
 * 管理：
 *   POST /option/sync/academy                  立即同步
 *   GET  /option/sync/academy/list             同步流水
 *   GET  /option/sync/academy/{kind}/items     条目版本
 *   GET  /option/sync/academy/{kind}/items/{versionId}  条目版本详情（含内容）
 * 
 * @author yscandice
 */
@RestController
public class OptionAcademyController extends BaseController
{
    @Autowired
    private IOptionAcademyService academyService;

    /**
     * 学堂汇总
     */
    @GetMapping("/api/member/academy")
    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(academyService.getSummary(), request, response);
    }

    /**
     * 类型列表
     */
    @GetMapping("/api/member/academy/{kind}")
    public void kind(@PathVariable("kind") String kind,
            @RequestParam(value = "keyword", required = false) String keyword, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(academyService.getKind(kind, keyword), request, response);
    }

    /**
     * 单条完整内容
     */
    @GetMapping("/api/member/academy/{kind}/{key}")
    public void item(@PathVariable("kind") String kind, @PathVariable("key") String key,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionResourceController.write(academyService.getItem(kind, key), request, response);
    }

    /**
     * 立即同步学堂菜单
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "学堂菜单同步", businessType = BusinessType.OTHER)
    @PostMapping("/option/sync/academy")
    public AjaxResult sync()
    {
        return success(academyService.sync(true, "MANUAL", SecurityUtils.getUsername()));
    }

    /**
     * 同步流水
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/academy/list")
    public TableDataInfo list(OptionAcademySync sync)
    {
        startPage();
        List<OptionAcademySync> list = academyService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 条目版本列表
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/academy/{kind}/items")
    public TableDataInfo items(@PathVariable("kind") String kind, OptionAcademyItem item)
    {
        startPage();
        List<OptionAcademyItem> list = academyService.selectItemList(kind, item);
        return getDataTable(list);
    }

    /**
     * 条目版本详情
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/option/sync/academy/{kind}/items/{versionId}")
    public AjaxResult version(@PathVariable("kind") String kind, @PathVariable("versionId") Long versionId)
    {
        return success(academyService.selectItem(kind, versionId));
    }
}
