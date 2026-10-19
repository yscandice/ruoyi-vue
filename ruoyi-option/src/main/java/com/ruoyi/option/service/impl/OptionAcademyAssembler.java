package com.ruoyi.option.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.utils.OptionHtml;
import com.ruoyi.option.utils.OptionHtml.Node;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「学堂」页面解析：学堂首页 / 课程 / 一周问答书架与详情 / 术语速查 -> 结构化 JSON
 * 
 * 页面为服务端渲染 HTML，正文在唯一的 main 元素内；课程导航书目在 nav.acd-shelf。
 * 每条结果同时保留安全化后的正文 HTML（去 script/style/事件属性，站内地址转绝对地址）与纯文本，
 * 便于前端按原样式渲染或做检索。
 * 
 * @author yscandice
 */
@Component
public class OptionAcademyAssembler
{
    /** 学堂首页路径 */
    public static final String HOME_PATH = "/academy";

    /** 一周问答书架路径 */
    public static final String QA_PATH = "/academy/weekly-qa";

    /** 术语速查路径 */
    public static final String GLOSSARY_PATH = "/academy/glossary";

    /** 会员课未登录时的门禁区块 class */
    public static final String GATE_CLASS = "acd-gate";

    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private static final Pattern COUNT = Pattern.compile("(\\d+)\\s*个问答");

    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    /**
     * 页面主体
     */
    public static Node main(Node root)
    {
        return root.find("main", null);
    }

    /**
     * 会员正文是否被门禁拦截（会话失效时会员课只返回门禁区块）
     */
    public static boolean locked(Node root)
    {
        Node main = main(root);
        return main != null && main.find("section", GATE_CLASS) != null;
    }

    /**
     * 课程导航书目（nav.acd-shelf 下的 a/span.acd-book，当前页为 span）
     */
    public static ArrayNode books(Node root, String currentPath)
    {
        ArrayNode list = OptionJson.MAPPER.createArrayNode();
        Node shelf = root.find("nav", "acd-shelf");
        if (shelf == null)
        {
            return list;
        }
        int order = 0;
        for (Node book : shelf.findAll(null, "acd-book"))
        {
            String path = StringUtils.nvl(book.attr("href"), currentPath);
            ObjectNode node = list.addObject();
            node.put("key", slug(path));
            node.put("path", path);
            node.put("sortNo", order++);
            node.put("type", book.hasClass("is-qa") ? "qa" : book.hasClass("is-ref") ? "glossary" : "lesson");
            node.put("no", text(book.child("i", null)));
            node.put("title", text(book.child("b", null)));
            Node em = book.child("em", null);
            node.put("access", text(em));
            node.put("open", em != null && em.hasClass("open"));
        }
        return list;
    }

    /**
     * 学堂首页 /academy
     */
    public ObjectNode home(Node root, String baseUrl)
    {
        Node main = main(root);
        ObjectNode page = pageHeader("home", HOME_PATH, root, main, baseUrl);
        page.set("scope", scope(main));
        Node video = main.find("button", "nc-vcard");
        if (video != null)
        {
            ObjectNode v = page.putObject("video");
            Node img = video.find("img", null);
            v.put("poster", img == null ? null : OptionHtml.absolute(img.attr("src"), baseUrl));
            Node tx = video.find("span", "nc-vcard-tx");
            v.put("title", text(tx == null ? null : tx.child("b", null)));
            v.put("summary", text(tx == null ? null : tx.child("span", null)));
            v.put("meta", text(tx == null ? null : tx.child("em", null)));
        }
        ArrayNode notes = page.putArray("notes");
        Node why = main.find("section", "acd-why-sec");
        if (why != null)
        {
            page.put("notesHeading", textExcept(why.find("h2", null), "i"));
            for (Node a : why.findAll("a", null))
            {
                ObjectNode note = notes.addObject();
                note.put("id", query(a.attr("href"), "id"));
                note.put("href", OptionHtml.absolute(a.attr("href"), baseUrl));
                note.put("tag", text(a.child("small", null)));
                note.put("title", text(a.child("b", null)));
            }
        }
        ArrayNode lessons = page.putArray("lessons");
        Node ol = main.find("ol", "acd-list");
        int order = 0;
        if (ol != null)
        {
            for (Node a : ol.findAll("a", null))
            {
                lessons.add(lessonEntry(a, ++order));
            }
        }
        ArrayNode refs = page.putArray("refs");
        for (Node a : main.findAll("a", "acd-ref-card"))
        {
            ObjectNode ref = refs.addObject();
            ref.put("key", slug(a.attr("href")));
            ref.put("path", a.attr("href"));
            ref.put("title", text(a.child("b", null)));
            ref.put("summary", text(a.child("span", null)));
        }
        page.put("note", text(main.find("p", "acd-note")));
        Node foot = main.find("footer", "acd-foot");
        ArrayNode footer = page.putArray("footer");
        if (foot != null)
        {
            for (Node p : foot.findAll("p", null))
            {
                footer.add(p.text());
            }
        }
        finish(page, main, baseUrl, lessons.size());
        return page;
    }

