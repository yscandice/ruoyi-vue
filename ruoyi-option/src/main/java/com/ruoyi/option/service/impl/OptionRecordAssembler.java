package com.ruoyi.option.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.option.enums.OptionRecordKind;
import com.ruoyi.option.utils.OptionJson;

/**
 * OSM「战绩」菜单组装（按页面 ScorecardClient 规则：feed.performance / feed.history + /api/member/decisions）
 * 
 * 战绩明细 trade：history 中 resultKind ∈ EXPIRY/EARLY_TAKE_PROFIT 的信号，管理后净结果取 spreadMarks 最后一个
 * EXPIRY 节点的 performance_net_pnl_usd（不计入汇总时为空）?? finalNetPnlUsd；结算日按 resultObservedAt 推年份；
 * 附我跟了的组数（decisions.latest ?? events 中 ADOPT_INTENT 的 quantity）
 * 每日结算净额 daily：按结算日归集的管理后净额 + spreadMarks DAILY_CLOSE 的当日浮动盈亏（今日取 strategyTracking.livePnlUsd）
 * 按标的 symbol：strategyTracking.symbols（all）/ monthSymbols（month）
 * 战绩统计 stat：all / month / week 汇总 + management 管理战绩 + midterm 中期 + buyer 买方 + accountSim 账户模拟说明
 * 账户模拟 account：strategyTracking.accountSim.accounts，按账户规模升序
 * 
 * @author yscandice
 */
@Component
public class OptionRecordAssembler
{
    /** 统计区间：全部 */
    public static final String ALL = "all";

    /** 统计区间：本月 */
    public static final String MONTH = "month";

    /** 统计区间：本周 */
    public static final String WEEK = "week";

    private static final Set<String> SETTLED_KINDS = Set.of("EXPIRY", "EARLY_TAKE_PROFIT");

    private static final Map<String, String> OUTCOME_LABELS = Map.of("EXPIRY", "到期结算", "EARLY_TAKE_PROFIT", "提前止盈");

    private static final Pattern OBSERVED = Pattern.compile("^(\\d{2})/(\\d{2})(?:\\D+(\\d{1,2}):(\\d{2}))?");

    private static final Pattern ISO_MONTH = Pattern.compile("^(\\d{4})-(\\d{2})");

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    /**
     * 组装战绩菜单 5 个类型
     * 
     * @param feed /api/member/feed 根节点
     * @param decisions /api/member/decisions 根节点（可为空）
     * @return 类型 -> 条目
     */
    public Map<OptionRecordKind, List<ObjectNode>> assemble(JsonNode feed, JsonNode decisions)
    {
        String marketDay = OptionConductAssembler.marketDay(feed);
        String monthKey = marketDay.length() >= 7 ? marketDay.substring(0, 7) : "";
        JsonNode performance = feed.path("performance");
        JsonNode tracking = performance.path("strategyTracking");
        String sampleStart = text(performance, "sampleStart");
        Map<String, Integer> lots = lots(decisions);

        List<ObjectNode> trades = trades(feed.path("history"), marketDay, lots);
        Map<OptionRecordKind, List<ObjectNode>> result = new EnumMap<>(OptionRecordKind.class);
        result.put(OptionRecordKind.TRADE, trades);
        result.put(OptionRecordKind.DAILY, daily(feed.path("history"), trades, marketDay, sampleStart, tracking, lots));
        result.put(OptionRecordKind.SYMBOL, symbols(tracking));
        result.put(OptionRecordKind.STAT, stats(feed, trades, marketDay, monthKey, sampleStart, lots));
        result.put(OptionRecordKind.ACCOUNT, accounts(tracking));
        return result;
    }

    // ------------------------------------------------------------------ 战绩明细

