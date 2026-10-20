package com.ruoyi.option.interceptor;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.option.utils.OptionAccessContext;

/**
 * 给会员接口的 AjaxResult 增加顶层字段 access（技术文档 3.5），没有数据权限上下文时不做任何改动
 * 
 * 原样输出 OSM JSON 的兼容接口（feed、today 资源）不是 AjaxResult，只通过响应头标识视图。
 * 
 * @author yscandice
 */
@RestControllerAdvice
public class OptionAccessResponseAdvice implements ResponseBodyAdvice<Object>
{
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType)
    {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response)
    {
        OptionAccessContext context = OptionAccessContext.get();
        if (context != null && body instanceof AjaxResult result && !result.containsKey("access"))
        {
            result.put("access", context.toAccessMap());
        }
        return body;
    }
}