    /**
     * 首页课程清单条目 ol.acd-list > li > a
     */
    private static ObjectNode lessonEntry(Node a, int order)
    {
        ObjectNode entry = OptionJson.MAPPER.createObjectNode();
        String path = a.attr("href");
        entry.put("key", slug(path));
        entry.put("path", path);
        entry.put("order", order);
        Node ord = a.find("p", "acd-list-ord");
        entry.put("no", textExcept(ord, "em", "i"));
        entry.put("tagline", ord == null ? null : text(ord.find("em", null)));
        Node access = ord == null ? null : ord.find("i", null);
        entry.put("access", text(access));
        entry.put("open", access != null && access.hasClass("open"));
        entry.put("first", a.hasClass("is-first"));
        entry.put("title", text(a.find("h2", null)));
        entry.put("summary", text(a.find("p", "acd-list-sum")));
        entry.put("caseLine", text(a.find("p", "acd-list-case")));
        return entry;
    }

    /**
     * 课程页 /academy/{slug}
     * 
     * @param entry 首页课程清单中的条目（课序、可见范围、摘要），可为 null
     */
    public ObjectNode lesson(Node root, String path, JsonNode entry, String baseUrl)
    {
        Node main = main(root);
        String slug = slug(path);
        ObjectNode lesson = OptionJson.MAPPER.createObjectNode();
        lesson.put("key", slug);
        lesson.put("path", path);
        lesson.put("order", entry == null ? 0 : entry.path("order").asInt());
        lesson.put("no", entry == null ? null : OptionConductAssembler.text(entry, "no"));
        lesson.put("access", entry == null ? null : OptionConductAssembler.text(entry, "access"));
        lesson.put("open", entry != null && entry.path("open").asBoolean());
        lesson.put("tagline", entry == null ? null : OptionConductAssembler.text(entry, "tagline"));
        lesson.put("summary", entry == null ? null : OptionConductAssembler.text(entry, "summary"));
        lesson.put("caseLine", entry == null ? null : OptionConductAssembler.text(entry, "caseLine"));
        Node current = null;
        Node shelf = root.find("nav", "acd-shelf");
        if (shelf != null)
        {
            current = shelf.find(n -> n.hasClass("acd-book") && n.hasClass("is-current"));
        }
        lesson.put("navTitle", current == null ? null : text(current.child("b", null)));
        lesson.put("kick", text(first(main, "acd-kick", "kick")));
        lesson.put("title", text(main.find("h1", null)));
        lesson.put("deck", text(first(main, "acd-deck", "deck")));
        ArrayNode ledes = lesson.putArray("ledes");
        for (Node p : main.findAll(n -> n.is("p") && (n.hasClass("acd-lede") || n.hasClass("lede")
                || n.hasClass("sog-long-lede"))))
        {
            ledes.add(p.text());
        }
        lesson.put("lede", ledes.isEmpty() ? null : ledes.get(0).asText());
        Node firstSec = main.find(n -> n.is("section") && (n.hasClass("acd-first") || n.hasClass("first")));
        ArrayNode keys = lesson.putArray("keys");
        if (firstSec != null)
        {
            lesson.put("firstLabel", text(firstSec.find(n -> n.is("p")
                    && (n.hasClass("acd-first-label") || n.hasClass("first-label")))));
            for (Node li : firstSec.findAll("li", null))
            {
                keys.add(li.text());
            }
        }
        lesson.set("scope", scope(main));
        ArrayNode sections = lesson.putArray("sections");
        sections(main, sections);
        ArrayNode notes = lesson.putArray("notes");
        for (Node p : main.findAll("p", "acd-sec-note"))
        {
            notes.add(p.text());
        }
        Node why = root.find("a", "acd-why");
        if (why != null)
        {
            ObjectNode note = lesson.putObject("why");
            note.put("id", query(why.attr("href"), "id"));
            note.put("href", OptionHtml.absolute(why.attr("href"), baseUrl));
            note.put("tag", text(why.child("small", null)));
            note.put("title", text(why.child("b", null)));
            note.put("summary", text(why.child("span", null)));
        }
        lesson.put("scopeLine", text(root.find("p", "acd-scope-line")));
        lesson.put("locked", locked(root));
        finish(lesson, main, baseUrl, sections.size());
        return lesson;
    }