    private List<ObjectNode> trades(JsonNode history, String marketDay, Map<String, Integer> lots)
    {
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode item : iterable(history))
        {
            String kind = str(item, "resultKind");
            if (!SETTLED_KINDS.contains(kind))
            {
                continue;
            }
            ObjectNode row = OptionJson.MAPPER.createObjectNode();
            row.put("id", str(item, "id"));
            row.put("symbol", str(item, "symbol"));
            row.put("company", str(item, "company"));
            row.put("intent", str(item, "intent").toUpperCase());
            row.put("structure", str(item, "structure"));
            row.put("expiry", str(item, "expiry"));
            row.put("expiryDate", str(item, "expiryDate"));
            String observed = str(item, "resultObservedAt");
            String day = observedDay(observed, marketDay);
            row.put("day", day);
            row.put("resultObservedAt", observed);
            row.put("settledAtUtc", observedUtc(observed, day));
            Double mid = number(item.path("entryPricing").get("midpointCredit"));
            putNumber(row, "premiumUsd", mid == null ? null : (double) Math.round(mid * 100));
            String maxRisk = str(item, "maxRisk");
            row.put("maxRisk", maxRisk.isEmpty() ? "—" : maxRisk);
            putNumber(row, "holdingDays", number(item.get("holdingTradingDays")));
            putNumber(row, "protectionWindows", number(item.get("protectionWindowCount")));
            row.put("outcome", kind);
            ObjectNode close = executedClose(item);
            row.set("executedClose", close);
            row.put("outcomeLabel", close != null
                    ? "已执行平仓 · " + (close.path("familyCn").asText().isEmpty() ? "平仓" : close.path("familyCn").asText())
                    : OUTCOME_LABELS.get(kind));
            Double pnl = managedPnl(item);
            putNumber(row, "pnl", pnl);
            putNumber(row, "rawPnl", number(item.get("researchPnlUsd")));
            putNumber(row, "protectionCostUsd", number(item.get("protectionCostUsd")));
            row.put("pendingReview", pnl == null);
            row.put("lifecycleBasis", str(item, "lifecyclePnlBasis"));
            row.put("excluded", item.path("excludedFromPerformance").isBoolean()
                    && item.path("excludedFromPerformance").asBoolean());
            row.put("exclusionReason", str(item, "auditExclusionReason"));
            putNumber(row, "returnOnRiskPct", number(item.get("returnOnRiskPct")));
            putNumber(row, "annualizedPct", number(item.get("annualizedReturnPct")));
            row.put("sellerReason", str(item, "sellerReason"));
            row.put("recommendationId", str(item, "recommendationId"));
            row.put("sourceEventId", str(item, "sourceEventId"));
            Integer lot = lot(row, lots);
            if (lot == null)
            {
                row.putNull("adoptedLots");
            }
            else
            {
                row.put("adoptedLots", lot);
            }
            list.add(row);
        }
        // 结算日倒序（稳定排序，同日保持 history 原顺序）
        list.sort((a, b) -> b.path("day").asText().compareTo(a.path("day").asText()));
        return list;
    }

    /**
     * 管理后净结果：spreadMarks 最后一个 EXPIRY 节点的 performance_net_pnl_usd（标记不计入汇总时为空），否则 finalNetPnlUsd
     */
    private static Double managedPnl(JsonNode item)
    {
        JsonNode marks = item.path("spreadMarks");
        if (marks.isArray())
        {
            for (int i = marks.size() - 1; i >= 0; i--)
            {
                JsonNode mark = marks.get(i);
                if (!"EXPIRY".equals(str(mark, "mark_kind")))
                {
                    continue;
                }
                Double value = number(mark.get("performance_net_pnl_usd"));
                if (value == null)
                {
                    break;
                }
                JsonNode eligible = mark.get("performance_aggregate_eligible");
                return eligible != null && eligible.isBoolean() && !eligible.asBoolean() ? null : value;
            }
        }
        return number(item.get("finalNetPnlUsd"));
    }

    private static ObjectNode executedClose(JsonNode item)
    {
        JsonNode close = item.get("executedClose");
        if (close == null || !close.isObject())
        {
            return null;
        }
        Double pnl = number(close.get("pnlUsd"));
        if (pnl == null)
        {
            return null;
        }
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        node.put("familyCn", str(close, "familyCn"));
        putNumber(node, "pnlUsd", pnl);
        putNumber(node, "fillPrice", number(close.get("fillPrice")));
        return node;
    }

    /**
     * resultObservedAt（MM/dd · HH:mm ET）推出结算日：月份比交易日月份大 1 以上视为上一年
     */
    public static String observedDay(String observed, String marketDay)
    {
        Matcher m = OBSERVED.matcher(observed == null ? "" : observed.trim());
        Matcher r = ISO_MONTH.matcher(marketDay == null ? "" : marketDay);
        if (!m.find() || !r.find())
        {
            return "";
        }
        int year = Integer.parseInt(r.group(1));
        if (Integer.parseInt(m.group(1)) > Integer.parseInt(r.group(2)) + 1)
        {
            year--;
        }
        return year + "-" + m.group(1) + "-" + m.group(2);
    }

    private static String observedUtc(String observed, String day)
    {
        Matcher m = OBSERVED.matcher(observed == null ? "" : observed.trim());
        if (day.isEmpty() || !m.find() || m.group(3) == null)
        {
            return null;
        }
        try
        {
            LocalDateTime local = LocalDate.parse(day).atTime(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            return local.atZone(NEW_YORK).toInstant().toString();
        }
        catch (java.time.DateTimeException e)
        {
            return null;
        }
    }

    // ------------------------------------------------------------------ 我跟了的

    /**
     * decisions.latest ?? decisions.events 中 ADOPT_INTENT：recommendationId / signalId -> 组数（quantity，至少 1）
     */
    private static Map<String, Integer> lots(JsonNode decisions)
    {
        Map<String, Integer> lots = new LinkedHashMap<>();
        if (decisions == null)
        {
            return lots;
        }
        JsonNode list = decisions.get("latest");
        if (list == null || list.isNull())
        {
            list = decisions.get("events");
        }
        for (JsonNode event : iterable(list))
        {
            if (!"ADOPT_INTENT".equals(str(event, "decisionType")))
            {
                continue;
            }
            JsonNode q = event.get("quantity");
            int quantity = q != null && q.isNumber() && q.asDouble() >= 1 ? (int) q.asDouble() : 1;
            for (String field : new String[] { "recommendationId", "signalId" })
            {
                String id = str(event, field);
                if (!id.isEmpty())
                {
                    lots.put(id, quantity);
                }
            }
        }
        return lots;
    }

    private static Integer lot(JsonNode trade, Map<String, Integer> lots)
    {
        for (String field : new String[] { "recommendationId", "sourceEventId", "id" })
        {
            String id = str(trade, field);
            if (!id.isEmpty() && lots.containsKey(id))
            {
                return lots.get(id);
            }
        }
        return null;
    }

    /**
     * 我跟了的口径：金额按组数放大
     */
    public static ObjectNode scale(JsonNode trade, int lots)
    {
        ObjectNode copy = trade.deepCopy();
        if (lots == 1)
        {
            return copy;
        }
        for (String field : new String[] { "pnl", "rawPnl", "premiumUsd", "protectionCostUsd" })
        {
            Double value = number(trade.get(field));
            putNumber(copy, field, value == null ? null : value * lots);
        }
        return copy;
    }

    // ------------------------------------------------------------------ 每日结算净额

    private List<ObjectNode> daily(JsonNode history, List<ObjectNode> trades, String marketDay, String sampleStart,
            JsonNode tracking, Map<String, Integer> lots)
    {
        Map<String, double[]> settled = buckets(trades, "day", t -> true);
        Map<String, double[]> adopted = buckets(adoptedTrades(trades), "day", t -> true);
        Set<String> days = new TreeSet<>();
        Map<String, double[]> floats = floats(history, marketDay, sampleStart, null, days);
        Map<String, double[]> adoptedFloats = floats(history, marketDay, sampleStart, lots.keySet(), new TreeSet<>());
        days.addAll(settled.keySet());
        Double live = number(tracking.get("livePnlUsd"));
        List<ObjectNode> list = new ArrayList<>();
        for (String day : days)
        {
            ObjectNode row = OptionJson.MAPPER.createObjectNode();
            row.put("day", day);
            row.put("week", period(day, WEEK));
            row.put("month", period(day, MONTH));
            fillDay(row, settled.get(day), floats.get(day), day.equals(marketDay) ? live : null);
            ObjectNode mine = row.putObject("adopted");
            fillDay(mine, adopted.get(day), adoptedFloats.get(day), null);
            list.add(row);
        }
        return list;
    }

    private static void fillDay(ObjectNode row, double[] settled, double[] floating, Double liveToday)
    {
        putNumber(row, "settledPnlUsd", settled == null || settled[1] == 0 ? null : settled[0]);
        row.put("settledCount", settled == null ? 0 : (int) settled[1]);
        row.put("missingCount", settled == null ? 0 : (int) settled[2]);
        if (floating != null)
        {
            putNumber(row, "floatPnlUsd", floating[0]);
            row.put("openCount", (int) floating[1]);
            row.put("intraday", false);
        }
        else if (liveToday != null)
        {
            putNumber(row, "floatPnlUsd", liveToday);
            row.put("openCount", 0);
            row.put("intraday", true);
        }
        else
        {
            row.putNull("floatPnlUsd");
            row.put("openCount", 0);
            row.put("intraday", false);
        }
    }

    /**
     * 当日浮动盈亏：spreadMarks DAILY_CLOSE（样本起点之后、结算日之前）的 performance_net_pnl_usd ?? net_pnl_usd 求和
     * 
     * @param ids 非空时只统计我跟了的信号
     * @param days 输出全部出现过的标记日 + 交易日
     * @return 日期 -> [浮动盈亏, 在途组数]
     */
    private static Map<String, double[]> floats(JsonNode history, String marketDay, String sampleStart, Set<String> ids,
            Set<String> days)
    {
        Map<String, double[]> map = new HashMap<>();
        for (JsonNode item : iterable(history))
        {
            if (item.path("excludedFromPerformance").isBoolean() && item.path("excludedFromPerformance").asBoolean())
            {
                continue;
            }
            if (ids != null && !(ids.contains(str(item, "recommendationId")) || ids.contains(str(item, "sourceEventId"))
                    || ids.contains(str(item, "id"))))
            {
                continue;
            }
            String kind = str(item, "resultKind");
            String settledDay = SETTLED_KINDS.contains(kind) ? observedDay(str(item, "resultObservedAt"), marketDay) : "";
            for (JsonNode mark : iterable(item.get("spreadMarks")))
            {
                if (!"DAILY_CLOSE".equals(str(mark, "mark_kind")))
                {
                    continue;
                }
                String day = str(mark, "mark_date_et");
                if (day.isEmpty() || (!sampleStart.isEmpty() && day.compareTo(sampleStart) < 0))
                {
                    continue;
                }
                days.add(day);
                if (!settledDay.isEmpty() && day.compareTo(settledDay) >= 0)
                {
                    continue;
                }
                Double value = number(mark.get("performance_net_pnl_usd"));
                if (value == null)
                {
                    value = number(mark.get("net_pnl_usd"));
                }
                if (value == null)
                {
                    continue;
                }
                double[] sum = map.computeIfAbsent(day, k -> new double[2]);
                sum[0] += value;
                sum[1] += 1;
            }
        }
        if (!marketDay.isEmpty())
        {
            days.add(marketDay);
        }
        return map;
    }

    /**
     * 按周期归集已结算（排除不计入战绩的）：key -> [净额, 有结果笔数, 待复核笔数]
     */
    public static Map<String, double[]> buckets(List<? extends JsonNode> trades, String granularity,
            Predicate<JsonNode> filter)
    {
        Map<String, double[]> map = new TreeMap<>();
        for (JsonNode trade : trades)
        {
            if (trade.path("excluded").asBoolean() || !filter.test(trade))
            {
                continue;
            }
            String key = period(trade.path("day").asText(), granularity);
            if (key.isEmpty())
            {
                continue;
            }
            double[] sum = map.computeIfAbsent(key, k -> new double[3]);
            Double pnl = number(trade.get("pnl"));
            if (pnl == null)
            {
                sum[2] += 1;
            }
            else
            {
                sum[0] += pnl;
                sum[1] += 1;
            }
        }
        return map;
    }

    /**
     * 日期归到周期：day 原样，week 为所在周周一，month 为 yyyy-MM
     */
    public static String period(String day, String granularity)
    {
        if (day == null || day.isEmpty() || "day".equals(granularity))
        {
            return day == null ? "" : day;
        }
        if (MONTH.equals(granularity))
        {
            return day.length() >= 7 ? day.substring(0, 7) : day;
        }
        try
        {
            LocalDate date = LocalDate.parse(day);
            return date.minusDays(date.getDayOfWeek().getValue() - 1L).toString();
        }
        catch (DateTimeParseException e)
        {
            return day;
        }
    }

    private static List<ObjectNode> adoptedTrades(List<ObjectNode> trades)
    {
        List<ObjectNode> list = new ArrayList<>();
        for (ObjectNode trade : trades)
        {
            JsonNode lots = trade.get("adoptedLots");
            if (lots != null && lots.isNumber())
            {
                list.add(scale(trade, lots.asInt()));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ 按标的

    private static List<ObjectNode> symbols(JsonNode tracking)
    {
        List<ObjectNode> list = new ArrayList<>();
        for (String scope : new String[] { ALL, MONTH })
        {
            List<ObjectNode> rows = new ArrayList<>();
            for (JsonNode item : iterable(tracking.get(ALL.equals(scope) ? "symbols" : "monthSymbols")))
            {
                ObjectNode row = OptionJson.MAPPER.createObjectNode();
                String symbol = str(item, "symbol");
                row.put("id", scope + ":" + (symbol.isEmpty() ? "—" : symbol));
                row.put("scope", scope);
                row.put("symbol", symbol.isEmpty() ? "—" : symbol);
                putNumber(row, "pnlUsd", zero(item.get("pnlUsd")));
                putNumber(row, "settledPnlUsd", zero(item.get("settledPnlUsd")));
                putNumber(row, "livePnlUsd", zero(item.get("livePnlUsd")));
                putNumber(row, "settledGroups", zero(item.get("settledGroups")));
                putNumber(row, "liveGroups", zero(item.get("liveGroups")));
                rows.add(row);
            }
            rows.sort(Comparator.comparingDouble((ObjectNode r) -> r.path("pnlUsd").asDouble()).reversed());
            list.addAll(rows);
        }
        return list;
    }

    // ------------------------------------------------------------------ 战绩统计

    private static List<ObjectNode> stats(JsonNode feed, List<ObjectNode> trades, String marketDay, String monthKey,
            String sampleStart, Map<String, Integer> lots)
    {
        JsonNode performance = feed.path("performance");
        JsonNode tracking = performance.path("strategyTracking");
        List<ObjectNode> list = new ArrayList<>();

        // 全部：终端口径 + 明细统计 + 研究口径
        ObjectNode all = stat(ALL, "全部正式信号", marketDay, sampleStart, tracking);
        all.set("tracking", trackingAll(tracking, performance));
        all.set("research", research(performance, true));
        fillSummary(all, trades, lots);
        list.add(all);

        // 本月
        ObjectNode month = stat(MONTH, "本月", marketDay, sampleStart, tracking);
        month.put("monthKey", monthKey);
        month.set("tracking", period(tracking.path("month")));
        month.set("research", research(performance.path("month"), false));
        List<ObjectNode> monthTrades = new ArrayList<>();
        for (ObjectNode trade : trades)
        {
            if (!monthKey.isEmpty() && trade.path("day").asText().startsWith(monthKey))
            {
                monthTrades.add(trade);
            }
        }
        fillSummary(month, monthTrades, lots);
        list.add(month);

        // 本周（终端口径）
        ObjectNode week = stat(WEEK, "本周", marketDay, sampleStart, tracking);
        week.set("tracking", period(tracking.path("week")));
        week.set("research", research(performance.path("week"), false));
        list.add(week);

        list.add(management(tracking.path("management"), marketDay));
        list.add(midterm(tracking.path("midterm"), marketDay));
        list.add(buyer(feed.path("buyerLifecycle"), marketDay));
        list.add(accountSim(tracking.path("accountSim"), marketDay));
        return list;
    }

    private static ObjectNode stat(String id, String label, String marketDay, String sampleStart, JsonNode tracking)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        node.put("id", id);
        node.put("scope", id);
        node.put("label", label);
        node.put("marketDay", marketDay);
        node.put("sampleStart", sampleStart);
        node.put("asOfEt", str(tracking, "asOfEt"));
        return node;
    }

    private static ObjectNode trackingAll(JsonNode n, JsonNode performance)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        String start = str(n, "sampleStart");
        node.put("start", start.isEmpty() ? str(performance, "sampleStart") : start);
        node.put("end", str(n, "asOfEt"));
        putNumber(node, "total", zero(n.get("eligibleTotal")));
        putNumber(node, "priced", zero(n.get("pricedTotal")));
        putNumber(node, "winners", zero(n.get("winners")));
        putNumber(node, "winRate", number(n.get("winRate")));
        putNumber(node, "managedPnlUsd", number(n.get("managedPnlUsd")));
        putNumber(node, "rawPnlUsd", number(n.get("rawPnlUsd")));
        putNumber(node, "settledPnlUsd", number(n.get("settledPnlUsd")));
        putNumber(node, "settledRawPnlUsd", number(n.get("settledRawPnlUsd")));
        putNumber(node, "settledPriced", zero(n.get("settledPriced")));
        putNumber(node, "premiumUsd", number(n.get("premiumUsd")));
        putNumber(node, "entriesTotal", zero(n.get("entriesTotal")));
        putNumber(node, "eligibleTotal", zero(n.get("eligibleTotal")));
        putNumber(node, "auditExcluded", zero(n.get("auditExcluded")));
        putNumber(node, "managementPending", zero(n.get("managementPending")));
        putNumber(node, "preSampleExcluded", number(n.get("preSampleExcluded")));
        putNumber(node, "liveTotal", zero(n.get("liveTotal")));
        putNumber(node, "livePriced", zero(n.get("livePriced")));
        putNumber(node, "livePnlUsd", number(n.get("livePnlUsd")));
        putNumber(node, "settledTotal", zero(n.get("settledTotal")));
        putNumber(node, "managedClosures", number(n.get("managedClosures")));
        putNumber(node, "managedClosurePnlUsd", number(n.get("managedClosurePnlUsd")));
        return node;
    }

    private static ObjectNode period(JsonNode n)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        node.put("start", str(n, "start"));
        node.put("end", str(n, "end"));
        putNumber(node, "total", zero(n.get("total")));
        putNumber(node, "priced", zero(n.get("priced")));
        putNumber(node, "winners", zero(n.get("winners")));
        putNumber(node, "winRate", number(n.get("winRate")));
        putNumber(node, "managedPnlUsd", number(n.get("managedPnlUsd")));
        putNumber(node, "rawPnlUsd", number(n.get("rawPnlUsd")));
        putNumber(node, "settledPnlUsd", number(n.get("settledPnlUsd")));
        putNumber(node, "settledPriced", zero(n.get("settledPriced")));
        putNumber(node, "premiumUsd", number(n.get("premiumUsd")));
        return node;
    }

    /**
     * 研究口径（performance 根节点 / month / week，不含 strategyTracking 等嵌套对象）
     */
    private static ObjectNode research(JsonNode n, boolean root)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();)
        {
            Map.Entry<String, JsonNode> entry = it.next();
            if (!entry.getValue().isContainerNode())
            {
                node.set(entry.getKey(), entry.getValue());
            }
        }
        if (root)
        {
            node.remove("asOfEt");
        }
        return node;
    }

    /**
     * 明细统计（页面 summary / compare）：全部正式信号与我跟了的
     */
    private static void fillSummary(ObjectNode stat, List<ObjectNode> trades, Map<String, Integer> lots)
    {
        List<JsonNode> kept = new ArrayList<>();
        List<JsonNode> adopted = new ArrayList<>();
        List<JsonNode> missed = new ArrayList<>();
        int lotsTotal = 0;
        for (ObjectNode trade : trades)
        {
            if (trade.path("excluded").asBoolean())
            {
                continue;
            }
            kept.add(trade);
            JsonNode lot = trade.get("adoptedLots");
            if (lot != null && lot.isNumber())
            {
                adopted.add(scale(trade, lot.asInt()));
                lotsTotal += lot.asInt();
            }
            else
            {
                missed.add(trade);
            }
        }
        stat.set("summary", summary(kept));
        stat.set("adoptedSummary", summary(adopted));
        ObjectNode compare = stat.putObject("compare");
        compare.put("allCount", kept.size());
        compare.put("adoptedCount", adopted.size());
        putNumber(compare, "adoptionRate", kept.isEmpty() ? null : adopted.size() * 100.0 / kept.size());
        compare.put("lotsTotal", lotsTotal);
        compare.put("decisionCount", lots.size());
        List<JsonNode> priced = new ArrayList<>();
        for (JsonNode trade : missed)
        {
            if (number(trade.get("pnl")) != null)
            {
                priced.add(trade);
            }
        }
        putNumber(compare, "missedNet", priced.isEmpty() ? null : sum(priced));
        priced.sort(Comparator.comparingDouble((JsonNode t) -> t.path("pnl").asDouble()).reversed());
        ArrayNode best = compare.putArray("missedBest");
        for (int i = 0; i < Math.min(3, priced.size()); i++)
        {
            best.add(brief(priced.get(i)));
        }
        ArrayNode worst = compare.putArray("missedWorst");
        for (int i = priced.size() - 1; i >= Math.max(0, priced.size() - 3); i--)
        {
            if (priced.get(i).path("pnl").asDouble() < 0)
            {
                worst.add(brief(priced.get(i)));
            }
        }
        compare.set("sideAll", side(kept));
        compare.set("sideAdopted", side(adopted));
    }

    private static ObjectNode summary(List<JsonNode> trades)
    {
        List<Double> pnls = new ArrayList<>();
        List<Double> profits = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        double premium = 0;
        int premiumCount = 0;
        int expiry = 0;
        int early = 0;
        Set<String> units = new HashSet<>();
        for (JsonNode trade : trades)
        {
            Double pnl = number(trade.get("pnl"));
            Double prem = number(trade.get("premiumUsd"));
            if (prem != null)
            {
                premium += prem;
                premiumCount++;
            }
            String outcome = trade.path("outcome").asText();
            expiry += "EXPIRY".equals(outcome) ? 1 : 0;
            early += "EARLY_TAKE_PROFIT".equals(outcome) ? 1 : 0;
            if (pnl == null)
            {
                continue;
            }
            pnls.add(pnl);
            units.add(trade.path("symbol").asText() + "|" + trade.path("expiry").asText() + "|"
                    + trade.path("intent").asText());
            if (pnl > 0)
            {
                profits.add(pnl);
            }
            else if (pnl < 0)
            {
                losses.add(pnl);
            }
        }
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        node.put("evaluated", pnls.size());
        node.put("profitable", profits.size());
        node.put("losses", losses.size());
        putNumber(node, "winRate", pnls.isEmpty() ? null : profits.size() * 100.0 / pnls.size());
        putNumber(node, "net", pnls.isEmpty() ? null : pnls.stream().mapToDouble(Double::doubleValue).sum());
        putNumber(node, "premium", premiumCount == 0 ? null : premium);
        putNumber(node, "avgProfit", avg(profits));
        putNumber(node, "avgLoss", avg(losses));
        putNumber(node, "maxProfit", profits.isEmpty() ? null : profits.stream().mapToDouble(Double::doubleValue).max().getAsDouble());
        putNumber(node, "maxLoss", losses.isEmpty() ? null : losses.stream().mapToDouble(Double::doubleValue).min().getAsDouble());
        ObjectNode byOutcome = node.putObject("byOutcome");
        byOutcome.put("EXPIRY", expiry);
        byOutcome.put("EARLY_TAKE_PROFIT", early);
        node.put("missing", trades.size() - pnls.size());
        // 按结算日（只计有结果的日）
        Map<String, double[]> days = buckets(trades, "day", t -> true);
        List<Map.Entry<String, double[]>> priced = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : days.entrySet())
        {
            if (entry.getValue()[1] > 0)
            {
                priced.add(entry);
            }
        }
        node.put("days", priced.size());
        Map.Entry<String, double[]> bestDay = null;
        Map.Entry<String, double[]> worstDay = null;
        for (Map.Entry<String, double[]> entry : priced)
        {
            bestDay = bestDay == null || entry.getValue()[0] > bestDay.getValue()[0] ? entry : bestDay;
            worstDay = worstDay == null || entry.getValue()[0] < worstDay.getValue()[0] ? entry : worstDay;
        }
        node.set("bestDay", dayNode(bestDay));
        node.set("worstDay", dayNode(worstDay));
        node.put("independentUnits", units.size());
        List<Double> dayLosses = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : priced)
        {
            if (entry.getValue()[0] < 0)
            {
                dayLosses.add(entry.getValue()[0]);
            }
        }
        dayLosses.sort(Double::compare);
        if (dayLosses.size() < 5)
        {
            node.putNull("worstFiveDays");
        }
        else
        {
            double total = dayLosses.stream().mapToDouble(Double::doubleValue).sum();
            double five = dayLosses.subList(0, 5).stream().mapToDouble(Double::doubleValue).sum();
            ObjectNode worst = node.putObject("worstFiveDays");
            putNumber(worst, "lossShare", total == 0 ? 0 : five / total * 100);
            putNumber(worst, "loss", five);
            putNumber(worst, "totalLoss", total);
        }
        return node;
    }

    private static ObjectNode dayNode(Map.Entry<String, double[]> entry)
    {
        if (entry == null)
        {
            return null;
        }
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        node.put("key", entry.getKey());
        putNumber(node, "pnl", entry.getValue()[0]);
        node.put("count", (int) entry.getValue()[1]);
        node.put("missing", (int) entry.getValue()[2]);
        return node;
    }

    /**
     * 卖看跌 / 卖看涨 分边统计
     */
    private static ObjectNode side(List<JsonNode> trades)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        for (String key : new String[] { "PUT", "CALL" })
        {
            int n = 0;
            int wins = 0;
            Double net = null;
            Double worst = null;
            for (JsonNode trade : trades)
            {
                Double pnl = number(trade.get("pnl"));
                String intent = trade.path("intent").asText();
                if (pnl == null || !intent.contains(key) || ("CALL".equals(key) && intent.contains("PUT")))
                {
                    continue;
                }
                n++;
                wins += pnl > 0 ? 1 : 0;
                net = (net == null ? 0 : net) + pnl;
                worst = worst == null ? pnl : Math.min(worst, pnl);
            }
            ObjectNode row = node.putObject(key);
            row.put("n", n);
            row.put("wins", wins);
            putNumber(row, "net", net);
            putNumber(row, "worst", worst);
        }
        return node;
    }

    private static ObjectNode brief(JsonNode trade)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        for (String field : new String[] { "id", "symbol", "structure", "expiry", "day", "outcomeLabel", "pnl",
                "recommendationId" })
        {
            node.set(field, trade.get(field));
        }
        return node;
    }

    private static ObjectNode management(JsonNode n, String marketDay)
    {
        ObjectNode node = base("management", "管理战绩", marketDay);
        boolean ok = n.isObject() && !n.hasNonNull("error");
        node.put("available", ok);
        if (!ok)
        {
            return node;
        }
        node.put("asOf", str(n, "asOf"));
        for (String field : new String[] { "issued", "chosen", "settled" })
        {
            putNumber(node, field, zero(n.get(field)));
        }
        for (String field : new String[] { "followRecRate", "recBeatsNoDoRate", "choiceBeatsNoDoRate",
                "recEdgeVsNoDoUsd", "choiceEdgeVsNoDoUsd", "choicePnlUsd", "recPnlUsd", "bestPnlUsd", "noDoPnlUsd" })
        {
            putNumber(node, field, number(n.get(field)));
        }
        node.put("note", str(n, "note"));
        ArrayNode choices = node.putArray("byChoice");
        for (Iterator<Map.Entry<String, JsonNode>> it = n.path("byChoice").fields(); it.hasNext();)
        {
            Map.Entry<String, JsonNode> entry = it.next();
            ObjectNode row = choices.addObject();
            row.put("key", entry.getKey());
            putNumber(row, "n", zero(entry.getValue().get("n")));
            putNumber(row, "pnl", zero(entry.getValue().get("pnl")));
        }
        node.set("byScenario", n.path("byScenario").isObject() ? n.get("byScenario") : null);
        return node;
    }

    private static ObjectNode midterm(JsonNode n, String marketDay)
    {
        ObjectNode node = base("midterm", "中期信号", marketDay);
        List<ObjectNode> rows = new ArrayList<>();
        for (JsonNode item : iterable(n.get("rows")))
        {
            String id = str(item, "id");
            if (id.isEmpty())
            {
                continue;
            }
            ObjectNode row = OptionJson.MAPPER.createObjectNode();
            row.put("id", id);
            row.put("symbol", str(item, "symbol"));
            row.put("side", "call".equalsIgnoreCase(str(item, "side")) ? "C" : "P");
            row.put("issuedDay", str(item, "issuedDayEt"));
            row.put("expiry", str(item, "expiration"));
            putNumber(row, "shortStrike", number(item.get("shortStrike")));
            putNumber(row, "longStrike", number(item.get("longStrike")));
            putNumber(row, "creditUsd", number(item.get("creditUsd")));
            row.put("settled", item.path("settled").isBoolean() && item.path("settled").asBoolean());
            row.put("stage", str(item, "stage"));
            putNumber(row, "pnl", number(item.get("pnlUsd")));
            row.put("closedAtUtc", str(item, "closedAtUtc"));
            rows.add(row);
        }
        rows.sort((a, b) -> b.path("issuedDay").asText().compareTo(a.path("issuedDay").asText()));
        node.put("available", !rows.isEmpty());
        node.put("start", str(n, "start"));
        putNumber(node, "total", number(n.get("total")) == null ? (double) rows.size() : number(n.get("total")));
        putNumber(node, "priced", zero(n.get("pricedTotal")));
        putNumber(node, "winners", zero(n.get("winners")));
        putNumber(node, "winRate", number(n.get("winRate")));
        putNumber(node, "premiumUsd", number(n.get("premiumUsd")));
        putNumber(node, "managedPnlUsd", number(n.get("managedPnlUsd")));
        long settled = rows.stream().filter(r -> r.path("settled").asBoolean()).count();
        putNumber(node, "settledN", number(n.get("settled")) == null ? (double) settled : number(n.get("settled")));
        putNumber(node, "settledPnlUsd", number(n.get("settledPnlUsd")));
        putNumber(node, "livePnlUsd", number(n.get("livePnlUsd")));
        node.put("inTotals", n.path("inTotals").asBoolean(false));
        node.put("basis", str(n, "basis"));
        node.putArray("rows").addAll(rows);
        return node;
    }

    private static ObjectNode buyer(JsonNode lifecycle, String marketDay)
    {
        ObjectNode node = base("buyer", "买方卡族", marketDay);
        JsonNode t = lifecycle.path("scorecard");
        node.set("counts", lifecycle.path("counts").isObject() ? lifecycle.get("counts")
                : OptionJson.MAPPER.createObjectNode());
        node.put("available", t.isObject());
        if (!t.isObject())
        {
            node.put("note", "买方卡族还没有发过卡。");
            return node;
        }
        double settledN = zero(t.get("settled_n"));
        double issuedN = zero(t.get("issued_n"));
        putNumber(node, "settledN", settledN);
        putNumber(node, "issuedN", issuedN);
        Double pending = number(t.get("pending_n"));
        putNumber(node, "pendingN", pending == null ? Math.max(0, issuedN - settledN) : pending);
        node.put("basis", str(t, "basis"));
        node.put("generatedAt", str(t, "generated_at"));
        ArrayNode ladder = node.putArray("ladder");
        for (JsonNode item : iterable(t.get("ladder_calibration")))
        {
            ObjectNode row = ladder.addObject();
            putNumber(row, "claimedPct", zero(item.get("claimed_pct")));
            putNumber(row, "actualPct", zero(item.get("actual_pct")));
            putNumber(row, "n", zero(item.get("n")));
            putNumber(row, "gapPp", zero(item.get("gap_pp")));
        }
        for (String[] f : new String[][] { { "reachLongPct", "reach_long_pct" },
                { "expiryAboveLongPct", "expiry_above_long_pct" }, { "expiryFullPct", "expiry_full_pct" },
                { "expiryZeroPct", "expiry_zero_pct" } })
        {
            putNumber(node, f[0], number(t.get(f[1])));
        }
        JsonNode pnl = t.get("pnl");
        if (pnl != null && pnl.isObject())
        {
            ObjectNode p = node.putObject("pnl");
            for (String[] f : new String[][] { { "n", "n" }, { "total", "total" }, { "totalCost", "total_cost" },
                    { "returnPct", "return_pct" }, { "winN", "win_n" }, { "winPct", "win_pct" } })
            {
                putNumber(p, f[0], zero(pnl.get(f[1])));
            }
        }
        else
        {
            node.putNull("pnl");
        }
        node.put("note", settledN > 0 ? ""
                : issuedN > 0 ? "已发 " + (long) issuedN + " 张，还没有一张走到到期日——买方价差按到期结算，第一批结果要等窗口走完。"
                        : "买方卡族还没有发过卡。");
        return node;
    }

    private static ObjectNode accountSim(JsonNode n, String marketDay)
    {
        ObjectNode node = base("accountSim", "账户模拟", marketDay);
        boolean ok = n.isObject() && !n.hasNonNull("error");
        node.put("available", ok && n.path("accounts").isArray() && n.path("accounts").size() > 0);
        if (!ok)
        {
            return node;
        }
        node.put("asOf", str(n, "asOf"));
        node.put("sampleStart", str(n, "sampleStart"));
        node.put("replayUntil", str(n, "replayUntil"));
        putNumber(node, "n", zero(n.get("n")));
        node.put("basis", str(n, "basis"));
        node.put("sr", str(n, "sr"));
        putNumber(node, "defaultsVersion", number(n.get("defaultsVersion")));
        node.set("kpiNote", n.path("kpiNote").isObject() ? n.get("kpiNote") : null);
        node.set("headline", n.path("headline").isObject() ? n.get("headline") : null);
        return node;
    }

    private static ObjectNode base(String id, String label, String marketDay)
    {
        ObjectNode node = OptionJson.MAPPER.createObjectNode();
        node.put("id", id);
        node.put("scope", ALL);
        node.put("label", label);
        node.put("marketDay", marketDay);
        return node;
    }

    // ------------------------------------------------------------------ 账户模拟

    private static List<ObjectNode> accounts(JsonNode tracking)
    {
        JsonNode sim = tracking.path("accountSim");
        List<ObjectNode> list = new ArrayList<>();
        if (!sim.isObject() || sim.hasNonNull("error"))
        {
            return list;
        }
        for (JsonNode t : iterable(sim.get("accounts")))
        {
            Double size = number(t.get("size"));
            if (size == null)
            {
                continue;
            }
            ObjectNode row = OptionJson.MAPPER.createObjectNode();
            row.put("id", String.valueOf(size.longValue()));
            String label = str(t, "label");
            row.put("label", label.isEmpty() ? "$" + Math.round(size / 1000) + "k 账户 · 默认设置" : label);
            putNumber(row, "size", size);
            Double qty = number(t.get("qty"));
            row.put("qty", Math.max(1, qty == null ? 1 : (int) Math.floor(qty)));
            putNumber(row, "taken", zero(t.get("taken")));
            putNumber(row, "skipped", zero(t.get("skipped")));
            List<ObjectNode> skips = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = t.path("skipCounts").fields(); it.hasNext();)
            {
                Map.Entry<String, JsonNode> entry = it.next();
                double count = zero(entry.getValue());
                if (count > 0)
                {
                    ObjectNode skip = OptionJson.MAPPER.createObjectNode();
                    skip.put("why", entry.getKey());
                    putNumber(skip, "n", count);
                    skips.add(skip);
                }
            }
            skips.sort(Comparator.comparingDouble((ObjectNode s) -> s.path("n").asDouble()).reversed());
            row.putArray("skipTop").addAll(skips);
            putNumber(row, "pnlUsd", zero(t.get("pnlUsd")));
            putNumber(row, "pnlPct", number(t.get("pnlPct")));
            ArrayNode curve = row.putArray("curve");
            for (JsonNode point : iterable(t.get("curve")))
            {
                if (point.isArray() && point.size() >= 2 && point.get(0).isTextual() && number(point.get(1)) != null)
                {
                    curve.addArray().add(point.get(0).asText()).add(point.get(1));
                }
            }
            JsonNode dd = t.path("maxDrawdown");
            putNumber(row, "maxDrawdownPct", number(dd.get("pct")));
            putNumber(row, "maxDrawdownUsd", number(dd.get("usd")));
            row.put("maxDrawdownTroughDay", str(dd, "troughDay"));
            JsonNode ww = t.get("worstWeek");
            if (ww != null && ww.isObject() && number(ww.get("pnlUsd")) != null)
            {
                ObjectNode w = row.putObject("worstWeek");
                w.put("weekOf", str(ww, "weekOf"));
                putNumber(w, "pnlUsd", number(ww.get("pnlUsd")));
            }
            else
            {
                row.putNull("worstWeek");
            }
            putNumber(row, "fullPiercePct", number(t.path("fullPierce").get("pct")));
            putNumber(row, "peakUtilPct", number(t.get("peakUtilPct")));
            JsonNode fl = t.get("inFlight");
            if (fl != null && fl.isObject())
            {
                ObjectNode f = row.putObject("inFlight");
                putNumber(f, "n", zero(fl.get("n")));
                putNumber(f, "riskUsd", zero(fl.get("riskUsd")));
            }
            else
            {
                row.putNull("inFlight");
            }
            row.set("clusterNow", t.path("clusterNow").isObject() ? t.get("clusterNow") : null);
            list.add(row);
        }
        list.sort(Comparator.comparingDouble((ObjectNode r) -> r.path("size").asDouble()));
        return list;
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 区间：month 本月（交易日所在月）/ all 全部（null）
     */
    public static boolean inRange(String day, String range, String marketDay)
    {
        if (!MONTH.equals(range))
        {
            return true;
        }
        return marketDay != null && marketDay.length() >= 7 && day != null && day.startsWith(marketDay.substring(0, 7));
    }

    private static double sum(List<JsonNode> trades)
    {
        return trades.stream().mapToDouble(t -> t.path("pnl").asDouble()).sum();
    }

    private static Double avg(List<Double> values)
    {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
    }

    private static Iterable<JsonNode> iterable(JsonNode array)
    {
        List<JsonNode> list = new ArrayList<>();
        if (array != null && array.isArray())
        {
            array.forEach(node -> {
                if (node != null && node.isObject() || node != null && node.isArray())
                {
                    list.add(node);
                }
            });
        }
        return list;
    }

    static Double number(JsonNode value)
    {
        return OptionSettlementAssembler.number(value);
    }

    private static double zero(JsonNode value)
    {
        Double number = number(value);
        return number == null ? 0 : number;
    }

    private static String str(JsonNode node, String field)
    {
        String text = OptionConductAssembler.text(node, field);
        return text == null ? "" : text;
    }

    private static String text(JsonNode node, String field)
    {
        return str(node, field);
    }

    /**
     * 写入数值：保留 2 位小数，整数按整数输出
     */
    public static void putNumber(ObjectNode node, String field, Double value)
    {
        if (value == null || !Double.isFinite(value))
        {
            node.putNull(field);
            return;
        }
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15)
        {
            node.put(field, (long) rounded);
        }
        else
        {
            node.put(field, rounded);
        }
    }
}
