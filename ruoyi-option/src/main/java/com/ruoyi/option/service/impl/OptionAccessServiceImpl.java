package com.ruoyi.option.service.impl;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.domain.OptionAccessIdentity;
import com.ruoyi.option.domain.OptionAccessPolicy;
import com.ruoyi.option.domain.OptionMemberAccess;
import com.ruoyi.option.domain.OptionReleaseWindow;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.enums.OptionDataView;
import com.ruoyi.option.mapper.OptionAccessPolicyMapper;
import com.ruoyi.option.mapper.OptionMemberAccessMapper;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.utils.OptionMarketCalendar;
import com.ruoyi.system.service.ISysUserService;

/**
 * 会员数据权限 服务层实现
 * 
 * @author yscandice
 */
@Service
public class OptionAccessServiceImpl implements IOptionAccessService
{
    private static final Logger log = LoggerFactory.getLogger(OptionAccessServiceImpl.class);

    /** 会员时间对外输出为东八区 ISO 8601 */
    private static final ZoneId MEMBER_ZONE = OptionMarketCalendar.BEIJING_ZONE;

    @Autowired
    private OptionMarketCalendar marketCalendar;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private OptionMemberAccessMapper memberAccessMapper;

    @Autowired
    private OptionAccessPolicyMapper accessPolicyMapper;