    /**
     * 一周问答书架 /academy/weekly-qa：返回 [书架页头, 各期封面...]
     */
    public ObjectNode qaShelf(Node root, String baseUrl)
    {
        Node main = main(root);
        ObjectNode page = pageHeader("weekly-qa", QA_PATH, root, main, baseUrl);
        ArrayNode issues = page.putArray("issues");
        int order = 0;
        for (Node a : main.findAll("a", "wqs-cover"))
        {
            ObjectNode issue = issues.addObject();
            String path = a.attr("href");
            issue.put("key", slug(path));
            issue.put("path", path);
            issue.put("sortNo", order++);
            issue.put("no", text(a.find("span", "wqs-no")));
            issue.put("latest", a.hasClass("is-latest") || a.find("span", "wqs-new") != null);
            issue.put("title", text(a.child("b", null)));
            issue.put("range", text(a.child("small", null)));
            issue.put("summary", text(a.child("p", null)));
            String meta = text(a.child("em", null));
            issue.put("meta", meta);
            issue.put("publishDate", match(DATE, meta));
            String count = match(COUNT, meta);
            if (count != null)
            {
                issue.put("count", Integer.parseInt(count));
            }
        }
        finish(page, main, baseUrl, issues.size());
        return page;
    }

    /**
     * 一周问答单期 /academy/weekly-qa/{n}
     * 
     * @param cover 书架封面条目，可为 null
     */
    public ObjectNode qa(Node root, String path, JsonNode cover, String baseUrl)
    {
        Node main = main(root);
        ObjectNode qa = OptionJson.MAPPER.createObjectNode();
        qa.put("key", slug(path));
        qa.put("path", path);
        qa.put("no", cover == null ? null : OptionConductAssembler.text(cover, "no"));
        String title = text(main.find("h1", null));
        qa.put("issue", match(NUMBER, title));
        qa.put("title", title);
        qa.put("kick", text(main.find("p", "acd-kick")));
        qa.put("deck", text(main.find("p", "acd-deck")));
        qa.put("lede", text(main.find("p", "acd-lede")));
        qa.put("range", cover == null ? null : OptionConductAssembler.text(cover, "range"));
        qa.put("summary", cover == null ? null : OptionConductAssembler.text(cover, "summary"));
        qa.put("publishDate", cover == null ? null : OptionConductAssembler.text(cover, "publishDate"));
        qa.put("latest", cover != null && cover.path("latest").asBoolean());
        ArrayNode chapters = qa.putArray("chapters");
        int total = 0;
        for (Node section : main.findAll("section", null))
        {
            Node chap = section.find("h2", "wq-chap");
            if (chap == null)
            {
                continue;
            }
            ObjectNode chapter = chapters.addObject();
            Node chapNo = chap.find(n -> !n.isText());
            chapter.put("no", text(chapNo));
            chapter.put("title", chapNo == null ? chap.text() : textExcept(chap, chapNo.tag()));
            ArrayNode items = chapter.putArray("items");
            for (Node article : section.findAll("article", "wq-item"))
            {
                ObjectNode item = items.addObject();
                Node h3 = article.find("h3", null);
                Node q = h3 == null ? null : h3.find(n -> !n.isText());
                item.put("q", text(q));
                item.put("title", q == null ? text(h3) : textExcept(h3, q.tag()));
                Node ask = article.find("p", "wq-ask");
                Node askLabel = ask == null ? null : ask.find(n -> !n.isText());
                item.put("askLabel", text(askLabel));
                item.put("ask", askLabel == null ? text(ask) : textExcept(ask, askLabel.tag()));
                Node ans = article.find("div", "wq-ans");
                Node avatar = ans == null ? null : ans.find("img", null);
                item.put("avatar", avatar == null ? null : OptionHtml.absolute(avatar.attr("src"), baseUrl));
                Node body = ans == null ? null : ans.find("div", "wq-ans-body");
                item.put("answerHtml", body == null ? null : body.innerHtml(baseUrl));
                item.put("answer", body == null ? null : body.blockText());
                Node key = article.find("p", "wq-key");
                Node keyLabel = key == null ? null : key.find(n -> !n.isText());
                item.put("keyLabel", text(keyLabel));
                item.put("key", keyLabel == null ? text(key) : textExcept(key, keyLabel.tag()));
                total++;
            }
        }
        qa.put("count", total);
        finish(qa, main, baseUrl, total);
        return qa;
    }

