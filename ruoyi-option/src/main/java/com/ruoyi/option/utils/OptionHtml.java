package com.ruoyi.option.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 HTML 解析（OSM 学堂页面为服务端渲染的规范 HTML，无需完整 HTML5 容错）
 * 
 * 支持：注释 / doctype 跳过、script / style 原文、void 元素、自闭合、属性、实体解码、按标签 / class 查找、
 * 文本提取、安全序列化（去掉 script / style / 事件属性，站内相对地址转为绝对地址）
 * 
 * @author yscandice
 */
public final class OptionHtml
{
    private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr");

    private static final Set<String> RAW = Set.of("script", "style", "textarea", "title");

    private static final Set<String> DROP = Set.of("script", "style", "noscript", "template", "iframe", "object",
            "link", "meta");

    private static final Set<String> BLOCK = Set.of("address", "article", "aside", "blockquote", "dd", "div", "dl",
            "dt", "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main",
            "nav", "ol", "p", "pre", "section", "table", "tr", "ul", "br");

    private static final Pattern ATTR = Pattern
            .compile("([^\\s\"'>/=]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");

    private static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);");

    private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("amp", "&"), Map.entry("lt", "<"),
            Map.entry("gt", ">"), Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", "\u00a0"),
            Map.entry("middot", "·"), Map.entry("mdash", "—"), Map.entry("ndash", "–"), Map.entry("hellip", "…"),
            Map.entry("times", "×"), Map.entry("rarr", "→"), Map.entry("larr", "←"), Map.entry("ldquo", "“"),
            Map.entry("rdquo", "”"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("yen", "¥"),
            Map.entry("copy", "©"), Map.entry("reg", "®"), Map.entry("deg", "°"), Map.entry("plusmn", "±"));

    private OptionHtml()
    {
    }

    /**
     * 节点：元素（tag 非空）或文本（tag 为空）
     */
    public static final class Node
    {
        private final String tag;

        private final Map<String, String> attrs;

        private final List<Node> children = new ArrayList<>();

        private final String text;

        private Node(String tag, Map<String, String> attrs, String text)
        {
            this.tag = tag;
            this.attrs = attrs;
            this.text = text;
        }

        public String tag()
        {
            return tag;
        }

        public boolean isText()
        {
            return tag == null;
        }

        public List<Node> children()
        {
            return children;
        }

        public String attr(String name)
        {
            return attrs == null ? null : attrs.get(name);
        }

        public boolean is(String name)
        {
            return name.equals(tag);
        }

        public boolean hasClass(String cls)
        {
            String value = attr("class");
            if (value == null)
            {
                return false;
            }
            for (String part : value.split("\\s+"))
            {
                if (part.equals(cls))
                {
                    return true;
                }
            }
            return false;
        }

        /** 直接子元素 */
        public List<Node> elements()
        {
            List<Node> list = new ArrayList<>();
            for (Node child : children)
            {
                if (!child.isText())
                {
                    list.add(child);
                }
            }
            return list;
        }

        /** 直接子元素中按标签 / class 匹配（参数为 null 不限） */
        public List<Node> elements(String name, String cls)
        {
            List<Node> list = new ArrayList<>();
            for (Node child : elements())
            {
                if (matches(child, name, cls))
                {
                    list.add(child);
                }
            }
            return list;
        }

        public Node child(String name, String cls)
        {
            List<Node> list = elements(name, cls);
            return list.isEmpty() ? null : list.get(0);
        }

        /** 后代元素（先序）中按标签 / class 匹配 */
        public List<Node> findAll(String name, String cls)
        {
            return findAll(n -> matches(n, name, cls));
        }

        public List<Node> findAll(Predicate<Node> predicate)
        {
            List<Node> list = new ArrayList<>();
            collect(this, predicate, list);
            return list;
        }

        public Node find(String name, String cls)
        {
            return find(n -> matches(n, name, cls));
        }

        public Node find(Predicate<Node> predicate)
        {
            for (Node child : elements())
            {
                if (predicate.test(child))
                {
                    return child;
                }
                Node found = child.find(predicate);
                if (found != null)
                {
                    return found;
                }
            }
            return null;
        }

        /** 行内文本：合并空白 */
        public String text()
        {
            StringBuilder sb = new StringBuilder();
            appendText(this, sb, false);
            return normalize(sb.toString());
        }

        /** 原始文本（不合并空白） */
        public String rawText()
        {
            StringBuilder sb = new StringBuilder();
            appendText(this, sb, false);
            return sb.toString();
        }

        /** 块级文本：块级元素之间换行，适合全文检索与纯文本展示 */
        public String blockText()
        {
            StringBuilder sb = new StringBuilder();
            appendText(this, sb, true);
            StringBuilder out = new StringBuilder();
            for (String line : sb.toString().split("\n"))
            {
                String value = normalize(line);
                if (!value.isEmpty())
                {
                    out.append(out.length() == 0 ? "" : "\n").append(value);
                }
            }
            return out.toString();
        }

        /** 本节点（含自身）安全序列化 */
        public String html(String baseUrl)
        {
            StringBuilder sb = new StringBuilder();
            write(this, sb, baseUrl);
            return sb.toString();
        }

        /** 子节点安全序列化 */
        public String innerHtml(String baseUrl)
        {
            StringBuilder sb = new StringBuilder();
            for (Node child : children)
            {
                write(child, sb, baseUrl);
            }
            return sb.toString();
        }
    }

    /**
     * 解析整页 HTML，返回虚拟根节点
     */
    public static Node parse(String html)
    {
        Node root = new Node("#root", Map.of(), null);
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        int i = 0;
        int n = html.length();
        StringBuilder text = new StringBuilder();
        while (i < n)
        {
            char c = html.charAt(i);
            if (c != '<' || i + 1 >= n)
            {
                text.append(c);
                i++;
                continue;
            }
            char next = html.charAt(i + 1);
            if (html.startsWith("<!--", i))
            {
                int end = html.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (next == '!' || next == '?')
            {
                int end = html.indexOf('>', i);
                i = end < 0 ? n : end + 1;
                continue;
            }
            if (next == '/')
            {
                int end = html.indexOf('>', i);
                if (end < 0)
                {
                    text.append(html, i, n);
                    break;
                }
                flush(stack.peek(), text);
                String name = html.substring(i + 2, end).trim().toLowerCase(Locale.ROOT);
                close(stack, name);
                i = end + 1;
                continue;
            }
            if (!Character.isLetter(next))
            {
                text.append(c);
                i++;
                continue;
            }
            int end = tagEnd(html, i + 1);
            if (end < 0)
            {
                text.append(html, i, n);
                break;
            }
            flush(stack.peek(), text);
            String inner = html.substring(i + 1, end);
            boolean selfClosing = inner.endsWith("/");
            if (selfClosing)
            {
                inner = inner.substring(0, inner.length() - 1);
            }
            int split = 0;
            while (split < inner.length() && !Character.isWhitespace(inner.charAt(split)))
            {
                split++;
            }
            String name = inner.substring(0, split).toLowerCase(Locale.ROOT);
            Node element = new Node(name, attributes(inner.substring(split)), null);
            stack.peek().children.add(element);
            i = end + 1;
            if (RAW.contains(name))
            {
                int close = indexOfIgnoreCase(html, "</" + name, i);
                int stop = close < 0 ? n : close;
                String raw = html.substring(i, stop);
                element.children.add(new Node(null, null, "script".equals(name) || "style".equals(name) ? raw
                        : decode(raw)));
                int gt = close < 0 ? -1 : html.indexOf('>', close);
                i = gt < 0 ? n : gt + 1;
                continue;
            }
            if (!selfClosing && !VOID.contains(name))
            {
                stack.push(element);
            }
        }
        flush(stack.peek(), text);
        return root;
    }

    /**
     * 解码 HTML 实体
     */
    public static String decode(String text)
    {
        if (text == null || text.indexOf('&') < 0)
        {
            return text;
        }
        Matcher m = ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find())
        {
            String entity = m.group(1);
            String value;
            if (entity.charAt(0) == '#')
            {
                try
                {
                    int code = entity.length() > 1 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X')
                            ? Integer.parseInt(entity.substring(2), 16)
                            : Integer.parseInt(entity.substring(1));
                    value = new String(Character.toChars(code));
                }
                catch (IllegalArgumentException e)
                {
                    value = m.group();
                }
            }
            else
            {
                value = NAMED.getOrDefault(entity, m.group());
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 合并空白（含不换行空格）并去掉首尾空白
     */
    public static String normalize(String text)
    {
        return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean matches(Node node, String name, String cls)
    {
        return !node.isText() && (name == null || name.equals(node.tag)) && (cls == null || node.hasClass(cls));
    }

    private static void collect(Node node, Predicate<Node> predicate, List<Node> list)
    {
        for (Node child : node.elements())
        {
            if (predicate.test(child))
            {
                list.add(child);
            }
            collect(child, predicate, list);
        }
    }

    private static void appendText(Node node, StringBuilder sb, boolean block)
    {
        if (node.isText())
        {
            sb.append(node.text);
            return;
        }
        if (DROP.contains(node.tag) || "svg".equals(node.tag))
        {
            return;
        }
        boolean isBlock = block && BLOCK.contains(node.tag);
        if (isBlock)
        {
            sb.append('\n');
        }
        for (Node child : node.children)
        {
            appendText(child, sb, block);
        }
        if (isBlock)
        {
            sb.append('\n');
        }
    }

    private static void write(Node node, StringBuilder sb, String baseUrl)
    {
        if (node.isText())
        {
            sb.append(escape(node.text, false));
            return;
        }
        if (DROP.contains(node.tag))
        {
            return;
        }
        if ("#root".equals(node.tag))
        {
            for (Node child : node.children)
            {
                write(child, sb, baseUrl);
            }
            return;
        }
        sb.append('<').append(node.tag);
        for (Map.Entry<String, String> attr : node.attrs.entrySet())
        {
            String name = attr.getKey();
            if (name.startsWith("on") || "nonce".equals(name))
            {
                continue;
            }
            String value = attr.getValue();
            if (value != null && ("src".equals(name) || "href".equals(name) || "poster".equals(name)))
            {
                if (value.trim().toLowerCase(Locale.ROOT).startsWith("javascript:"))
                {
                    continue;
                }
                value = absolute(value, baseUrl);
            }
            else if (value != null && ("srcset".equalsIgnoreCase(name)))
            {
                StringBuilder set = new StringBuilder();
                for (String part : value.split(","))
                {
                    set.append(set.length() == 0 ? "" : ", ").append(absolute(part.trim(), baseUrl));
                }
                value = set.toString();
            }
            sb.append(' ').append(name);
            if (value != null)
            {
                sb.append("=\"").append(escape(value, true)).append('"');
            }
        }
        sb.append('>');
        if (VOID.contains(node.tag))
        {
            return;
        }
        for (Node child : node.children)
        {
            write(child, sb, baseUrl);
        }
        sb.append("</").append(node.tag).append('>');
    }

    /**
     * 站内相对地址（/ 开头，非 //）转为绝对地址；锚点与外链保持不变
     */
    public static String absolute(String value, String baseUrl)
    {
        if (baseUrl == null || value == null || !value.startsWith("/") || value.startsWith("//"))
        {
            return value;
        }
        return baseUrl + value;
    }

    private static String escape(String text, boolean attr)
    {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            switch (c)
            {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append(attr ? "&quot;" : "\"");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void flush(Node parent, StringBuilder text)
    {
        if (text.length() > 0)
        {
            parent.children.add(new Node(null, null, decode(text.toString())));
            text.setLength(0);
        }
    }

    private static void close(Deque<Node> stack, String name)
    {
        for (Node node : stack)
        {
            if (name.equals(node.tag))
            {
                while (stack.size() > 1)
                {
                    Node popped = stack.pop();
                    if (popped == node)
                    {
                        return;
                    }
                }
                return;
            }
        }
    }

    private static int tagEnd(String html, int from)
    {
        char quote = 0;
        for (int i = from; i < html.length(); i++)
        {
            char c = html.charAt(i);
            if (quote != 0)
            {
                if (c == quote)
                {
                    quote = 0;
                }
            }
            else if (c == '"' || c == '\'')
            {
                quote = c;
            }
            else if (c == '>')
            {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, String> attributes(String text)
    {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(text);
        while (m.find())
        {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
            attrs.putIfAbsent(name, value == null ? null : decode(value));
        }
        return attrs;
    }

    private static int indexOfIgnoreCase(String html, String needle, int from)
    {
        int len = needle.length();
        for (int i = from; i <= html.length() - len; i++)
        {
            if (html.regionMatches(true, i, needle, 0, len))
            {
                return i;
            }
        }
        return -1;
    }
}
