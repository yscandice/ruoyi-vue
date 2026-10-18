package com.ruoyi.option.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.common.exception.ServiceException;

/**
 * 期权 feed JSON 工具：保持字段顺序与数值精度
 * 
 * @author yscandice
 */
public class OptionJson
{
    /** 浮点数按 BigDecimal 读取，保证写回时数值不变 */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private OptionJson()
    {
    }

    /**
     * 解析 JSON
     * 
     * @param text JSON 文本
     * @return 节点
     */
    public static JsonNode read(String text)
    {
        try
        {
            return MAPPER.readTree(text);
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException("JSON 解析失败：" + e.getOriginalMessage());
        }
    }

    /**
     * 序列化为紧凑 JSON
     * 
     * @param value 对象
     * @return JSON 文本
     */
    public static String write(Object value)
    {
        try
        {
            return MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException("JSON 序列化失败：" + e.getOriginalMessage());
        }
    }

    /**
     * 计算 SHA-256（十六进制）
     * 
     * @param text 文本
     * @return 64 位哈希
     */
    public static String sha256(String text)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 生成弱 ETag
     * 
     * @param text 内容
     * @return ETag
     */
    public static String etag(String text)
    {
        return "W/\"" + sha256(text).substring(0, 24) + "\"";
    }
}
