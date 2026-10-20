package com.ruoyi.web.controller.option;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
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
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.option.domain.OptionMemberRegisterLog;
import com.ruoyi.option.domain.vo.OptionMemberVo;
import com.ruoyi.option.service.IOptionMemberAdminService;

/**
 * 会员管理（后台，技术文档 9.3.2）
 * 
 * 会员即挂在「会员」部门、角色 option_member 的 sys_user；身份按 3.1 规则实时计算。
 * 任何修改都会删除身份缓存，会员下一次请求立即按新身份返回；停用、重置密码同时强制下线。
 * 本期不提供物理删除，只能停用（保留注册和风控记录）。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/option/member")
public class OptionMemberAdminController extends BaseController
{
    @Autowired
    private IOptionMemberAdminService memberAdminService;

    /**
     * 会员列表
     */
    @PreAuthorize("@ss.hasPermi('option:member:list')")
    @GetMapping("/list")
    public TableDataInfo list(OptionMemberVo query)
    {
        startPage();
        List<OptionMemberVo> list = memberAdminService.selectMemberList(query);
        return getDataTable(list);
    }

    /**
     * 统计卡：总数、今日注册、体验期中、盘后版、付费、停用，近 7 天每天注册数
     */
    @PreAuthorize("@ss.hasPermi('option:member:list')")
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        return success(memberAdminService.selectMemberStats());
    }

    /**
     * 导出会员
     */
    @PreAuthorize("@ss.hasPermi('option:member:export')")
    @Log(title = "会员管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, OptionMemberVo query)
    {
        List<OptionMemberVo> list = memberAdminService.selectMemberList(query);
        ExcelUtil<OptionMemberVo> util = new ExcelUtil<>(OptionMemberVo.class);
        util.exportExcel(response, list, "会员数据");
    }

    /**
     * 注册日志
     */
    @PreAuthorize("@ss.hasPermi('option:member:log')")
    @GetMapping("/registerLog/list")
    public TableDataInfo registerLogList(OptionMemberRegisterLog query)
    {
        startPage();
        List<OptionMemberRegisterLog> list = memberAdminService.selectRegisterLogList(query);
        return getDataTable(list);
    }

    /**
     * 会员详情
     */
    @PreAuthorize("@ss.hasPermi('option:member:query')")
    @GetMapping("/{userId}")
    public AjaxResult getInfo(@PathVariable("userId") Long userId)
    {
        return success(memberAdminService.selectMemberDetail(userId));
    }

    /**
     * 延长 / 缩短体验期 { extendHours, remark }
     */
    @PreAuthorize("@ss.hasPermi('option:member:edit')")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/trial")
    public AjaxResult trial(@PathVariable("userId") Long userId, @RequestBody TrialBody body)
    {
        return toAjax(memberAdminService.extendTrial(List.of(userId), body.getExtendHours(), body.getRemark()));
    }

    /**
     * 批量延长体验期 { userIds, extendHours, remark }
     */
    @PreAuthorize("@ss.hasPermi('option:member:edit')")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE)
    @PutMapping("/trial/batch")
    public AjaxResult trialBatch(@RequestBody TrialBody body)
    {
        int rows = memberAdminService.extendTrial(body.getUserIds(), body.getExtendHours(), body.getRemark());
        return success().put("count", rows);
    }

    /**
     * 设置付费到期 { paidUntil }，null 表示取消付费
     */
    @PreAuthorize("@ss.hasPermi('option:member:edit')")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/paid")
    public AjaxResult paid(@PathVariable("userId") Long userId, @RequestBody Map<String, Object> body)
    {
        return toAjax(memberAdminService.updatePaidUntil(userId, body));
    }

    /**
     * 启用 / 停用 { status }，停用时强制下线
     */
    @PreAuthorize("@ss.hasPermi('option:member:edit')")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/status")
    public AjaxResult status(@PathVariable("userId") Long userId, @RequestBody Map<String, String> body)
    {
        return toAjax(memberAdminService.updateStatus(userId, body == null ? null : body.get("status")));
    }

    /**
     * 重置密码 { password }，并强制下线
     */
    @PreAuthorize("@ss.hasPermi('option:member:resetPwd')")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE, excludeParamNames = { "password" })
    @PutMapping("/{userId}/resetPwd")
    public AjaxResult resetPwd(@PathVariable("userId") Long userId, @RequestBody Map<String, String> body)
    {
        return toAjax(memberAdminService.resetPwd(userId, body == null ? null : body.get("password")));
    }

    /**
     * 强制下线
     */
    @PreAuthorize("@ss.hasPermi('option:member:forceLogout')")
    @Log(title = "会员管理", businessType = BusinessType.FORCE)
    @DeleteMapping("/{userId}/online")
    public AjaxResult forceLogout(@PathVariable("userId") Long userId)
    {
        return success().put("count", memberAdminService.forceLogout(userId));
    }

    /**
     * 体验期调整参数
     */
    public static class TrialBody
    {
        private List<Long> userIds;

        private Integer extendHours;

        private String remark;

        public List<Long> getUserIds()
        {
            return userIds;
        }

        public void setUserIds(List<Long> userIds)
        {
            this.userIds = userIds;
        }

        public Integer getExtendHours()
        {
            return extendHours;
        }

        public void setExtendHours(Integer extendHours)
        {
            this.extendHours = extendHours;
        }

        public String getRemark()
        {
            return remark;
        }

        public void setRemark(String remark)
        {
            this.remark = remark;
        }
    }
}
