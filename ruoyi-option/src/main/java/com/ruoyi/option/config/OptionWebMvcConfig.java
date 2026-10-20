package com.ruoyi.option.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.interceptor.OptionAccessInterceptor;
import com.ruoyi.option.interceptor.OptionMemberSystemInterceptor;

/**
 * 注册会员数据权限拦截器、会员访问若依系统接口的白名单拦截器
 * 
 * @author yscandice
 */
@Configuration
public class OptionWebMvcConfig implements WebMvcConfigurer
{
    @Autowired
    private OptionAccessInterceptor accessInterceptor;

    @Autowired
    private OptionMemberSystemInterceptor memberSystemInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(accessInterceptor).addPathPatterns(OptionAccessConstants.ACCESS_PATH_PATTERN)
                .excludePathPatterns(OptionAccessConstants.ACCESS_EXCLUDE_PATTERNS);
        // 会员只放行改密码、上传头像，其余若依系统接口 403（技术文档 7.3.5）
        registry.addInterceptor(memberSystemInterceptor).addPathPatterns("/system/**");
    }
}