    /**
     * 术语速查 /academy/glossary：返回 [页头, 术语...]
     */
    public List<ObjectNode> glossary(Node root, String baseUrl, ObjectNode[] header)
    {
        Node main = main(root);
        List<ObjectNode> terms = new ArrayList<>();
        int order = 0;
        for (Node item : main.findAll("div", "acd-gloss-item"))
        {
            ObjectNode term = OptionJson.MAPPER.createObjectNode();
            Node dt = item.find("dt", null);
            Node dd = item.find("dd", null);
            String name = textExcept(dt, "small");
            term.put("key", StringUtils.nvl(item.attr("id"), name));
            term.put("term", name);
            term.put("en", text(dt == null ? null : dt.find("small", null)));
            Node definition = dd == null ? null
                    : dd.find(n -> n.is("p") && !n.hasClass("acd-gloss-where") && !n.hasClass("acd-gloss-not"));
            term.put("definition", text(definition));
            Node where = dd == null ? null : dd.find("p", "acd-gloss-where");
            term.put("whereLabel", text(where == null ? null : where.find("span", null)));
            term.put("where", textExcept(where, "span"));
            Node not = dd == null ? null : dd.find("p", "acd-gloss-not");
            term.put("notLabel", text(not == null ? null : not.find("span", null)));
            term.put("not", textExcept(not, "span"));
            term.put("anchor", GLOSSARY_PATH + "#" + term.path("key").asText());
            term.put("sortNo", order++);
            terms.add(term);
        }
        ObjectNode page = pageHeader("glossary", GLOSSARY_PATH, root, main, baseUrl);
        page.put("termCount", terms.size());
        finish(page, main, baseUrl, terms.size());
        header[0] = page;
        return terms;
    }

    private static ObjectNode pageHeader(String key, String path, Node root, Node main, String baseUrl)
    {
        ObjectNode page = OptionJson.MAPPER.createObjectNode();
        page.put("key", key);
        page.put("path", path);
        page.put("kick", text(main.find("p", "acd-kick")));
        page.put("title", text(main.find("h1", null)));
        page.put("deck", text(main.find("p", "acd-deck")));
        page.put("lede", text(main.find("p", "acd-lede")));
        page.put("scopeLine", text(root.find("p", "acd-scope-line")));
        page.set("books", books(root, path));
        return page;
    }

    private static void finish(ObjectNode node, Node main, String baseUrl, int itemCount)
    {
        String text = main.blockText();
        node.put("itemCount", itemCount);
        node.put("textLength", text.replaceAll("\\s", "").length());
        node.put("text", text);
        node.put("html", main.innerHtml(baseUrl));
    }

