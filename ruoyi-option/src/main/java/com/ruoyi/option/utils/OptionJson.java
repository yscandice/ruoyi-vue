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

    /**
     * 按原 JSON 生成同结构的空数据（盘后视图读不到时 delayedFallback = EMPTY 使用）：
     * 对象保留字段、数组置空、字符串置空串、数字置 0、布尔置 false，不带任何实际数据
     * 
     * @param text 原 JSON 文本，为空时返回 {}
     * @return 空结构 JSON
     */
    public static String emptyStructure(String text)
    {
        if (text == null || text.isBlank())
        {
            return "{}";
        }
        return write(emptyNode(read(text)));
    }

    private static JsonNode emptyNode(JsonNode node)
    {
        JsonNodeFactory factory = MAPPER.getNodeFactory();
        if (node == null || node.isNull())
        {
            return factory.nullNode();
        }
        if (node.isObject())
        {
            com.fasterxml.jackson.databind.node.ObjectNode result = factory.objectNode();
            node.properties().forEach(entry -> result.set(entry.getKey(), emptyNode(entry.getValue())));
            return result;
        }
        if (node.isArray())
        {
            return factory.arrayNode();
        }
        if (node.isNumber())
        {
            return factory.numberNode(0);
        }
        if (node.isBoolean())
        {
            return factory.booleanNode(false);
        }
        return factory.textNode("");
    }
}
