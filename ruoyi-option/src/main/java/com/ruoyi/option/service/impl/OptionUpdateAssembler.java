package com.ruoyi.option.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.utils.OptionHtml;
import com.ruoyi.option.utils.OptionHtml.Node;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「更新」菜单页面解析（/member/updates「功能更新」，服务端渲染 HTML -> 结构化 JSON）
 * 
 * 页面结构：div.sdh-wrap
 *   header.pu-head-row   small 栏目英文名、h1 标题、p.pu-sub 说明（含设置入口）、img.pu-mascot 吉祥物
 *   ol.pu-list > li.pu-item#u-{yyyy-MM-dd}-{slug}
 *     div.pu-visual       配图：video（src + poster）或内联 svg 示意图
 *     div.pu-body         div.pu-head > time[datetime]、h2 标题、p.pu-what 做了什么、p.pu-how（b 怎么用）、
 *                         div.pu-foot > a.sd-bt「去用一下 →」目标链接
 *   p.sc-foot            页脚说明
 * 
 * @author yscandice
 */
@Component
public class OptionUpdateAssembler
{
    /** 更新页面路径 */
    public static final String PAGE_PATH = "/member/updates";

    /** 页头条目键 */
    public static final String PAGE_KEY = "updates";

    /** 条目锚点前缀 */
    private static final String ANCHOR_PREFIX = "u-";

    /**
     * 页面容器（找不到返回 null，视为页面结构调整或未登录）
     */
    public static Node wrap(Node root)
    {
        Node list = root.find("ol", "pu-list");
        if (list == null)
        {
            return null;
        }
        Node wrap = root.find("div", "sdh-wrap");
        return wrap == null ? root : wrap;
    }

    /**
     * 解析更新条目（页面顺序，最新在前）
     */
    public List<ObjectNode> entries(Node root, String baseUrl)
    {
        List<ObjectNode> list = new ArrayList<>();
        Node ol = root.find("ol", "pu-list");
        if (ol == null)
        {
            return list;
        }
        int order = 0;
        for (Node li : ol.findAll("li", "pu-item"))
        {
            list.add(entry(li, ++order, baseUrl));
        }
        return list;
    }

    private ObjectNode entry(Node li, int order, String baseUrl)
    {
        ObjectNode item = OptionJson.MAPPER.createObjectNode();
        String anchor = li.attr("id");
        Node body = li.find("div", "pu-body");
        Node scope = body == null ? li : body;
        Node time = scope.find("time", null);
        String date = time == null ? null : StringUtils.defaultIfEmpty(time.attr("datetime"), text(time));
        String title = text(scope.find("h2", null));
        String key = anchor == null ? null
                : anchor.startsWith(ANCHOR_PREFIX) ? anchor.substring(ANCHOR_PREFIX.length()) : anchor;
        if (StringUtils.isEmpty(key))
        {
            key = StringUtils.nvl(date, "") + "-" + order;
        }
        item.put("key", key);
        item.put("anchor", anchor);
        item.put("path", PAGE_PATH + (anchor == null ? "" : "#" + anchor));
        item.put("order", order);
        item.put("date", date);
        item.put("month", date != null && date.length() >= 7 ? date.substring(0, 7) : null);
        item.put("dateText", text(time));
        item.put("title", title);

        Node what = scope.find("p", "pu-what");
        item.put("what", text(what));
        item.put("whatHtml", what == null ? null : what.innerHtml(baseUrl));

        Node how = scope.find("p", "pu-how");
        Node howLabel = how == null ? null : how.child("b", null);
        item.put("howLabel", text(howLabel));
        item.put("how", how == null ? null : OptionAcademyAssembler.textExcept(how, "b"));
        item.put("howHtml", how == null ? null : innerHtmlExcept(how, howLabel, baseUrl));

        ObjectNode target = item.putObject("target");
        Node link = scope.find("div", "pu-foot");
        link = link == null ? null : link.find("a", null);
        if (link != null)
        {
            String href = link.attr("href");
            target.put("label", StringUtils.trim(StringUtils.nvl(text(link), "").replace("→", "")));
            target.put("path", href);
            target.put("url", OptionHtml.absolute(href, baseUrl));
        }

        ObjectNode visual = item.putObject("visual");
        Node box = li.find("div", "pu-visual");
        Node video = box == null ? null : box.find("video", null);
        Node svg = box == null ? null : box.find("svg", null);
        Node img = box == null ? null : box.find("img", null);
        if (video != null)
        {
            visual.put("type", "video");
            visual.put("src", OptionHtml.absolute(video.attr("src"), baseUrl));
            visual.put("poster", OptionHtml.absolute(video.attr("poster"), baseUrl));
        }
        else if (svg != null)
        {
            visual.put("type", "svg");
            visual.put("viewBox", StringUtils.defaultIfEmpty(svg.attr("viewbox"), svg.attr("viewBox")));
            visual.put("label", StringUtils.defaultIfEmpty(svg.attr("aria-label"), null));
            visual.put("html", svg.html(baseUrl));
        }
        else if (img != null)
        {
            visual.put("type", "image");
            visual.put("src", OptionHtml.absolute(img.attr("src"), baseUrl));
            visual.put("alt", img.attr("alt"));
        }
        else
        {
            visual.put("type", "none");
        }

        String plain = StringUtils.nvl(item.path("what").asText(null), "") + StringUtils.nvl(item.path("how").asText(null), "");
        item.put("textLength", plain.replaceAll("\\s+", "").length());
        return item;
    }