    /**
     * 这里教 / 这里不教（div.acd-scope 或 div.scope）
     */
    private static ArrayNode scope(Node main)
    {
        ArrayNode list = OptionJson.MAPPER.createArrayNode();
        Node scope = main.find(n -> n.is("div") && (n.hasClass("acd-scope") || n.hasClass("scope")));
        if (scope == null)
        {
            return list;
        }
        for (Node div : scope.elements("div", null))
        {
            ObjectNode item = list.addObject();
            item.put("type", div.hasClass("no") ? "exclude" : "include");
            Node label = div.find("span", null);
            item.put("label", text(label));
            item.put("text", label == null ? div.text() : textExcept(div, "span"));
        }
        return list;
    }

    /**
     * 正文小节：含标题（h2/h3）的 section，嵌套小节放入 subsections
     */
    private static void sections(Node node, ArrayNode out)
    {
        for (Node child : node.elements())
        {
            if (child.is("section") && !child.hasClass("acd-first") && !child.hasClass("first")
                    && !child.hasClass(GATE_CLASS))
            {
                Node heading = local(child, n -> n.is("h2") || n.is("h3"));
                if (heading != null)
                {
                    ObjectNode sec = out.addObject();
                    sec.put("id", child.attr("id"));
                    sec.put("num", text(local(child, n -> isNum(n))));
                    sec.put("title", textExcept(heading, "small", "i"));
                    sec.put("summary", summary(child));
                    ArrayNode subs = OptionJson.MAPPER.createArrayNode();
                    sections(child, subs);
                    if (!subs.isEmpty())
                    {
                        sec.set("subsections", subs);
                    }
                    sec.put("text", child.blockText());
                    continue;
                }
            }
            sections(child, out);
        }
    }

    private static String summary(Node section)
    {
        Node head = local(section, n -> n.is("div") && (n.hasClass("acd-sec-h") || n.hasClass("sec-h")));
        if (head != null)
        {
            for (Node p : head.elements("p", null))
            {
                if (!isNum(p))
                {
                    return p.text();
                }
            }
        }
        Node apg = local(section, n -> n.is("div") && n.hasClass("apg-section-head"));
        if (apg != null)
        {
            return text(apg.find("small", null));
        }
        Node p = local(section, n -> n.is("p") && n.hasClass("sog-text"));
        if (p == null)
        {
            p = local(section, n -> n.is("p") && !isNum(n));
        }
        return text(p);
    }

    private static boolean isNum(Node n)
    {
        String cls = n.attr("class");
        if (cls == null)
        {
            return false;
        }
        for (String part : cls.split("\\s+"))
        {
            if ("num".equals(part) || part.endsWith("-num"))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 小节内查找（不进入嵌套 section）
     */
    private static Node local(Node section, Predicate<Node> predicate)
    {
        for (Node child : section.elements())
        {
            if (child.is("section"))
            {
                continue;
            }
            if (predicate.test(child))
            {
                return child;
            }
            Node found = local(child, predicate);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    private static Node first(Node main, String... classes)
    {
        return main.find(n -> {
            for (String cls : classes)
            {
                if (n.hasClass(cls))
                {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 文本（跳过指定标签的子元素），如 dt 去掉 small 英文名
     */
    static String textExcept(Node node, String... tags)
    {
        if (node == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Node child : node.children())
        {
            boolean skip = false;
            for (String tag : tags)
            {
                skip |= child.is(tag);
            }
            if (!skip)
            {
                sb.append(child.rawText());
            }
        }
        String value = OptionHtml.normalize(sb.toString());
        return value.isEmpty() ? null : value;
    }

    static String text(Node node)
    {
        if (node == null)
        {
            return null;
        }
        String value = node.text();
        return value.isEmpty() ? null : value;
    }

    /**
     * 路径最后一段：/academy/workbench -> workbench，/academy/weekly-qa/1 -> 1，/academy -> home
     */
    static String slug(String path)
    {
        if (StringUtils.isEmpty(path))
        {
            return null;
        }
        String clean = path.replaceAll("[?#].*$", "").replaceAll("/+$", "");
        if (HOME_PATH.equals(clean) || clean.isEmpty())
        {
            return "home";
        }
        return clean.substring(clean.lastIndexOf('/') + 1);
    }

    private static String query(String href, String name)
    {
        if (href == null)
        {
            return null;
        }
        Matcher m = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&#]*)").matcher(href);
        return m.find() ? m.group(1) : null;
    }

    private static String match(Pattern pattern, String text)
    {
        if (text == null)
        {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
