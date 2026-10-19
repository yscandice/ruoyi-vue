package com.ruoyi.option.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.enums.OptionSettlementKind;
import com.ruoyi.option.utils.OptionJson;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * OSM「结算」菜单组装（按页面 SettlementClient 规则，数据全部来自 /api/member/feed）
 * 
 * 已结算 record：history 中已了结的信号（resultKind ∈ EXPIRY/EARLY_TAKE_PROFIT 或 statusCode ∈ SETTLED/EARLY_TAKE_PROFIT/CLOSED）
 * + 已了结的中期影子卡（midtermShadowCards / midtermSettledCards），按发报时间倒序；每条附带所属的动态管理卡与保护记录
 * 结算单 card：settlementCards（到期结算/平仓指令），按发出时间倒序
 * 结算统计 stat：performance.strategyTracking 的结算盈亏/管理前对照 + 已结算信号的保护成本与动态管理成本
 * 
 * @author yscandice
 */
@Component
public class OptionSettlementAssembler
{
    /** 记录类型：卖方信号 */
    public static final String TYPE_SIGNAL = "signal";

    /** 记录类型：中期信号 */
    public static final String TYPE_MIDTERM = "midterm";

    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{2})/(\\d{2})");

    private static final Pattern ISO_DAY = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");

    private static final Pattern ET_TIME = Pattern.compile("(\\d{2})/(\\d{2})\\D+(\\d{1,2}):(\\d{2})");

    private static final Set<String> SETTLED_KINDS = Set.of("EXPIRY", "EARLY_TAKE_PROFIT");

    private static final Set<String> SETTLED_CODES = Set.of("SETTLED", "EARLY_TAKE_PROFIT", "CLOSED");

    private static final Set<String> MIDTERM_CLOSED = Set.of("CLOSED_TAKE_PROFIT", "CLOSED_EXIT", "EXPIRED");

    /**
     * 组装三类数据
     * 
     * @param feed feed 根节点
     * @return 各类型条目（record、card 已排序；stat 单条）
     */
    public Map<OptionSettlementKind, List<ObjectNode>> assemble(JsonNode feed)
    {
        List<ObjectNode> history = new ArrayList<>();
        for (ObjectNode item : objects(feed.get("history")))
        {
            if (settled(item))
            {
                history.add(item);
            }
        }
        List<ObjectNode> protections = new ArrayList<>();
        for (ObjectNode item : objects(feed.get("protections")))
        {
            if (!"eod".equals(text(item, "eventKind")))
            {
                protections.add(item);
            }
        }
        List<ObjectNode> managements = objects(feed.get("managementCards"));

        // 1、已结算记录
        List<ObjectNode> records = new ArrayList<>();
        for (ObjectNode raw : history)
        {
            records.add(record(raw, null, TYPE_SIGNAL, managements, protections));
        }
        for (ObjectNode card : midtermSettled(feed))
        {
            records.add(record((ObjectNode) card.get("sellerRow"), card, TYPE_MIDTERM, managements, protections));
        }
        records.sort(Comparator.comparingLong((ObjectNode r) -> r.path("sortAtMs").asLong()).reversed());

        // 2、结算单
        List<ObjectNode> cards = new ArrayList<>(objects(feed.get("settlementCards")));
        cards.sort(Comparator.comparingLong((ObjectNode c) -> OptionConductAssembler
                .parseMs(firstText(c, "emitted_at", "sentAtUtc"), 0)).reversed());

        // 3、结算统计（已结算集合只取 history 中的信号，与页面一致）
        Set<String> settledIds = new HashSet<>();
        for (ObjectNode raw : history)
        {
            for (String field : List.of("recommendationId", "signalId", "id"))
            {
                String value = text(raw, field);
                if (value != null)
                {
                    settledIds.add(value);
                }
            }
        }
        ObjectNode stat = stat(feed, protections, managements, settledIds);
        stat.put("recordCount", records.size());
        stat.put("signalRecordCount", history.size());
        stat.put("midtermRecordCount", records.size() - history.size());
        stat.put("cardCount", cards.size());

        Map<OptionSettlementKind, List<ObjectNode>> result = new EnumMap<>(OptionSettlementKind.class);
        result.put(OptionSettlementKind.RECORD, records);
        result.put(OptionSettlementKind.CARD, cards);
        result.put(OptionSettlementKind.STAT, new ArrayList<>(List.of(stat)));
        return result;
    }

    /**
     * 页面 rt：是否已了结
     */
    public static boolean settled(JsonNode item)
    {
        return SETTLED_KINDS.contains(StringUtils.nvl(text(item, "resultKind"), ""))
                || SETTLED_CODES.contains(StringUtils.nvl(text(item, "statusCode"), ""));
    }

    /**
     * 已了结的中期影子卡（页面 g：midtermShadowCards + midtermSettledCards 按 id 去重，只取影子层机会卡）
     */
    private static List<ObjectNode> midtermSettled(JsonNode feed)
    {
        Map<String, ObjectNode> cards = new LinkedHashMap<>();
        for (String field : List.of("midtermShadowCards", "midtermSettledCards"))
        {
            for (ObjectNode card : objects(feed.get(field)))
            {
                String id = text(card, "id");
                if (id != null && !cards.containsKey(id))
                {
                    cards.put(id, card);
                }
            }
        }
        List<ObjectNode> list = new ArrayList<>();
        for (ObjectNode card : cards.values())
        {
            JsonNode row = card.get("sellerRow");
            boolean shadow = "shadow".equals(text(card, "tier"))
                    && "osm.midterm-shadow-card.v1".equals(text(card, "schema"))
                    && isFalse(card, "tradePermission") && isFalse(card, "orderIntentAllowed")
                    && isFalse(card, "countsAsFormalSignal") && "OPPORTUNITY".equals(text(card, "cardKind"));
            if (!shadow || row == null || !row.isObject() || !StringUtils.equals(text(row, "id"), text(card, "id"))
                    || text(row, "structure") == null || text(row, "expiryDate") == null)
            {
                continue;
            }
            String stage = text(card.path("managementState"), "stage");
            if (settled(row) && stage != null && MIDTERM_CLOSED.contains(stage))
            {
                list.add(card);
            }
        }
        return list;
    }

    /**
     * 一条已结算记录：原始行 + 结算日/发报日/结算时间 + 所属动态管理卡与保护记录（页面 ArchiveBox）
     */
    private static ObjectNode record(ObjectNode raw, ObjectNode midterm, String type, List<ObjectNode> managements,
            List<ObjectNode> protections)
    {
        ObjectNode item = raw.deepCopy();
        item.put("recordType", type);
        item.put("settledDay", settledDay(raw));
        item.put("issuedDay", monthDay(text(raw, "emittedAt")));
        long settledMs = settledMs(raw);
        if (settledMs > 0)
        {
            item.put("settledAtUtc", java.time.Instant.ofEpochMilli(settledMs).toString());
        }
        else
        {
            item.putNull("settledAtUtc");
        }
        long at = OptionConductAssembler.emittedMs(raw);
        if (at == 0 && midterm != null)
        {
            at = OptionConductAssembler.parseMs(text(midterm, "issuedAtUtc"), 0);
        }
        item.put("sortAtMs", at);
        Set<String> keys = new LinkedHashSet<>();
        for (String field : List.of("id", "signalId", "recommendationId"))
        {
            String value = text(raw, field);
            if (value != null)
            {
                keys.add(value);
            }
        }
        if (midterm != null)
        {
            ObjectNode card = midterm.deepCopy();
            card.remove("sellerRow");
            item.set("midtermCard", card);
        }
        item.set("managements", belongs(managements, keys));
        item.set("protections", belongs(protections, keys));
        return item;
    }

    private static ArrayNode belongs(List<ObjectNode> entries, Set<String> keys)
    {
        ArrayNode list = OptionJson.MAPPER.createArrayNode();
        for (ObjectNode entry : entries)
        {
            String sid = text(entry, "signalId");
            String rid = text(entry, "recommendationId");
            if ((sid != null && keys.contains(sid)) || (rid != null && keys.contains(rid)))
            {
                list.add(entry);
            }
        }
        return list;
    }

    /**
     * 页面 st：结算统计
     */
    private static ObjectNode stat(JsonNode feed, List<ObjectNode> protections, List<ObjectNode> managements,
            Set<String> settledIds)
    {
        JsonNode tracking = feed.path("performance").path("strategyTracking");
        List<ObjectNode> ownProtections = new ArrayList<>();
        for (ObjectNode p : protections)
        {
            if (belongsTo(p, settledIds))
            {
                ownProtections.add(p);
            }
        }
        List<ObjectNode> ownManagements = new ArrayList<>();
        for (ObjectNode m : managements)
        {
            if (belongsTo(m, settledIds))
            {
                ownManagements.add(m);
            }
        }
        double protectionCost = 0;
        int protectionPriced = 0;
        for (ObjectNode p : ownProtections)
        {
            Double cost = number(p.get("entryCostUsd"));
            if (cost != null)
            {
                protectionCost += cost;
                protectionPriced++;
            }
        }
        double cost = 0;
        double credit = 0;
        int costCount = 0;
        int creditCount = 0;
        int priced = 0;
        for (ObjectNode m : ownManagements)
        {
            JsonNode paths = m.get("paths");
            Double net = paths != null && paths.isArray() && paths.size() > 0 ? number(paths.get(0).get("netUsd")) : null;
            if (net == null)
            {
                continue;
            }
            priced++;
            if (net < 0)
            {
                cost += net;
                costCount++;
            }
            else if (net > 0)
            {
                credit += net;
                creditCount++;
            }
        }
        Double settledPnl = number(tracking.get("settledPnlUsd"));
        Double rawPnl = number(tracking.get("settledRawPnlUsd"));
        ObjectNode stat = OptionJson.MAPPER.createObjectNode();
        stat.put("id", "strategy");
        putNumber(stat, "settledPnlUsd", settledPnl);
        stat.put("settledCount", tracking.path("settledTotal").isNumber() ? tracking.get("settledTotal").asInt() : 0);
        putNumber(stat, "rawPnlUsd", rawPnl);
        stat.put("rawPriced", tracking.path("settledRawPriced").isNumber() ? tracking.get("settledRawPriced").asInt() : 0);
        putNumber(stat, "manageNetUsd", settledPnl != null && rawPnl != null ? settledPnl - rawPnl : null);
        putNumber(stat, "protectionCostUsd", protectionPriced > 0 ? (double) Math.round(protectionCost) : null);
        stat.put("protectionPriced", protectionPriced);
        stat.put("protectionTotal", ownProtections.size());
        putNumber(stat, "manageCostUsd", costCount > 0 ? (double) Math.round(Math.abs(cost)) : null);
        putNumber(stat, "manageCreditUsd", creditCount > 0 ? (double) Math.round(credit) : null);
        stat.put("managePriced", priced);
        stat.put("manageTotal", ownManagements.size());
        return stat;
    }

    private static boolean belongsTo(JsonNode entry, Set<String> ids)
    {
        String rid = text(entry, "recommendationId");
        String sid = text(entry, "signalId");
        return (rid != null && ids.contains(rid)) || (sid != null && ids.contains(sid));
    }

    /**
     * 页面 at：结算日 MM/dd（resultObservedAt ?? updatedAt ?? emittedAt）
     */
    public static String settledDay(JsonNode item)
    {
        String source = firstText(item, "resultObservedAt", "updatedAt", "emittedAt");
        Matcher m = MONTH_DAY.matcher(StringUtils.nvl(source, ""));
        return m.find() ? m.group(1) + "/" + m.group(2) : "";
    }

    /**
     * 页面 tt 的日期部分：ISO 日期或 MM/dd 转 MM/dd
     */
    public static String monthDay(String text)
    {
        String value = StringUtils.nvl(text, "");
        Matcher iso = ISO_DAY.matcher(value);
        if (iso.find())
        {
            return iso.group(2) + "/" + iso.group(3);
        }
        Matcher m = MONTH_DAY.matcher(value);
        return m.find() ? m.group(1) + "/" + m.group(2) : "";
    }

    /**
     * 页面 tt：分组日期（issued 按发报日，否则按结算日）
     */
    public static String groupDay(JsonNode item, String sort)
    {
        String settled = text(item, "settledDay");
        String issued = text(item, "issuedDay");
        if ("issued".equals(sort))
        {
            return issued != null ? issued : StringUtils.nvl(settled, "");
        }
        return settled != null ? settled : StringUtils.nvl(issued, "");
    }

    /**
     * 结算时间：resultObservedAt（如 "10/16 · 16:00 ET"，美东时间，年份取到期日/发报日）
     */
    private static long settledMs(JsonNode item)
    {
        String source = firstText(item, "resultObservedAt", "updatedAt");
        Matcher m = ET_TIME.matcher(StringUtils.nvl(source, ""));
        if (!m.find())
        {
            return 0;
        }
        int year = yearOf(item);
        if (year <= 0)
        {
            return 0;
        }
        try
        {
            LocalDateTime local = LocalDateTime.of(LocalDate.of(year, Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2))), LocalTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))));
            return ZonedDateTime.of(local, OptionMarketCalendar.MARKET_ZONE).toInstant().toEpochMilli();
        }
        catch (RuntimeException e)
        {
            return 0;
        }
    }

    private static int yearOf(JsonNode item)
    {
        for (String field : List.of("expiryDate", "emittedAtUtc", "sellerSignalAt"))
        {
            Matcher iso = ISO_DAY.matcher(StringUtils.nvl(text(item, field), ""));
            if (iso.find())
            {
                return Integer.parseInt(iso.group(1));
            }
        }
        return 0;
    }

    /**
     * 区间（页面 it）：week 本周一至交易日 / month 本月 1 日至交易日 / custom 自定义 / 其余不限
     * 
     * @return [from, to]（MM/dd），不限返回 null
     */
    public static String[] range(String range, String marketDay, String from, String to)
    {
        if (StringUtils.isEmpty(range) || "all".equals(range))
        {
            return null;
        }
        if ("custom".equals(range))
        {
            String f = monthDay(from);
            String t = monthDay(to);
            return f.isEmpty() && t.isEmpty() ? null
                    : new String[] { f.isEmpty() ? "01/01" : f, t.isEmpty() ? "12/31" : t };
        }
        if (StringUtils.isEmpty(marketDay) || !marketDay.matches("^\\d{4}-\\d{2}-\\d{2}$"))
        {
            return null;
        }
        LocalDate day = LocalDate.parse(marketDay);
        LocalDate start = "month".equals(range) ? day.withDayOfMonth(1)
                : day.minusDays(day.getDayOfWeek().getValue() - 1L);
        return new String[] { String.format("%02d/%02d", start.getMonthValue(), start.getDayOfMonth()),
                String.format("%02d/%02d", day.getMonthValue(), day.getDayOfMonth()) };
    }

    /**
     * 计算内容哈希用的副本：递归去掉易变时间戳字段
     */
    public static JsonNode stable(JsonNode node)
    {
        JsonNode copy = node.deepCopy();
        strip(copy);
        return copy;
    }

    private static void strip(JsonNode node)
    {
        if (node.isObject())
        {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();)
            {
                Map.Entry<String, JsonNode> entry = it.next();
                if (OptionFeedConstants.isVolatileItemField(entry.getKey()))
                {
                    it.remove();
                }
                else
                {
                    strip(entry.getValue());
                }
            }
        }
        else if (node.isArray())
        {
            for (JsonNode child : node)
            {
                strip(child);
            }
        }
    }

    private static boolean isFalse(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() && !value.asBoolean();
    }

    static Double number(JsonNode value)
    {
        return value != null && value.isNumber() && Double.isFinite(value.asDouble()) ? value.asDouble() : null;
    }

    private static void putNumber(ObjectNode node, String field, Double value)
    {
        if (value == null)
        {
            node.putNull(field);
        }
        else if (value == Math.rint(value) && Math.abs(value) < 1e15)
        {
            node.put(field, value.longValue());
        }
        else
        {
            node.put(field, value);
        }
    }

    static String text(JsonNode node, String field)
    {
        return OptionConductAssembler.text(node, field);
    }

    static String firstText(JsonNode node, String... fields)
    {
        return OptionConductAssembler.firstText(node, fields);
    }

    private static List<ObjectNode> objects(JsonNode array)
    {
        List<ObjectNode> list = new ArrayList<>();
        if (array != null && array.isArray())
        {
            for (JsonNode node : array)
            {
                if (node.isObject())
                {
                    list.add((ObjectNode) node);
                }
            }
        }
        return list;
    }
}
