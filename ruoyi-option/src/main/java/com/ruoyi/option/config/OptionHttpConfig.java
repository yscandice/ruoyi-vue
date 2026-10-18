package com.ruoyi.option.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * OSM 上游 HTTP 客户端配置
 * 
 * @author yscandice
 */
@Configuration
public class OptionHttpConfig
{
    /**
     * OSM 专用 RestTemplate（连接 10 秒、读取 30 秒超时）
     */
    @Bean("optionRestTemplate")
    public RestTemplate optionRestTemplate()
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}
