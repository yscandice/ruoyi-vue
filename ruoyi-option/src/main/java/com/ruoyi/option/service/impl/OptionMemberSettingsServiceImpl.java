package com.ruoyi.option.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionMemberConstants;
import com.ruoyi.option.domain.OptionMemberNotification;
import com.ruoyi.option.domain.OptionMemberProfile;
import com.ruoyi.option.domain.OptionMemberUpdateSeen;
import com.ruoyi.option.mapper.OptionMemberNotificationMapper;
import com.ruoyi.option.mapper.OptionMemberProfileMapper;
import com.ruoyi.option.mapper.OptionMemberUpdateSeenMapper;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionMemberSettingsService;
import com.ruoyi.option.service.IOptionResourceService;
import com.ruoyi.option.utils.OptionJson;
import com.ruoyi.option.utils.OptionMarketCalendar;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 会员「设置」Service业务层处理
 * 
 * 资料扩展、通知偏好、阅读进度都是本人数据：没有记录时按默认值返回，第一次保存才建记录；
 * 本人数据（不含 sys_user 的昵称/邮箱/头像）缓存在 option:member:settings:{userId}，30 分钟，保存时删除。
 * 
 * @author yscandice
 */
@Service
public class OptionMemberSettingsServiceImpl implements IOptionMemberSettingsService
{
    private static final Logger log = LoggerFactory.getLogger(OptionMemberSettingsServiceImpl.class);

    /** currentSeq 来自同步的 /api/member/updates（实时数据，更新菜单不冻结） */
    private static final String UPDATES_RESOURCE = "updates";

    private static final Pattern WATCH_SYMBOL = Pattern.compile(OptionMemberConstants.WATCH_SYMBOL_PATTERN);

    private static final String CONFLICT_MSG = "设置已在其他设备修改，请刷新";

    private static final Set<String> PROFILE_KEYS = Set.of("displayName", "watchSymbols", "strategyPreferences",
            "strongAlerts", "updatedAt", "email", "avatar");

    private static final Set<String> NOTIFICATION_BODY_KEYS = Set.of("prefs", "updatedAt");

    private static final Set<String> SEEN_BODY_KEYS = Set.of("seenSeq");

    @Autowired
    private OptionMemberProfileMapper profileMapper;

    @Autowired
    private OptionMemberNotificationMapper notificationMapper;

    @Autowired
    private OptionMemberUpdateSeenMapper updateSeenMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private IOptionAccessService accessService;

    @Autowired
    private IOptionResourceService resourceService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private RedisCache redisCache;