    /**
     * 页头：栏目名、标题、说明、设置入口、吉祥物、页脚，并汇总条目数 / 日期范围 / 按月与按目标链接分布
     */
    public ObjectNode page(Node root, List<ObjectNode> entries, String baseUrl)
    {
        ObjectNode page = OptionJson.MAPPER.createObjectNode();
        Node wrap = wrap(root);
        Node head = wrap == null ? null : wrap.find("header", "pu-head-row");
        page.put("key", PAGE_KEY);
        page.put("path", PAGE_PATH);
        page.put("kick", head == null ? null : text(head.find("small", null)));
        page.put("title", head == null ? null : text(head.find("h1", null)));
        Node sub = head == null ? null : head.find("p", "pu-sub");
        page.put("sub", text(sub));
        page.put("subHtml", sub == null ? null : sub.innerHtml(baseUrl));
        Node settings = sub == null ? null : sub.find("a", null);
        page.put("settingsPath", settings == null ? null : settings.attr("href"));
        page.put("settingsLabel", text(settings));
        Node mascot = head == null ? null : head.find("img", null);
        page.put("mascot", mascot == null ? null : OptionHtml.absolute(mascot.attr("src"), baseUrl));
        Node foot = wrap == null ? null : wrap.find("p", "sc-foot");
        page.put("foot", text(foot));

        page.put("itemCount", entries.size());
        String latest = null;
        String earliest = null;
        Map<String, Integer> months = new LinkedHashMap<>();
        Map<String, Integer> targets = new LinkedHashMap<>();
        Map<String, Integer> visuals = new LinkedHashMap<>();
        for (ObjectNode entry : entries)
        {
            String date = entry.path("date").asText(null);
            if (date != null)
            {
                latest = latest == null || date.compareTo(latest) > 0 ? date : latest;
                earliest = earliest == null || date.compareTo(earliest) < 0 ? date : earliest;
            }
            String month = entry.path("month").asText(null);
            if (month != null)
            {
                months.merge(month, 1, Integer::sum);
            }
            String path = entry.path("target").path("path").asText(null);
            if (path != null)
            {
                targets.merge(path.replaceAll("[?#].*$", ""), 1, Integer::sum);
            }
            visuals.merge(entry.path("visual").path("type").asText("none"), 1, Integer::sum);
        }
        page.put("latestDate", latest);
        page.put("earliestDate", earliest);
        ArrayNode monthList = page.putArray("months");
        months.forEach((k, v) -> monthList.addObject().put("month", k).put("count", v));
        ArrayNode targetList = page.putArray("targets");
        targets.forEach((k, v) -> targetList.addObject().put("path", k).put("count", v));
        ObjectNode visualCount = page.putObject("visuals");
        visuals.forEach(visualCount::put);
        int length = 0;
        for (ObjectNode entry : entries)
        {
            length += entry.path("textLength").asInt();
        }
        page.put("textLength", length);
        return page;
    }

    private static String innerHtmlExcept(Node node, Node skip, String baseUrl)
    {
        StringBuilder sb = new StringBuilder();
        for (Node child : node.children())
        {
            if (child != skip)
            {
                sb.append(child.html(baseUrl));
            }
        }
        String html = sb.toString().trim();
        return html.isEmpty() ? null : html;
    }

    private static String text(Node node)
    {
        return OptionAcademyAssembler.text(node);
    }
}
