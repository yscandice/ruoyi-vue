package com.ruoyi.option.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.enums.OptionFollowKind;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「跟踪」菜单标记组装（规则与 /member/tracks 页面 TracksClient 一致）
 * 
 * 卖方信号：decisions.events 每个信号（recommendationId / signalId）取第一条，排除 decidedBy=autopilot_none，
 * 关联 feed signals + history（id / recommendationId / sourceEventId）得到系统参考估值；
 * 中期 / 买方 / 动态管理：management-choices.events 按 cardKind 拆分，每张卡取 occurredAt 最新的一条，
 * 分别关联 feed midtermShadowCards / buyerSignals / managementCards。
 * 
 * @author yscandice
 */
@Component
public class OptionFollowAssembler
{
    /** 标记类型：跟了 / 观察（旧） / 否决 */
    public static final String ADOPT_INTENT = "ADOPT_INTENT";

    public static final String OBSERVE = "OBSERVE";

    public static final String SKIP = "SKIP";

    private static final Map<String, String[]> MARKS = Map.of(ADOPT_INTENT, new String[] { "跟了", "adopt" },
            OBSERVE, new String[] { "观察（旧）", "observe" }, SKIP, new String[] { "否决", "skip" });

    /** management-choices.choice -> 标记类型 */
    private static final Map<String, String> CHOICES = Map.of("ADOPT", ADOPT_INTENT, "ADOPT_INTENT", ADOPT_INTENT,
            "WATCH", OBSERVE, "OBSERVE", OBSERVE, "REJECT", SKIP, "SKIP", SKIP);

    private static final Map<String, String> SKIP_REASONS = Map.of("IV_TOO_LOW", "IV 不够高", "PREMIUM_TOO_THIN",
            "权利金太薄", "TOO_CLOSE", "距短腿太近", "EVENT_RISK", "事件风险", "OTHER", "其他");

    private static final Set<String> SETTLED_RESULT = Set.of("EXPIRY", "EARLY_TAKE_PROFIT");