    @Override
    public Map<String, Object> selectSettings(Long userId)
    {
        JSONObject stored = stored(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", profileView(userId, stored.getJSONObject("profile")));
        data.put("notifications", notificationView(stored.getJSONObject("notifications")));
        data.put("access", accessService.selectAccessInfo(userId));
        data.put("updates", progressView(stored.getJSONObject("seen")));
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("referrals", false);
        features.put("push", false);
        features.put("apiKeys", false);
        features.put("advisoryContact", false);
        data.put("features", features);
        return data;
    }

    @Override
    public Map<String, Object> selectProfile(Long userId)
    {
        return profileView(userId, stored(userId).getJSONObject("profile"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateProfile(Long userId, Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            throw badRequest("没有要修改的内容");
        }
        for (String key : body.keySet())
        {
            if (!PROFILE_KEYS.contains(key))
            {
                throw badRequest("不支持的字段：" + key);
            }
        }
        SysUser user = userMapper.selectUserById(userId);
        if (user == null)
        {
            throw new ServiceException("用户不存在", HttpStatus.UNAUTHORIZED);
        }
        if (body.containsKey("email") && !StringUtils.equalsIgnoreCase(StringUtils.trim(str(body.get("email"))), user.getEmail()))
        {
            throw badRequest("邮箱是登录账号，不能在这里修改");
        }
        if (body.containsKey("avatar") && !Objects.equals(StringUtils.trimToNull(str(body.get("avatar"))), StringUtils.trimToNull(user.getAvatar())))
        {
            throw badRequest("头像请通过上传头像接口修改");
        }

        // 1、校验要改的字段
        String displayName = body.containsKey("displayName") ? validDisplayName(body.get("displayName")) : null;
        List<String> watchSymbols = body.containsKey("watchSymbols") ? validWatchSymbols(body.get("watchSymbols")) : null;
        Map<String, Boolean> strategyChanges = body.containsKey("strategyPreferences")
                ? validFlags(body.get("strategyPreferences"), OptionMemberConstants.STRATEGY_KEYS, "strategyPreferences", "策略偏好")
                : null;
        Boolean strongAlerts = body.containsKey("strongAlerts") ? validBoolean(body.get("strongAlerts"), "strongAlerts") : null;
        if (displayName == null && watchSymbols == null && strategyChanges == null && strongAlerts == null)
        {
            throw badRequest("没有要修改的内容");
        }

        // 2、乐观锁：与库里的 updatedAt 比较
        OptionMemberProfile row = profileMapper.selectOptionMemberProfileById(userId);
        checkUpdatedAt(body.get("updatedAt"), row == null ? null : updatedAt(row.getUpdateTime(), row.getCreateTime()));

        // 3、昵称写 sys_user.nick_name，其他字段写 option_member_profile（每次保存都刷新 updatedAt）
        if (displayName != null && !displayName.equals(user.getNickName()))
        {
            SysUser change = new SysUser();
            change.setUserId(userId);
            change.setNickName(displayName);
            change.setUpdateBy(user.getUserName());
            userMapper.updateUser(change);
        }
        Map<String, Boolean> strategy = row == null ? defaultFlags(OptionMemberConstants.STRATEGY_KEYS, OptionMemberConstants.STRATEGY_DEFAULT_ON)
                : flags(row.getStrategyPreferences(), OptionMemberConstants.STRATEGY_KEYS, OptionMemberConstants.STRATEGY_DEFAULT_ON);
        if (strategyChanges != null)
        {
            strategy.putAll(strategyChanges);
        }
        OptionMemberProfile profile = new OptionMemberProfile();
        profile.setUserId(userId);
        profile.setStrategyPreferences(JSON.toJSONString(strategy));
        if (row == null)
        {
            profile.setWatchSymbols(JSON.toJSONString(watchSymbols == null ? List.of() : watchSymbols));
            profile.setStrongAlerts(flag(Boolean.TRUE.equals(strongAlerts)));
            try
            {
                profileMapper.insertOptionMemberProfile(profile);
            }
            catch (DuplicateKeyException e)
            {
                throw conflict();
            }
        }
        else
        {
            profile.setWatchSymbols(watchSymbols == null ? null : JSON.toJSONString(watchSymbols));
            profile.setStrongAlerts(strongAlerts == null ? null : flag(strongAlerts));
            profile.setVersion(row.getVersion());
            if (profileMapper.updateOptionMemberProfile(profile) == 0)
            {
                throw conflict();
            }
        }
        clearCache(userId);
        return selectProfile(userId);
    }

    @Override
    public Map<String, Object> selectNotifications(Long userId)
    {
        return notificationView(stored(userId).getJSONObject("notifications"));
    }

    @Override
    public Map<String, Object> updateNotifications(Long userId, Map<String, Object> body)
    {
        if (body == null || !body.containsKey("prefs"))
        {
            throw badRequest("prefs 必填");
        }
        for (String key : body.keySet())
        {
            if (!NOTIFICATION_BODY_KEYS.contains(key))
            {
                throw badRequest("不支持的字段：" + key);
            }
        }
        Map<String, Boolean> changes = validFlags(body.get("prefs"), OptionMemberConstants.NOTIFY_KEYS, "prefs", "通知开关");
        if (changes.isEmpty())
        {
            throw badRequest("没有要修改的开关");
        }
        OptionMemberNotification row = notificationMapper.selectOptionMemberNotificationById(userId);
        checkUpdatedAt(body.get("updatedAt"), row == null ? null : updatedAt(row.getUpdateTime(), row.getCreateTime()));

        if (row == null)
        {
            Map<String, Boolean> prefs = defaultNotifyPrefs();
            prefs.putAll(changes);
            OptionMemberNotification insert = toNotification(userId, prefs);
            try
            {
                notificationMapper.insertOptionMemberNotification(insert);
            }
            catch (DuplicateKeyException e)
            {
                throw conflict();
            }
        }
        else
        {
            OptionMemberNotification update = toNotification(userId, changes);
            update.setVersion(row.getVersion());
            if (notificationMapper.updateOptionMemberNotification(update) == 0)
            {
                throw conflict();
            }
        }
        clearCache(userId);
        return selectNotifications(userId);
    }

    @Override
    public Map<String, Object> resetNotifications(Long userId)
    {
        // 删除记录即恢复默认（默认值以 option.settings.notifyDefaults 为准，下次保存再建记录）
        notificationMapper.deleteOptionMemberNotificationById(userId);
        clearCache(userId);
        return selectNotifications(userId);
    }

    @Override
    public Map<String, Object> selectUpdateProgress(Long userId)
    {
        return progressView(stored(userId).getJSONObject("seen"));
    }

    @Override
    public Map<String, Object> markUpdatesSeen(Long userId, Map<String, Object> body)
    {
        if (body == null || !body.containsKey("seenSeq"))
        {
            throw badRequest("seenSeq 必填");
        }
        for (String key : body.keySet())
        {
            if (!SEEN_BODY_KEYS.contains(key))
            {
                throw badRequest("不支持的字段：" + key);
            }
        }
        Object value = body.get("seenSeq");
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0
                || ((Number) value).longValue() > Integer.MAX_VALUE)
        {
            throw badRequest("seenSeq 必须是不小于 0 的整数");
        }
        int seenSeq = ((Number) value).intValue();
        Integer currentSeq = currentSeq();
        if (currentSeq == null)
        {
            throw new ServiceException("更新进度暂不可用，请稍后再试");
        }
        if (seenSeq > currentSeq)
        {
            throw badRequest("seenSeq 不能超过当前序号 " + currentSeq);
        }
        updateSeenMapper.upsertSeen(userId, seenSeq);
        clearCache(userId);
        return selectUpdateProgress(userId);
    }

    @Override
    public void clearCache(Long userId)
    {
        redisCache.deleteObject(OptionMemberConstants.SETTINGS_KEY + userId);
    }

    // ------------------------------------------------------------------ 读取与缓存

    /**
     * 本人数据（资料扩展、通知偏好、阅读进度），没有记录时为默认值
     */
    private JSONObject stored(Long userId)
    {
        String key = OptionMemberConstants.SETTINGS_KEY + userId;
        Object cached = redisCache.getCacheObject(key);
        if (cached instanceof String text)
        {
            try
            {
                return JSON.parseObject(text);
            }
            catch (RuntimeException e)
            {
                log.warn("设置缓存格式错误，重新读取 userId={}", userId);
            }
        }
        JSONObject stored = new JSONObject();

        OptionMemberProfile profile = profileMapper.selectOptionMemberProfileById(userId);
        JSONObject p = new JSONObject();
        p.put("watchSymbols", profile == null ? List.of() : symbols(profile.getWatchSymbols()));
        p.put("strategyPreferences", profile == null
                ? defaultFlags(OptionMemberConstants.STRATEGY_KEYS, OptionMemberConstants.STRATEGY_DEFAULT_ON)
                : flags(profile.getStrategyPreferences(), OptionMemberConstants.STRATEGY_KEYS, OptionMemberConstants.STRATEGY_DEFAULT_ON));
        p.put("strongAlerts", profile != null && "1".equals(profile.getStrongAlerts()));
        p.put("updatedAt", profile == null ? null : updatedAt(profile.getUpdateTime(), profile.getCreateTime()));
        stored.put("profile", p);

        OptionMemberNotification notification = notificationMapper.selectOptionMemberNotificationById(userId);
        JSONObject n = new JSONObject();
        n.put("prefs", notification == null ? defaultNotifyPrefs() : notifyPrefs(notification));
        n.put("updatedAt", notification == null ? null : updatedAt(notification.getUpdateTime(), notification.getCreateTime()));
        stored.put("notifications", n);

        OptionMemberUpdateSeen seen = updateSeenMapper.selectOptionMemberUpdateSeenById(userId);
        JSONObject s = new JSONObject();
        s.put("seenSeq", seen == null || seen.getSeenSeq() == null ? 0 : seen.getSeenSeq());
        s.put("seenAt", seen == null ? null : iso(seen.getSeenAt()));
        stored.put("seen", s);

        redisCache.setCacheObject(key, stored.toJSONString(), OptionMemberConstants.SETTINGS_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return stored;
    }

    private Map<String, Object> profileView(Long userId, JSONObject p)
    {
        SysUser user = userMapper.selectUserById(userId);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("displayName", user == null ? null : user.getNickName());
        profile.put("email", user == null ? null : user.getEmail());
        profile.put("avatar", user == null ? null : StringUtils.trimToNull(user.getAvatar()));
        profile.put("watchSymbols", p.getJSONArray("watchSymbols"));
        profile.put("strategyPreferences", p.getJSONObject("strategyPreferences"));
        profile.put("strongAlerts", p.getBooleanValue("strongAlerts"));
        profile.put("updatedAt", p.getString("updatedAt"));
        return profile;
    }

    private Map<String, Object> notificationView(JSONObject n)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prefs", n.getJSONObject("prefs"));
        data.put("updatedAt", n.getString("updatedAt"));
        return data;
    }

    private Map<String, Object> progressView(JSONObject s)
    {
        int seenSeq = s.getIntValue("seenSeq");
        Integer currentSeq = currentSeq();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seenSeq", seenSeq);
        data.put("currentSeq", currentSeq);
        data.put("seenAt", s.getString("seenAt"));
        data.put("unseen", currentSeq == null ? null : Math.max(0, currentSeq - seenSeq));
        return data;
    }

    /**
     * 当前最新序号：读同步的 /api/member/updates（实时，Redis 未命中时由资源服务从表回填）
     */
    private Integer currentSeq()
    {
        try
        {
            FeedBody body = resourceService.get(UPDATES_RESOURCE, null);
            if (body == null || body.body() == null)
            {
                return null;
            }
            JsonNode node = OptionJson.read(body.body()).path("currentSeq");
            return node.canConvertToInt() ? node.asInt() : null;
        }
        catch (RuntimeException e)
        {
            log.debug("读取更新 currentSeq 失败：{}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ 默认值与转换

    /**
     * 通知默认值：sys_config option.settings.notifyDefaults（JSON），缺失或格式错误时用内置默认值
     */
    private Map<String, Boolean> defaultNotifyPrefs()
    {
        Map<String, Boolean> prefs = defaultFlags(OptionMemberConstants.NOTIFY_KEYS, null);
        String text = configService.selectConfigByKey(OptionMemberConstants.CONFIG_NOTIFY_DEFAULTS);
        if (StringUtils.isNotBlank(text))
        {
            try
            {
                JSONObject config = JSON.parseObject(text);
                for (String key : OptionMemberConstants.NOTIFY_KEYS)
                {
                    if (config.get(key) instanceof Boolean value)
                    {
                        prefs.put(key, value);
                    }
                }
            }
            catch (RuntimeException e)
            {
                log.warn("参数 {} 不是合法 JSON，使用内置默认值", OptionMemberConstants.CONFIG_NOTIFY_DEFAULTS);
            }
        }
        return prefs;
    }

    /**
     * keys 全部为 true，defaultOn 不为空时只有其中的键为 true；NOTIFY_KEYS 的内置默认值按 NOTIFY_DEFAULT_OFF 取反
     */
    private static Map<String, Boolean> defaultFlags(String[] keys, String[] defaultOn)
    {
        List<String> on = defaultOn == null ? null : Arrays.asList(defaultOn);
        List<String> off = Arrays.asList(OptionMemberConstants.NOTIFY_DEFAULT_OFF);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String key : keys)
        {
            flags.put(key, on != null ? on.contains(key) : !off.contains(key));
        }
        return flags;
    }

    private static Map<String, Boolean> flags(String json, String[] keys, String[] defaultOn)
    {
        Map<String, Boolean> flags = defaultFlags(keys, defaultOn);
        if (StringUtils.isNotBlank(json))
        {
            try
            {
                JSONObject stored = JSON.parseObject(json);
                for (String key : keys)
                {
                    if (stored.get(key) instanceof Boolean value)
                    {
                        flags.put(key, value);
                    }
                }
            }
            catch (RuntimeException e)
            {
                log.warn("策略偏好 JSON 格式错误：{}", json);
            }
        }
        return flags;
    }

    private static List<String> symbols(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return List.of();
        }
        try
        {
            return JSONArray.parseArray(json).toJavaList(String.class);
        }
        catch (RuntimeException e)
        {
            log.warn("关注标的 JSON 格式错误：{}", json);
            return List.of();
        }
    }

    private static Map<String, Boolean> notifyPrefs(OptionMemberNotification n)
    {
        Map<String, Boolean> prefs = new LinkedHashMap<>();
        prefs.put("new_signal", "1".equals(n.getNewSignal()));
        prefs.put("protection_window", "1".equals(n.getProtectionWindow()));
        prefs.put("take_profit_window", "1".equals(n.getTakeProfitWindow()));
        prefs.put("settlement", "1".equals(n.getSettlement()));
        prefs.put("weekly_scorecard", "1".equals(n.getWeeklyScorecard()));
        prefs.put("product_update", "1".equals(n.getProductUpdate()));
        prefs.put("silent_reminder", "1".equals(n.getSilentReminder()));
        prefs.put("management_stop", "1".equals(n.getManagementStop()));
        prefs.put("manage_adopted_only", "1".equals(n.getManageAdoptedOnly()));
        return prefs;
    }

    /**
     * 只设置 prefs 中出现的开关，其余为 null（更新时不改动）
     */
    private static OptionMemberNotification toNotification(Long userId, Map<String, Boolean> prefs)
    {
        OptionMemberNotification n = new OptionMemberNotification();
        n.setUserId(userId);
        n.setNewSignal(flag(prefs.get("new_signal")));
        n.setProtectionWindow(flag(prefs.get("protection_window")));
        n.setTakeProfitWindow(flag(prefs.get("take_profit_window")));
        n.setSettlement(flag(prefs.get("settlement")));
        n.setWeeklyScorecard(flag(prefs.get("weekly_scorecard")));
        n.setProductUpdate(flag(prefs.get("product_update")));
        n.setSilentReminder(flag(prefs.get("silent_reminder")));
        n.setManagementStop(flag(prefs.get("management_stop")));
        n.setManageAdoptedOnly(flag(prefs.get("manage_adopted_only")));
        return n;
    }

    private static String flag(Boolean value)
    {
        return value == null ? null : (value ? "1" : "0");
    }

    // ------------------------------------------------------------------ 校验

    private static String validDisplayName(Object value)
    {
        String name = value instanceof String text ? text.trim() : null;
        if (StringUtils.isEmpty(name) || name.length() > OptionMemberConstants.DISPLAY_NAME_MAX_LENGTH)
        {
            throw badRequest("displayName 需为 1–" + OptionMemberConstants.DISPLAY_NAME_MAX_LENGTH + " 个字符");
        }
        return name;
    }

    /**
     * 关注标的：大写字母、数字、点，1–10 位，最多 50 个，自动转大写并去重
     */
    private static List<String> validWatchSymbols(Object value)
    {
        if (!(value instanceof List<?> list))
        {
            throw badRequest("watchSymbols 必须是数组");
        }
        Set<String> symbols = new LinkedHashSet<>();
        for (Object item : list)
        {
            String symbol = item instanceof String text ? text.trim().toUpperCase() : null;
            if (symbol == null || !WATCH_SYMBOL.matcher(symbol).matches())
            {
                throw badRequest("关注标的格式不正确：" + item + "（大写字母、数字、点，1–10 位）");
            }
            symbols.add(symbol);
        }
        if (symbols.size() > OptionMemberConstants.WATCH_SYMBOL_MAX)
        {
            throw badRequest("关注标的最多 " + OptionMemberConstants.WATCH_SYMBOL_MAX + " 个");
        }
        return List.copyOf(symbols);
    }

    /**
     * 开关对象：只接受白名单键，值必须是布尔
     */
    private static Map<String, Boolean> validFlags(Object value, String[] keys, String field, String label)
    {
        if (!(value instanceof Map<?, ?> map))
        {
            throw badRequest(field + " 必须是对象");
        }
        List<String> allowed = Arrays.asList(keys);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet())
        {
            String key = String.valueOf(entry.getKey());
            if (!allowed.contains(key))
            {
                throw badRequest("未知的" + label + "：" + key);
            }
            if (!(entry.getValue() instanceof Boolean flag))
            {
                throw badRequest(label + " " + key + " 必须是 true 或 false");
            }
            flags.put(key, flag);
        }
        return flags;
    }

    private static Boolean validBoolean(Object value, String field)
    {
        if (!(value instanceof Boolean flag))
        {
            throw badRequest(field + " 必须是 true 或 false");
        }
        return flag;
    }

    /**
     * 乐观锁：请求带的 updatedAt 必须与库里一致（都没有也算一致），按秒比较
     */
    private static void checkUpdatedAt(Object requested, String stored)
    {
        Long requestedSecond = epochSecond(requested);
        Long storedSecond = stored == null ? null : OffsetDateTime.parse(stored).toEpochSecond();
        if (!Objects.equals(requestedSecond, storedSecond))
        {
            throw conflict();
        }
    }

    private static Long epochSecond(Object value)
    {
        if (value == null || (value instanceof String text && StringUtils.isBlank(text)))
        {
            return null;
        }
        if (!(value instanceof String text))
        {
            throw badRequest("updatedAt 格式不正确");
        }
        try
        {
            return OffsetDateTime.parse(text.trim()).toEpochSecond();
        }
        catch (DateTimeParseException e)
        {
            throw badRequest("updatedAt 格式不正确，应为上次读取返回的值");
        }
    }

    private static String updatedAt(Date updateTime, Date createTime)
    {
        return iso(updateTime != null ? updateTime : createTime);
    }

    private static String iso(Date time)
    {
        return time == null ? null
                : Instant.ofEpochMilli(time.getTime() / 1000 * 1000).atZone(OptionMarketCalendar.BEIJING_ZONE)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static ServiceException badRequest(String message)
    {
        return new ServiceException(message, HttpStatus.BAD_REQUEST);
    }

    private static ServiceException conflict()
    {
        return new ServiceException(CONFLICT_MSG, HttpStatus.CONFLICT);
    }
}
