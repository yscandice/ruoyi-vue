package com.ruoyi.option.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.enums.OptionConductScope;
import com.ruoyi.option.utils.OptionMarketCalendar;

/**
 * 「进行」菜单分栏：按 OSM 会员页（/member?view=active）前端规则，把 feed 拆成 5 个栏目
 * 
 * 进行中：history 中未到期、未结算的正式信号（同推荐ID时合并今日 signals 的最新状态）+ 今日未锁定的 signals，按到期升序、发出时间倒序；
 * 已到期：history 中 resultKind=EXPIRY、statusCode=SETTLED 或到期日早于交易日的信号，按发出时间倒序；
 * 历史信号：history 中全部信号（每个生命周期一张卡），按发出时间倒序；
 * 保护窗口：交易日当天出现或更新的 protections，排除 eod 事件与当日已记录保护的信号，按更新时间倒序；
 * 动态管理：managementCards 中 retired != true 的管理卡，按发出时间倒序。
 * 
 * 未复刻页面中由浏览器本地状态生成的买方卡、中期影子卡合并展示。
 * 
 * @author yscandice
 */
@Component
public class OptionConductAssembler
{
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final Pattern MONTH_DAY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})$");

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM/dd");

    /** 无到期日时视为最远到期（与页面 2^53-1 一致） */
    private static final long NO_EXPIRY = Long.MAX_VALUE;

    /**
     * 分栏基准交易日（marketEtDay，缺省 etDay）
     * 
     * @param feed feed 根节点
     * @return yyyy-MM-dd，无法识别返回空串
     */
    public static String marketDay(JsonNode feed)
    {
        String day = text(feed, "marketEtDay");
        if (day == null)
        {
            day = text(feed, "etDay");
        }
        return day != null && DATE.matcher(day).matches() ? day : "";
    }

    /**
     * 拆分栏目
     * 
     * @param feed feed 根节点
     * @return 各栏目条目（已排序）
     */
    public Map<OptionConductScope, List<ObjectNode>> assemble(JsonNode feed)
    {
        String day = marketDay(feed);
        long dayStart = day.isEmpty() ? 0 : LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        List<ObjectNode> signals = objects(feed.path("signals"));
        List<ObjectNode> history = objects(feed.path("history"));
        List<ObjectNode> threads = threads(signals, history);
        List<ObjectNode> archived = new ArrayList<>();
        for (ObjectNode item : threads)
        {
            if ("history".equals(text(item, "date")))
            {
                archived.add(item);
            }
        }

        Map<OptionConductScope, List<ObjectNode>> scopes = new EnumMap<>(OptionConductScope.class);
        List<ObjectNode> expired = expired(archived, dayStart);
        scopes.put(OptionConductScope.ACTIVE, active(archived, expired, signals, dayStart));
        scopes.put(OptionConductScope.PROTECTION, protection(feed, threads, day));
        scopes.put(OptionConductScope.MANAGE, manage(feed));
        scopes.put(OptionConductScope.EXPIRED, sortByEmittedDesc(expired));
        scopes.put(OptionConductScope.HISTORY, sortByEmittedDesc(new ArrayList<>(archived)));
        return scopes;
    }

    /**
     * 今日 signals 与 history 合并后按生命周期去重（页面 K）
     */
    private List<ObjectNode> threads(List<ObjectNode> signals, List<ObjectNode> history)
    {
        Map<String, ObjectNode> historyById = new HashMap<>();
        for (ObjectNode item : history)
        {
            historyById.put(firstText(item, "recommendationId", "id"), item);
        }
        List<ObjectNode> today = new ArrayList<>();
        for (ObjectNode signal : signals)
        {
            ObjectNode archived = historyById.get(firstText(signal, "recommendationId", "id"));
            if (archived == null)
            {
                today.add(signal.deepCopy());
                continue;
            }
            ObjectNode merged = archived.deepCopy();
            merged.setAll(signal.deepCopy());
            merged.set("id", signal.get("id"));
            merged.put("date", "today");
            merged.remove("result");
            merged.remove("resultNote");
            today.add(merged);
        }
        today.sort(Comparator.comparingLong(OptionConductAssembler::activityMs).reversed());

        Map<String, List<ObjectNode>> groups = new LinkedHashMap<>();
        for (ObjectNode item : today)
        {
            groups.computeIfAbsent(threadKey(item), k -> new ArrayList<>()).add(item);
        }
        for (ObjectNode item : history)
        {
            groups.computeIfAbsent(threadKey(item), k -> new ArrayList<>()).add(item.deepCopy());
        }
        List<ObjectNode> threads = new ArrayList<>();
        for (Map.Entry<String, List<ObjectNode>> entry : groups.entrySet())
        {
            List<ObjectNode> versions = new ArrayList<>(entry.getValue());
            versions.sort(Comparator.comparingLong(OptionConductAssembler::activityMs));
            ObjectNode picked = versions.get(versions.size() - 1);
            for (int i = versions.size() - 1; i >= 0; i--)
            {
                if ("today".equals(text(versions.get(i), "date")))
                {
                    picked = versions.get(i);
                    break;
                }
            }
            ObjectNode thread = picked.deepCopy();
            thread.put("threadKey", entry.getKey());
            thread.put("threadVersion", versions.size());
            threads.add(thread);
        }
        return threads;
    }

    /**
     * 已到期：自然到期 / 已结算 / 到期日早于交易日
     */
    private List<ObjectNode> expired(List<ObjectNode> archived, long dayStart)
    {
        List<ObjectNode> expired = new ArrayList<>();
        for (ObjectNode item : archived)
        {
            if ("EXPIRY".equals(text(item, "resultKind")) || "SETTLED".equals(text(item, "statusCode"))
                    || expiryMs(item) < dayStart)
            {
                expired.add(item);
            }
        }
        return expired;
    }

    /**
     * 进行中：未到期正式信号（每个只显示一次）+ 今日新信号
     */
    private List<ObjectNode> active(List<ObjectNode> archived, List<ObjectNode> expired, List<ObjectNode> signals,
            long dayStart)
    {
        Set<String> expiredKeys = new HashSet<>();
        for (ObjectNode item : expired)
        {
            expiredKeys.add(itemKey(item));
        }
        Map<String, ObjectNode> signalByRecommendation = new HashMap<>();
        for (ObjectNode signal : signals)
        {
            String rid = text(signal, "recommendationId");
            if (rid != null)
            {
                signalByRecommendation.put(rid, signal);
            }
        }
        Set<String> used = new HashSet<>();
        List<ObjectNode> active = new ArrayList<>();
        for (ObjectNode item : archived)
        {
            if (expiredKeys.contains(itemKey(item)) || expiryMs(item) < dayStart)
            {
                continue;
            }
            String rid = firstText(item, "recommendationId", "id");
            used.add(rid);
            ObjectNode signal = rid == null ? null : signalByRecommendation.get(rid);
            ObjectNode card = item.deepCopy();
            if (signal != null)
            {
                card.setAll(signal.deepCopy());
                card.set("id", item.get("id"));
                card.put("recommendationId", rid);
            }
            else
            {
                card.put("state", "持有复核");
                card.put("lifecycleStage", "持有复核");
                card.put("statusCode", "AWAITING_REVIEW");
                card.put("verdict", "未到期 · 等待最新管理复核");
                card.put("nextReview", "随终端生命周期更新");
                card.put("nextMinutes", 99);
                card.put("managementNote", "这是尚未到期的正式信号；不把历史发布自动延长为新的开仓许可。");
            }
            card.put("date", "today");
            card.remove("result");
            card.remove("resultNote");
            active.add(card);
        }
        for (ObjectNode signal : signals)
        {
            if (signal.path("locked").asBoolean(false) || used.contains(firstText(signal, "recommendationId", "id"))
                    || expiryMs(signal) < dayStart)
            {
                continue;
            }
            active.add(signal.deepCopy());
        }
        active.sort(Comparator.comparingLong(OptionConductAssembler::expiryMs)
                .thenComparing(Comparator.comparingLong(OptionConductAssembler::emittedMs).reversed()));
        return active;
    }

    /**
     * 保护窗口：交易日当天出现或更新、非 eod、且对应信号当日未记录保护
     */
    private List<ObjectNode> protection(JsonNode feed, List<ObjectNode> threads, String day)
    {
        Set<String> recorded = new HashSet<>();
        for (ObjectNode item : threads)
        {
            if (item.path("protectionRecorded").asBoolean(false) && day.equals(etDay(emittedMs(item))))
            {
                recorded.add(protectionKey(text(item, "symbol"), text(item, "structure"), text(item, "expiryDate")));
            }
        }
        List<ObjectNode> list = new ArrayList<>();
        for (ObjectNode item : objects(feed.path("protections")))
        {
            if (!onDay(item, day) || "eod".equals(text(item, "eventKind")))
            {
                continue;
            }
            if (!recorded.isEmpty() && recorded.contains(
                    protectionKey(text(item, "symbol"), text(item, "sourceStructure"), text(item, "sourceExpiry"))))
            {
                continue;
            }
            list.add(item.deepCopy());
        }
        list.sort(Comparator.comparingLong(
                (ObjectNode e) -> parseMs(firstText(e, "lastUpdatedAtUtc", "openedAtUtc"), Long.MIN_VALUE)).reversed());
        return list;
    }

    /**
     * 动态管理：未退役的管理卡
     */
    private List<ObjectNode> manage(JsonNode feed)
    {
        List<ObjectNode> list = new ArrayList<>();
        for (ObjectNode item : objects(feed.path("managementCards")))
        {
            if (!item.path("retired").asBoolean(false))
            {
                list.add(item.deepCopy());
            }
        }
        list.sort(Comparator.comparingLong((ObjectNode e) -> parseMs(text(e, "issuedAtUtc"), Long.MIN_VALUE))
                .reversed());
        return list;
    }

    private static List<ObjectNode> sortByEmittedDesc(List<ObjectNode> list)
    {
        list.sort(Comparator.comparingLong(OptionConductAssembler::emittedMs).reversed());
        return list;
    }

    /**
     * 保护事件是否属于该交易日（任一时间点的美东日期等于交易日；无可解析时间时按 MM/dd 前缀判断）
     */
    private static boolean onDay(ObjectNode item, String day)
    {
        if (day.isEmpty())
        {
            return true;
        }
        List<String> times = new ArrayList<>();
        times.add(text(item, "lastUpdatedAtUtc"));
        for (JsonNode update : item.path("updates"))
        {
            times.add(text(update, "occurredAt"));
        }
        times.add(text(item, "openedAtUtc"));
        Set<String> days = new LinkedHashSet<>();
        for (String time : times)
        {
            long ms = parseMs(time, Long.MIN_VALUE);
            if (ms != Long.MIN_VALUE)
            {
                days.add(etDay(ms));
            }
        }
        if (!days.isEmpty())
        {
            return days.contains(day);
        }
        String md = LocalDate.parse(day).format(MD);
        return StringUtils.startsWith(text(item, "lastUpdatedAt"), md) || StringUtils.startsWith(text(item, "openedAt"), md);
    }

    private static String protectionKey(String symbol, String structure, String expiry)
    {
        String exp = expiry == null ? "" : expiry.substring(0, Math.min(10, expiry.length()));
        return StringUtils.nvl(symbol, "").toUpperCase(Locale.ROOT) + "|"
                + StringUtils.nvl(structure, "").replaceAll("\\s+", "") + "|" + exp;
    }

    /**
     * 条目键（页面 Xt：推荐ID与ID不同时为 id:recommendationId）
     * 
     * @param item 条目
     * @return 键，可能为 null
     */
    public static String itemKey(JsonNode item)
    {
        String id = text(item, "id");
        String rid = text(item, "recommendationId");
        return rid != null && !rid.equals(id) ? id + ":" + rid : id;
    }

    /**
     * 生命周期键（页面 Pt）
     * 
     * @param item 条目
     * @return 键
     */
    public static String threadKey(JsonNode item)
    {
        String rid = firstText(item, "recommendationId", "sourceEventId");
        if (rid != null)
        {
            return ("RECOMMENDATION|" + rid).toUpperCase(Locale.ROOT);
        }
        return (StringUtils.nvl(text(item, "symbol"), "") + "|" + StringUtils.nvl(text(item, "intent"), "") + "|"
                + StringUtils.nvl(text(item, "expiry"), "") + "|" + StringUtils.nvl(text(item, "structure"), ""))
                .toUpperCase(Locale.ROOT);
    }

    /**
     * 发出时间毫秒（页面 V：emittedAtUtc / activityAtUtc / sellerSignalAt / emittedAt 中第一个可解析的）
     * 
     * @param item 条目
     * @return 毫秒，无法解析返回 0
     */
    public static long emittedMs(JsonNode item)
    {
        for (String field : List.of("emittedAtUtc", "activityAtUtc", "sellerSignalAt", "emittedAt"))
        {
            long ms = parseMs(text(item, field), Long.MIN_VALUE);
            if (ms != Long.MIN_VALUE)
            {
                return ms;
            }
        }
        return 0;
    }

    private static long activityMs(JsonNode item)
    {
        return parseMs(firstText(item, "activityAtUtc", "emittedAtUtc"), 0);
    }

    /**
     * 到期日 0 点（UTC）毫秒：优先 expiryDate，其次 M/D（按发出时间推断年份），都没有视为最远
     */
    private static long expiryMs(JsonNode item)
    {
        String date = text(item, "expiryDate");
        if (date != null && DATE.matcher(date).matches())
        {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        }
        Matcher m = MONTH_DAY.matcher(StringUtils.nvl(text(item, "expiry"), ""));
        if (!m.matches())
        {
            return NO_EXPIRY;
        }
        long emitted = emittedMs(item);
        ZonedDateTime base = Instant.ofEpochMilli(emitted).atZone(ZoneOffset.UTC);
        int month = Integer.parseInt(m.group(1));
        int year = month < base.getMonthValue() - 6 ? base.getYear() + 1 : base.getYear();
        try
        {
            return LocalDate.of(year, month, Integer.parseInt(m.group(2))).atStartOfDay(ZoneOffset.UTC).toInstant()
                    .toEpochMilli();
        }
        catch (RuntimeException e)
        {
            return NO_EXPIRY;
        }
    }

    /**
     * 毫秒转美东日期
     * 
     * @param ms 毫秒
     * @return yyyy-MM-dd
     */
    public static String etDay(long ms)
    {
        return Instant.ofEpochMilli(ms).atZone(OptionMarketCalendar.MARKET_ZONE).toLocalDate().toString();
    }

    /**
     * 解析 ISO 时间
     * 
     * @param text 时间文本
     * @param fallback 无法解析时的返回值
     * @return 毫秒
     */
    public static long parseMs(String text, long fallback)
    {
        if (StringUtils.isEmpty(text))
        {
            return fallback;
        }
        try
        {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        }
        catch (DateTimeParseException e)
        {
            try
            {
                return Instant.parse(text).toEpochMilli();
            }
            catch (DateTimeParseException ex)
            {
                return fallback;
            }
        }
    }

    /**
     * 读取非空文本字段
     * 
     * @param node 节点
     * @param field 字段
     * @return 文本，不存在或为空返回 null
     */
    public static String text(JsonNode node, String field)
    {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isContainerNode())
        {
            return null;
        }
        String text = value.asText();
        return text.isEmpty() ? null : text;
    }

    /**
     * 依次读取字段，返回第一个非空值（页面 a ?? b）
     */
    static String firstText(JsonNode node, String... fields)
    {
        for (String field : fields)
        {
            String value = text(node, field);
            if (value != null)
            {
                return value;
            }
        }
        return null;
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
