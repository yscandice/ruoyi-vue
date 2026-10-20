package com.ruoyi.option.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ruoyi.option.interceptor.OptionAccessInterceptor;

/**
 * 注册会员数据权限拦截器
 * 
 * @author yscandice
 */
@Configuration
public class OptionWebMvcConfig implements WebMvcConfigurer
{
    @Autowired
    private OptionAccessInterceptor accessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(accessInterceptor).addPathPatterns("/api/member/**")
                .excludePathPatterns("/api/member/register/**", "/api/member/access");
    }
}
