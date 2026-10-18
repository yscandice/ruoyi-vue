package com.ruoyi.option.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * OSM 页面解析：从页面 HTML 内嵌的 RSC 数据中取出页面组件 props，从页面脚本中取出札记列表
 * 
 * @author yscandice
 */
public final class OptionPageParser
{
    /** 页面内嵌 RSC 片段：self.__VINEXT_RSC_CHUNKS__.push("...") */
    private static final Pattern RSC_CHUNK = Pattern.compile("__VINEXT_RSC_CHUNKS__\\.push\\((\".*?\")\\)</script>",
            Pattern.DOTALL);

    /** RSC 行：{id}:{json} */
    private static final Pattern RSC_ROW = Pattern.compile("^[0-9a-f]+:([\\[{].*)$");

    /** 札记页面脚本 */
    private static final Pattern NOTES_BUNDLE = Pattern.compile("/assets/NotesClient-[A-Za-z0-9_-]+\\.js");

    private OptionPageParser()
    {
    }

    /**
     * 从页面 HTML 中取出含 marker 字段的组件 props（已去掉 children，并还原 RSC 转义）
     * 
     * @param html 页面 HTML
     * @param marker 组件 props 中标识数据的字段名，如 brief / piece / noteId
     * @return props，未找到返回 null
     */
    public static ObjectNode pageProps(String html, String marker)
    {
        if (StringUtils.isEmpty(html))
        {
            return null;
        }
        StringBuilder flight = new StringBuilder();
        Matcher matcher = RSC_CHUNK.matcher(html);
        while (matcher.find())
        {
            JsonNode chunk = OptionJson.read(matcher.group(1));
            flight.append(chunk.asText());
        }
        for (String line : flight.toString().split("\n"))
        {
            Matcher row = RSC_ROW.matcher(line);
            if (!row.matches())
            {
                continue;
            }
            JsonNode node;
            try
            {
                node = OptionJson.read(row.group(1));
            }
            catch (ServiceException e)
            {
                continue;
            }
            ObjectNode props = findProps(node, marker);
            if (props != null)
            {
                props.remove("children");
                return (ObjectNode) unescape(props);
            }
        }
        return null;
    }

    /**
     * 札记页面脚本路径
     * 
     * @param html 札记页面 HTML
     * @return 如 /assets/NotesClient-xxx.js，未找到返回 null
     */
    public static String notesBundlePath(String html)
    {
        Matcher matcher = NOTES_BUNDLE.matcher(StringUtils.nvl(html, ""));
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 从札记页面脚本中取出札记列表（形如 [{id:`precision`,kind:`系统哲学`,...},...] 的 JS 字面量）
     * 
     * @param script 脚本内容
     * @return 札记数组，未找到返回 null
     */
    public static ArrayNode notes(String script)
    {
        if (StringUtils.isEmpty(script))
        {
            return null;
        }
        int start = script.indexOf("[{id:");
        if (start < 0)
        {
            return null;
        }
        String json = jsLiteralToJson(script, start);
        JsonNode node = OptionJson.read(json);
        return node.isArray() ? (ArrayNode) node : null;
    }

    /**
     * 深度优先查找同时含 marker 与 memberValid 的对象
     */
    private static ObjectNode findProps(JsonNode root, String marker)
    {
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty())
        {
            JsonNode node = stack.pop();
            if (node.isObject())
            {
                if (node.has(marker) && node.has("memberValid"))
                {
                    return (ObjectNode) node;
                }
                node.elements().forEachRemaining(stack::push);
            }
            else if (node.isArray())
            {
                node.elements().forEachRemaining(stack::push);
            }
        }
        return null;
    }

    /**
     * 还原 RSC 字符串转义："$$" 开头表示字面量 "$"，"$undefined" 表示 undefined
     */
    private static JsonNode unescape(JsonNode node)
    {
        if (node.isTextual())
        {
            String text = node.asText();
            if ("$undefined".equals(text))
            {
                return JsonNodeFactory.instance.nullNode();
            }
            return text.startsWith("$$") ? TextNode.valueOf(text.substring(1)) : node;
        }
        if (node.isObject())
        {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> entry = fields.next();
                entry.setValue(unescape(entry.getValue()));
            }
            return object;
        }
        if (node.isArray())
        {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++)
            {
                array.set(i, unescape(array.get(i)));
            }
        }
        return node;
    }

    /**
     * 把从 start 开始的 JS 对象/数组字面量转为 JSON（支持未加引号的键、反引号/单双引号字符串、数字、true/false/null）
     */
    private static String jsLiteralToJson(String src, int start)
    {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        int i = start;
        while (i < src.length())
        {
            char c = src.charAt(i);
            if (c == '`' || c == '\'' || c == '"')
            {
                int end = i + 1;
                StringBuilder text = new StringBuilder();
                while (end < src.length() && src.charAt(end) != c)
                {
                    char ch = src.charAt(end);
                    if (ch == '\\' && end + 1 < src.length())
                    {
                        char next = src.charAt(end + 1);
                        text.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
                        end += 2;
                        continue;
                    }
                    if (c == '`' && ch == '$' && end + 1 < src.length() && src.charAt(end + 1) == '{')
                    {
                        throw new ServiceException("札记脚本格式已变化（模板表达式），无法解析");
                    }
                    text.append(ch);
                    end++;
                }
                out.append(OptionJson.write(text.toString()));
                i = end + 1;
                continue;
            }
            if (c == '[' || c == '{')
            {
                depth++;
                out.append(c);
            }
            else if (c == ']' || c == '}')
            {
                depth--;
                out.append(c);
                if (depth == 0)
                {
                    return out.toString();
                }
            }
            else if (Character.isLetter(c) || c == '_' || c == '$')
            {
                int end = i;
                while (end < src.length() && (Character.isLetterOrDigit(src.charAt(end)) || src.charAt(end) == '_'
                        || src.charAt(end) == '$'))
                {
                    end++;
                }
                String word = src.substring(i, end);
                int next = end;
                while (next < src.length() && Character.isWhitespace(src.charAt(next)))
                {
                    next++;
                }
                if (next < src.length() && src.charAt(next) == ':')
                {
                    out.append(OptionJson.write(word));
                }
                else if ("true".equals(word) || "false".equals(word) || "null".equals(word))
                {
                    out.append(word);
                }
                else if ("void".equals(word) || "undefined".equals(word))
                {
                    out.append("null");
                    if ("void".equals(word))
                    {
                        while (next < src.length() && (Character.isWhitespace(src.charAt(next))
                                || Character.isDigit(src.charAt(next))))
                        {
                            next++;
                        }
                        end = next;
                    }
                }
                else
                {
                    throw new ServiceException("札记脚本格式已变化（含变量引用 " + word + "），无法解析");
                }
                i = end;
                continue;
            }
            else if (c == '!' && i + 1 < src.length() && (src.charAt(i + 1) == '0' || src.charAt(i + 1) == '1'))
            {
                // 压缩后的布尔值：!0 = true，!1 = false
                out.append(src.charAt(i + 1) == '0' ? "true" : "false");
                i += 2;
                continue;
            }
            else
            {
                out.append(c);
            }
            i++;
        }
        throw new ServiceException("札记脚本格式已变化（字面量未闭合），无法解析");
    }
}
