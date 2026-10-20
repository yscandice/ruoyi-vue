package com.ruoyi.web.controller.option;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.service.IOptionAccessService;

/**
 * 网站会员 注册与身份（/api/member/*）
 * 
 * 注册、验证码接口在步骤 8 补充。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/api/member")
public class OptionMemberController extends BaseController
{
    @Autowired
    private IOptionAccessService accessService;

    /**
     * 当前身份（登录后访问）：身份、体验期、盘后数据交易日与放出时间、各菜单视图（技术文档 7.1.3）
     */
    @GetMapping("/access")
    public AjaxResult access()
    {
        Map<String, Object> data = accessService.selectAccessInfo(getUserId());
        if (OptionAccessLevel.DISABLED.getCode().equals(data.get("level")))
        {
            AjaxResult ajax = AjaxResult.error(HttpStatus.FORBIDDEN, "会员账号已停用，请联系客服");
            ajax.put(AjaxResult.DATA_TAG, data);
            return ajax;
        }
        return success(data);
    }
}