    @Override
    public OptionReleaseWindow resolveReleaseWindow(ZonedDateTime at)
    {
        return marketCalendar.releaseWindow(at == null ? ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE) : at);
    }

    @Override
    public OptionAccessLevel resolve(Long userId)
    {
        return selectAccessIdentity(userId).levelAt(new Date());
    }

    @Override
    public OptionAccessIdentity selectAccessIdentity(Long userId)
    {
        String key = OptionAccessConstants.MEMBER_ACCESS_KEY + userId;
        OptionAccessIdentity identity = null;
        try
        {
            identity = redisCache.getCacheObject(key);
        }
        catch (Exception e)
        {
            log.warn("读取会员身份缓存失败 {}: {}", key, e.getMessage());
        }
        if (identity != null)
        {
            return identity;
        }
        identity = loadIdentity(userId);
        Date now = identity.getResolvedAt();
        long ttl = cacheSeconds(identity, now);
        try
        {
            redisCache.setCacheObject(key, identity, (int) ttl, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            log.warn("写入会员身份缓存失败 {}: {}", key, e.getMessage());
        }
        return identity;
    }

    @Override
    public OptionDataView resolveView(OptionAccessLevel level, OptionAccessPolicy policy)
    {
        if (level == null || level == OptionAccessLevel.DISABLED)
        {
            return OptionDataView.DENY;
        }
        if (policy == null)
        {
            return level.getDefaultView();
        }
        if (level.isRealtime())
        {
            OptionDataView view = OptionDataView.of(policy.getRealtimeMode());
            if (view == OptionDataView.SELF || OptionDataView.of(policy.getDelayedMode()) == OptionDataView.SELF)
            {
                return OptionDataView.SELF;
            }
            // 内部人员始终看实时
            if (level == OptionAccessLevel.STAFF)
            {
                return OptionDataView.REALTIME;
            }
            return view == null ? OptionDataView.REALTIME : view;
        }
        String mode = policy.getDelayedMode();
        if (StringUtils.isEmpty(mode) || OptionAccessConstants.MODE_CLOSE_SNAPSHOT.equalsIgnoreCase(mode))
        {
            return OptionDataView.DELAYED;
        }
        OptionDataView view = OptionDataView.of(mode);
        return view == null ? OptionDataView.DELAYED : view;
    }

    @Override
    public Map<String, Object> selectAccessInfo(Long userId)
    {
        Date now = new Date();
        OptionAccessIdentity identity = selectAccessIdentity(userId);
        OptionAccessLevel level = identity.levelAt(now);
        OptionDataView view = level.getDefaultView();
        OptionReleaseWindow window = resolveReleaseWindow(ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE));
        boolean delayed = view == OptionDataView.DELAYED;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", level.getCode());
        data.put("view", view.getCode());
        data.put("registerTime", iso(identity.getRegisterTime()));
        data.put("trialEndsAt", iso(identity.getTrialEndTime()));
        data.put("trialRemainingSeconds", identity.trialRemainingSeconds(now));
        data.put("paidUntil", iso(identity.getPaidUntil()));
        // 盘后数据交易日与放出时间只对盘后版有意义，实时视图返回 null
        data.put("dataTradeDate", delayed ? window.getDataTradeDate() : null);
        data.put("dataAsOf", delayed ? window.getDataAsOf() : null);
        data.put("releasedAt", delayed ? window.getReleasedAt() : null);
        data.put("nextReleaseAt", delayed ? window.getNextReleaseAt() : null);
        data.put("lagSessions", window.getLagSessions());
        data.put("marketOpen", window.isMarketOpen());
        data.put("policies", selectPolicyViews(level));
        return data;
    }

    @Override
    public void clearAccessCache(Long... userIds)
    {
        if (userIds == null || userIds.length == 0)
        {
            return;
        }
        List<String> keys = new ArrayList<>();
        Arrays.stream(userIds).filter(id -> id != null).forEach(id -> keys.add(OptionAccessConstants.MEMBER_ACCESS_KEY + id));
        redisCache.deleteObject(keys);
    }

    @Override
    public Map<String, Object> refreshLevels()
    {
        Date now = new Date();
        List<OptionMemberAccess> list = memberAccessMapper.selectOptionMemberAccessList(new OptionMemberAccess());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OptionAccessLevel level : OptionAccessLevel.values())
        {
            counts.put(level.getCode(), 0);
        }
        int changed = 0;
        for (OptionMemberAccess access : list)
        {
            OptionAccessLevel level = buildIdentity(access.getUserId(), access, now).levelAt(now);
            counts.merge(level.getCode(), 1, Integer::sum);
            if (!level.getCode().equals(access.getLevel()))
            {
                changed += memberAccessMapper.updateOptionMemberAccessLevel(access.getUserId(), level.getCode());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", list.size());
        result.put("changed", changed);
        result.put("levels", counts);
        return result;
    }

    /**
     * 从库里读取会员档案与若依用户，组装判断依据
     */
    private OptionAccessIdentity loadIdentity(Long userId)
    {
        return buildIdentity(userId, memberAccessMapper.selectOptionMemberAccessById(userId), new Date());
    }

    private OptionAccessIdentity buildIdentity(Long userId, OptionMemberAccess access, Date now)
    {
        OptionAccessIdentity identity = new OptionAccessIdentity();
        identity.setUserId(userId);
        identity.setResolvedAt(now);
        SysUser user = userId == null ? null : userService.selectUserById(userId);
        identity.setStaff(isStaff(userId, user));
        boolean userDisabled = user == null || "1".equals(user.getStatus()) || "2".equals(user.getDelFlag());
        identity.setDisabled(userDisabled || (access != null && "1".equals(access.getStatus())));
        if (access != null)
        {
            identity.setMember(true);
            identity.setRegisterTime(access.getRegisterTime());
            identity.setTrialEndTime(access.getTrialEndTime());
            identity.setPaidUntil(access.getPaidUntil());
        }
        return identity;
    }

    private static boolean isStaff(Long userId, SysUser user)
    {
        if (userId != null && SecurityUtils.isAdmin(userId))
        {
            return true;
        }
        if (user == null || user.getRoles() == null)
        {
            return false;
        }
        for (SysRole role : user.getRoles())
        {
            if (role != null && "0".equals(role.getStatus()) && (OptionAccessConstants.ROLE_ADMIN.equals(role.getRoleKey())
                    || OptionAccessConstants.ROLE_STAFF.equals(role.getRoleKey())))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 缓存时长：最长 5 分钟，并且不超过体验期 / 付费期剩余时长，到期后下一次请求重新计算
     */
    private static long cacheSeconds(OptionAccessIdentity identity, Date now)
    {
        long ttl = OptionAccessConstants.MEMBER_ACCESS_TTL_SECONDS;
        for (Date end : new Date[] { identity.getTrialEndTime(), identity.getPaidUntil() })
        {
            if (end != null && end.after(now))
            {
                ttl = Math.min(ttl, (end.getTime() - now.getTime() + 999) / 1000);
            }
        }
        return Math.max(1, ttl);
    }

    private List<Map<String, Object>> selectPolicyViews(OptionAccessLevel level)
    {
        OptionAccessPolicy query = new OptionAccessPolicy();
        query.setStatus("0");
        List<Map<String, Object>> policies = new ArrayList<>();
        for (OptionAccessPolicy policy : accessPolicyMapper.selectOptionAccessPolicyList(query))
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", policy.getResourceCode());
            item.put("name", policy.getResourceName());
            item.put("view", resolveView(level, policy).getCode());
            policies.add(item);
        }
        return policies;
    }

    private static String iso(Date time)
    {
        return time == null ? null
                : time.toInstant().atZone(MEMBER_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