    private static final Pattern ISO_DAY = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");

    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{2})/(\\d{2})");

    private static final Pattern PNL = Pattern.compile("([+\\-−]?)\\s*\\$?([\\d,]+(?:\\.\\d+)?)");

    /**
     * 组装四类标记
     * 
     * @param feed feed 根节点
     * @param decisions /api/member/decisions 根节点
     * @param choices /api/member/management-choices 根节点
     * @return 各类标记（按标记时间倒序）
     */
    public Map<OptionFollowKind, List<ObjectNode>> assemble(JsonNode feed, JsonNode decisions, JsonNode choices)
    {
        String marketDay = OptionConductAssembler.marketDay(feed);
        Map<OptionFollowKind, List<ObjectNode>> result = new EnumMap<>(OptionFollowKind.class);
        result.put(OptionFollowKind.SIGNAL, signals(feed, decisions, marketDay));
        result.put(OptionFollowKind.MIDTERM, midterm(feed, choices, marketDay));
        result.put(OptionFollowKind.BUYER, buyer(feed, choices));
        result.put(OptionFollowKind.MANAGE, manage(feed, choices));
        return result;
    }

    /**
     * 卖方信号标记
     */
    private List<ObjectNode> signals(JsonNode feed, JsonNode decisions, String marketDay)
    {
        Map<String, JsonNode> pool = signalPool(feed);
        Set<String> seen = new HashSet<>();
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode event : array(decisions, "events"))
        {
            String key = or(str(event, "recommendationId"), str(event, "signalId"));
            if (seen.contains(key))
            {
                continue;
            }
            seen.add(key);
            if ("autopilot_none".equals(str(event, "decidedBy")))
            {
                continue;
            }
            ObjectNode mark = OptionJson.MAPPER.createObjectNode();
            mark.put("kind", OptionFollowKind.SIGNAL.getKey());
            mark.put("cardKind", OptionFollowKind.SIGNAL.getCardKind());
            mark.put("eventId", str(event, "eventId"));
            mark.put("signalId", str(event, "signalId"));
            mark.put("recommendationId", str(event, "recommendationId"));
            mark.put("symbol", str(event, "symbol"));
            mark.put("structure", str(event, "structure"));
            putMark(mark, str(event, "decisionType"));
            String reason = str(event, "skipReason");
            mark.put("skipReason", reason.isEmpty() ? null : reason);
            mark.put("skipReasonLabel", reason.isEmpty() ? null : SKIP_REASONS.getOrDefault(reason, reason));
            mark.put("occurredAt", str(event, "occurredAt"));
            JsonNode quantity = event.get("quantity");
            if (quantity != null && quantity.isNumber())
            {
                mark.put("quantity", quantity.asInt());
            }
            else
            {
                mark.putNull("quantity");
            }
            putAdoptMode(mark, adoptMode(str(event, "adoptMode")));
            JsonNode row = pool.get(mark.path("recommendationId").asText());
            if (row == null)
            {
                row = pool.get(mark.path("signalId").asText());
            }
            valuate(mark, row, false, marketDay);
            list.add(mark);
        }
        return sortByMarked(list);
    }

    /**
     * 中期信号标记（cardKind=midterm_signal，机器否决不计入）
     */
    private List<ObjectNode> midterm(JsonNode feed, JsonNode choices, String marketDay)
    {
        Map<String, JsonNode> cards = byId(array(feed, "midtermShadowCards"));
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode event : latestByCard(choices, OptionFollowKind.MIDTERM.getCardKind()))
        {
            String choice = str(event, "choice").toUpperCase();
            if (!"ADOPT".equals(choice) && !"REJECT".equals(choice) && !"WATCH".equals(choice))
            {
                continue;
            }
            if (event.path("overridable").asBoolean(false) && "REJECT".equals(choice))
            {
                continue;
            }
            String cardId = str(event, "cardId");
            JsonNode card = cards.get(cardId);
            ObjectNode mark = OptionJson.MAPPER.createObjectNode();
            mark.put("kind", OptionFollowKind.MIDTERM.getKey());
            mark.put("cardKind", OptionFollowKind.MIDTERM.getCardKind());
            mark.put("eventId", "midterm:" + cardId);
            mark.put("cardId", cardId);
            mark.put("signalId", cardId);
            mark.put("recommendationId", "");
            mark.put("symbol", or(str(event, "symbol"), card == null ? "" : str(card, "symbol")));
            mark.put("structure", "");
            putMark(mark, CHOICES.get(choice));
            mark.put("rawChoice", choice);
            mark.putNull("skipReason");
            mark.putNull("skipReasonLabel");
            mark.put("occurredAt", str(event, "occurredAt"));
            if ("ADOPT".equals(choice))
            {
                int lots = event.path("quantity").isInt() ? event.path("quantity").asInt() : 0;
                mark.put("quantity", lots > 0 ? lots : 1);
                putAdoptMode(mark, "AUTO".equals(str(event, "adoptMode")) ? "AUTO" : "MANUAL");
            }
            else
            {
                mark.putNull("quantity");
                putAdoptMode(mark, null);
            }
            JsonNode row = card == null ? null : card.get("sellerRow");
            valuate(mark, row != null && row.isObject() ? row : null, card != null, marketDay);
            mark.set("card", card == null ? null : card.deepCopy());
            list.add(mark);
        }
        return sortByMarked(list);
    }

    /**
     * 买方信号标记（cardKind=buyer_signal）
     */
    private List<ObjectNode> buyer(JsonNode feed, JsonNode choices)
    {
        Map<String, JsonNode> cards = byId(array(feed, "buyerSignals"));
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode event : latestByCard(choices, OptionFollowKind.BUYER.getCardKind()))
        {
            String choice = str(event, "choice").toUpperCase();
            String type = CHOICES.get(choice);
            if (type == null)
            {
                continue;
            }
            String cardId = str(event, "cardId");
            JsonNode card = cards.get(cardId);
            ObjectNode mark = cardMark(OptionFollowKind.BUYER, event, cardId, card, type, choice);
            mark.put("note", card == null ? "系统参考：该卡片不在当前快照窗口内，估值待复核" : "");
            list.add(mark);
        }
        return sortByMarked(list);
    }

    /**
     * 动态管理标记（cardKind=dynamic_management，可覆盖的否决不计入）
     */
    private List<ObjectNode> manage(JsonNode feed, JsonNode choices)
    {
        Map<String, JsonNode> cards = byId(array(feed, "managementCards"));
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode event : latestByCard(choices, OptionFollowKind.MANAGE.getCardKind()))
        {
            String choice = str(event, "choice").toUpperCase();
            if (event.path("overridable").asBoolean(false) && "REJECT".equals(choice))
            {
                continue;
            }
            String type = "ADOPT".equals(choice) ? ADOPT_INTENT : "WATCH".equals(choice) ? OBSERVE : SKIP;
            String cardId = str(event, "cardId");
            JsonNode card = cards.get(cardId);
            ObjectNode mark = cardMark(OptionFollowKind.MANAGE, event, cardId, card, type, choice);
            if (card != null)
            {
                mark.put("signalId", str(card, "signalId"));
                mark.put("recommendationId", str(card, "recommendationId"));
                mark.put("structure", str(card, "sourceStructure"));
                mark.put("expiryDate", day(str(card, "sourceExpiry")));
            }
            mark.put("note", card == null ? "系统参考：该管理卡不在当前快照窗口内" : "");
            list.add(mark);
        }
        return sortByMarked(list);
    }

    private ObjectNode cardMark(OptionFollowKind kind, JsonNode event, String cardId, JsonNode card, String type,
            String choice)
    {
        ObjectNode mark = OptionJson.MAPPER.createObjectNode();
        mark.put("kind", kind.getKey());
        mark.put("cardKind", kind.getCardKind());
        mark.put("eventId", or(str(event, "eventId"), kind.getKey() + ":" + cardId));
        mark.put("cardId", cardId);
        mark.put("symbol", or(str(event, "symbol"), card == null ? "" : str(card, "symbol")));
        putMark(mark, type);
        mark.put("rawChoice", choice);
        mark.put("occurredAt", str(event, "occurredAt"));
        mark.put("onScreen", card != null);
        mark.put("sortAt", sortAt(card, str(event, "occurredAt")));
        mark.put("issuedDay", issuedDay(card, str(event, "occurredAt")));
        mark.set("card", card == null ? null : card.deepCopy());
        return mark;
    }

    /**
     * 系统参考估值（页面 N 函数）：分类、现价标记、浮动盈亏、状态、提示与结算日
     */
    private void valuate(ObjectNode mark, JsonNode row, boolean onScreen, String marketDay)
    {
        String category = category(row);
        String floating = row == null ? "" : str(row, "floatingPnl");
        floating = floating.isEmpty() ? "—" : floating;
        String state = row == null ? "" : str(row, "state");
        if (state.isEmpty())
        {
            state = row != null || onScreen ? "—" : "估值待复核";
        }
        String structure = mark.path("structure").asText("");
        if (structure.isEmpty() && row != null)
        {
            mark.put("structure", str(row, "structure"));
        }
        String settledDay = row != null && settled(row)
                ? or(monthDay(str(row, "resultObservedAt")), monthDay(str(row, "updatedAt")))
                : "";
        mark.put("category", category);
        mark.put("currentMark", row == null || str(row, "currentMark").isEmpty() ? "—" : str(row, "currentMark"));
        mark.put("floatingPnl", floating);
        Double pnl = parsePnl(floating);
        if (pnl == null)
        {
            mark.putNull("pnlUsd");
        }
        else
        {
            mark.put("pnlUsd", pnl);
        }
        mark.put("state", state);
        mark.put("note", note(row != null, onScreen, category, row == null ? "" : str(row, "result"), floating));
        mark.put("settledDay", settledDay);
        String today = monthDay(marketDay);
        // 已了结（结算日不是当前交易日）的标记页面不在列表展示，但计入参考盈亏
        mark.put("archived", !today.isEmpty() && !settledDay.isEmpty() && !settledDay.equals(today));
        mark.put("onScreen", row != null || onScreen);
        mark.put("expiryDate", row == null ? "" : day(str(row, "expiryDate")));
        mark.put("sortAt", sortAt(row, mark.path("occurredAt").asText("")));
        mark.put("issuedDay", issuedDay(row, mark.path("occurredAt").asText("")));
        mark.set("signal", row == null ? null : row.deepCopy());
    }

    private static String category(JsonNode row)
    {
        if (row == null)
        {
            return "unknown";
        }
        if (SETTLED_RESULT.contains(str(row, "resultKind")) || row.path("settled").asBoolean(false))
        {
            return "settled";
        }
        if (str(row, "statusCode").startsWith("TAKE_PROFIT"))
        {
            return "profit";
        }
        JsonNode orders = row.get("protectionOrders");
        if ("protection".equals(str(row, "category")) || row.path("protectionExecuted").asBoolean(false)
                || (orders != null && orders.isArray() && orders.size() > 0))
        {
            return "protect";
        }
        return "new";
    }

    private static boolean settled(JsonNode row)
    {
        String resultKind = str(row, "resultKind");
        String status = str(row, "statusCode");
        return SETTLED_RESULT.contains(resultKind) || "SETTLED".equals(status) || "EARLY_TAKE_PROFIT".equals(status)
                || "CLOSED".equals(status);
    }

    private static String note(boolean hasRow, boolean onScreen, String category, String result, String floating)
    {
        if (!hasRow)
        {
            return onScreen ? "" : "系统参考：该信号不在当前快照窗口内，估值待复核";
        }
        switch (category)
        {
            case "settled":
                return "系统参考：已结算 · " + (result.isEmpty() ? "结果见战绩" : result);
            case "protect":
                return "系统参考：保护窗口已出现，见信号卡";
            case "profit":
                return "系统参考：止盈条件已确认，见信号卡";
            default:
                return "—".equals(floating) ? "系统参考：暂无中位价估值" : "";
        }
    }

    /**
     * 汇总：标记计数（卖方 + 中期 + 买方，与页面「全部」一致）与「跟了」信号的参考盈亏
     * 
     * @param kinds 四类标记
     * @return marks、tally
     */
    public static ObjectNode stats(Map<OptionFollowKind, List<ObjectNode>> kinds)
    {
        Map<String, Integer> marks = new LinkedHashMap<>();
        marks.put("ALL", 0);
        marks.put(ADOPT_INTENT, 0);
        marks.put(OBSERVE, 0);
        marks.put(SKIP, 0);
        double open = 0;
        double closed = 0;
        int openN = 0;
        int closedN = 0;
        int unknown = 0;
        int archived = 0;
        for (OptionFollowKind kind : List.of(OptionFollowKind.SIGNAL, OptionFollowKind.MIDTERM,
                OptionFollowKind.BUYER))
        {
            for (ObjectNode mark : kinds.getOrDefault(kind, List.of()))
            {
                String type = mark.path("decisionType").asText("");
                marks.merge("ALL", 1, Integer::sum);
                marks.computeIfPresent(type, (k, v) -> v + 1);
                if (kind == OptionFollowKind.BUYER)
                {
                    continue;
                }
                if (mark.path("archived").asBoolean(false))
                {
                    archived++;
                }
                if (!ADOPT_INTENT.equals(type))
                {
                    continue;
                }
                JsonNode pnl = mark.get("pnlUsd");
                if (pnl == null || !pnl.isNumber())
                {
                    unknown++;
                }
                else if ("settled".equals(mark.path("category").asText()))
                {
                    closedN++;
                    closed += pnl.asDouble();
                }
                else
                {
                    openN++;
                    open += pnl.asDouble();
                }
            }
        }
        ObjectNode stats = OptionJson.MAPPER.createObjectNode();
        stats.set("marks", OptionJson.MAPPER.valueToTree(marks));
        ObjectNode tally = stats.putObject("tally");
        tally.put("open", open);
        tally.put("openN", openN);
        tally.put("closed", closed);
        tally.put("closedN", closedN);
        tally.put("unknown", unknown);
        tally.put("archived", archived);
        return stats;
    }

    private static void putMark(ObjectNode mark, String type)
    {
        String[] meta = MARKS.get(type);
        mark.put("decisionType", type == null ? "" : type);
        mark.put("decisionLabel", meta == null ? "" : meta[0]);
        mark.put("markCls", meta == null ? "" : meta[1]);
    }

    private static void putAdoptMode(ObjectNode mark, String mode)
    {
        mark.put("adoptMode", mode);
        mark.put("adoptModeLabel", mode == null ? null : "AUTO".equals(mode) ? "自动驾驶" : "手动");
    }

    private static String adoptMode(String mode)
    {
        return "AUTO".equals(mode) || "MANUAL".equals(mode) ? mode : null;
    }

    /**
     * management-choices.events 中某类卡片：每张卡取 occurredAt 最新的一条（相同时间取先出现的）
     */
    private static List<JsonNode> latestByCard(JsonNode choices, String cardKind)
    {
        Map<String, JsonNode> latest = new LinkedHashMap<>();
        for (JsonNode event : array(choices, "events"))
        {
            if (!cardKind.equals(str(event, "cardKind")))
            {
                continue;
            }
            String cardId = str(event, "cardId");
            if (cardId.isEmpty())
            {
                continue;
            }
            JsonNode before = latest.get(cardId);
            if (before != null && str(before, "occurredAt").compareTo(str(event, "occurredAt")) >= 0)
            {
                continue;
            }
            latest.put(cardId, event);
        }
        return new ArrayList<>(latest.values());
    }

    private static Map<String, JsonNode> signalPool(JsonNode feed)
    {
        Map<String, JsonNode> pool = new HashMap<>();
        List<JsonNode> rows = new ArrayList<>();
        array(feed, "signals").forEach(rows::add);
        array(feed, "history").forEach(rows::add);
        for (JsonNode row : rows)
        {
            for (String field : new String[] { "id", "recommendationId", "sourceEventId" })
            {
                String id = str(row, field);
                if (!id.isEmpty())
                {
                    pool.putIfAbsent(id, row);
                }
            }
        }
        return pool;
    }

    private static Map<String, JsonNode> byId(Iterable<JsonNode> array)
    {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode item : array)
        {
            String id = str(item, "id");
            if (!id.isEmpty())
            {
                map.putIfAbsent(id, item);
            }
        }
        return map;
    }

    private static long sortAt(JsonNode row, String occurredAt)
    {
        long ms = 0;
        if (row != null)
        {
            ms = OptionConductAssembler.parseMs(OptionConductAssembler.firstText(row, "emittedAtUtc", "issuedAtUtc",
                    "openedAtUtc", "observedAtUtc"), 0);
        }
        return ms > 0 ? ms : OptionConductAssembler.parseMs(occurredAt, 0);
    }

    private static String issuedDay(JsonNode row, String occurredAt)
    {
        long ms = sortAt(row, occurredAt);
        return ms > 0 ? OptionConductAssembler.etDay(ms) : "";
    }

    private static List<ObjectNode> sortByMarked(List<ObjectNode> list)
    {
        list.sort(Comparator.comparing((ObjectNode n) -> n.path("occurredAt").asText("")).reversed());
        return list;
    }

    /**
     * 解析 "+$1,234" / "−$56" 形式的金额
     */
    public static Double parsePnl(String text)
    {
        Matcher m = PNL.matcher(or(text, ""));
        if (!m.find())
        {
            return null;
        }
        try
        {
            double n = Double.parseDouble(m.group(2).replace(",", ""));
            return "-".equals(m.group(1)) || "−".equals(m.group(1)) ? -n : n;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static String monthDay(String text)
    {
        String value = or(text, "");
        Matcher iso = ISO_DAY.matcher(value);
        if (iso.find())
        {
            return iso.group(2) + "/" + iso.group(3);
        }
        Matcher md = MONTH_DAY.matcher(value);
        return md.find() ? md.group(1) + "/" + md.group(2) : "";
    }

    private static String day(String text)
    {
        return text != null && ISO_DAY.matcher(text).find() ? text.substring(0, 10) : "";
    }

    private static Iterable<JsonNode> array(JsonNode node, String field)
    {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isArray() ? value : List.of();
    }

    private static String or(String value, String fallback)
    {
        return StringUtils.isEmpty(value) ? (fallback == null ? "" : fallback) : value;
    }

    static String str(JsonNode node, String field)
    {
        String value = OptionConductAssembler.text(node, field);
        return value == null ? "" : value;
    }
}
