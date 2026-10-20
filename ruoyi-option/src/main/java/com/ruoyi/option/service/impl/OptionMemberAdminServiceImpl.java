package com.ruoyi.option.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionMemberConstants;
import com.ruoyi.option.domain.OptionMemberAccess;
import com.ruoyi.option.domain.OptionMemberRegisterLog;
import com.ruoyi.option.domain.vo.OptionMemberVo;
import com.ruoyi.option.mapper.OptionMemberAccessMapper;
import com.ruoyi.option.mapper.OptionMemberAdminMapper;
import com.ruoyi.option.mapper.OptionMemberRegisterLogMapper;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionMemberAdminService;
import com.ruoyi.option.service.IOptionMemberSettingsService;
import com.ruoyi.option.utils.OptionMarketCalendar;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.mapper.SysLogininforMapper;
import com.ruoyi.system.mapper.SysUserMapper;

/**
 * 会员后台管理Service业务层处理
 * 
 * @author yscandice
 */
@Service
public class OptionMemberAdminServiceImpl implements IOptionMemberAdminService
{
    private static final Logger log = LoggerFactory.getLogger(OptionMemberAdminServiceImpl.class);

    /** 近几天的注册趋势 */
    private static final int STATS_DAYS = 7;

    /** 单次调整体验期的上限（小时） */
    private static final int MAX_EXTEND_HOURS = 24 * 365;

    /** 批量调整一次最多处理的会员数 */
    private static final int MAX_BATCH = 500;

    /** 最近登录条数 */
    private static final int RECENT_LOGINS = 10;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private OptionMemberAdminMapper memberAdminMapper;

    @Autowired
    private OptionMemberAccessMapper memberAccessMapper;

    @Autowired
    private OptionMemberRegisterLogMapper registerLogMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysLogininforMapper logininforMapper;

    @Autowired
    private IOptionAccessService accessService;

    @Autowired
    private IOptionMemberSettingsService settingsService;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<OptionMemberVo> selectMemberList(OptionMemberVo query)
    {
        List<OptionMemberVo> list = memberAdminMapper.selectMemberList(query);
        if (!list.isEmpty())
        {
            Set<Long> online = onlineUserIds();
            list.forEach(member -> member.setOnline(online.contains(member.getUserId())));
        }
        return list;
    }

    @Override
    public Map<String, Object> selectMemberStats()
    {
        Map<String, Object> data = new LinkedHashMap<>(memberAdminMapper.selectMemberStats());
        LocalDate today = LocalDate.now(OptionMarketCalendar.BEIJING_ZONE);
        LocalDate since = today.minusDays(STATS_DAYS - 1);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : memberAdminMapper.selectRegisterDaily(beijingDate(since.atStartOfDay())))
        {
            counts.put(String.valueOf(row.get("day")), ((Number) row.get("count")).longValue());
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        for (LocalDate day = since; !day.isAfter(today); day = day.plusDays(1))
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", day.toString());
            item.put("count", counts.getOrDefault(day.toString(), 0L));
            daily.add(item);
        }
        data.put("daily", daily);
        return data;
    }

    @Override
    public Map<String, Object> selectMemberDetail(Long userId)
    {
        OptionMemberAccess access = requireMember(userId);
        SysUser user = userMapper.selectUserById(userId);

        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("userId", user.getUserId());
        basic.put("userName", user.getUserName());
        basic.put("email", user.getEmail());
        basic.put("nickName", user.getNickName());
        basic.put("avatar", user.getAvatar());
        basic.put("status", user.getStatus());
        basic.put("deptName", user.getDept() == null ? null : user.getDept().getDeptName());
        basic.put("loginIp", user.getLoginIp());
        basic.put("loginDate", user.getLoginDate());
        basic.put("pwdUpdateDate", user.getPwdUpdateDate());
        basic.put("createTime", user.getCreateTime());

        Map<String, Object> member = new LinkedHashMap<>();
        member.put("status", access.getStatus());
        member.put("registerTime", access.getRegisterTime());
        member.put("trialEndTime", access.getTrialEndTime());
        member.put("trialExtendHours", access.getTrialExtendHours());
        member.put("paidUntil", access.getPaidUntil());
        member.put("registerIp", access.getRegisterIp());
        member.put("deviceHash", StringUtils.isEmpty(access.getDeviceHash()) ? null : StringUtils.substring(access.getDeviceHash(), 0, 12) + "…");
        member.put("lastAccessTime", access.getLastAccessTime());
        member.put("updateBy", access.getUpdateBy());
        member.put("updateTime", access.getUpdateTime());
        member.put("remark", access.getRemark());

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("profile", settingsService.selectProfile(userId));
        settings.put("notifications", settingsService.selectNotifications(userId));
        settings.put("updates", settingsService.selectUpdateProgress(userId));

        SysLogininfor query = new SysLogininfor();
        query.setUserName(user.getUserName());
        PageHelper.startPage(1, RECENT_LOGINS, false);
        List<SysLogininfor> logins;
        try
        {
            logins = logininforMapper.selectLogininforList(query);
        }
        finally
        {
            PageHelper.clearPage();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", basic);
        data.put("member", member);
        data.put("access", accessService.selectAccessInfo(userId));
        data.put("settings", settings);
        data.put("sessions", sessionCount(userId));
        data.put("recentLogins", logins);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int extendTrial(List<Long> userIds, Integer extendHours, String remark)
    {
        if (extendHours == null || extendHours == 0 || Math.abs(extendHours) > MAX_EXTEND_HOURS)
        {
            throw badRequest("extendHours 必须是非 0 整数，绝对值不超过 " + MAX_EXTEND_HOURS);
        }
        if (StringUtils.length(remark) > 500)
        {
            throw badRequest("备注不能超过 500 个字符");
        }
        if (userIds == null || userIds.isEmpty())
        {
            throw badRequest("请选择会员");
        }
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(userIds));
        distinct.removeIf(id -> id == null);
        if (distinct.size() > MAX_BATCH)
        {
            throw badRequest("一次最多处理 " + MAX_BATCH + " 个会员");
        }
        List<Long> members = distinct.isEmpty() ? List.of() : memberAdminMapper.selectMemberIds(distinct);
        if (members.isEmpty())
        {
            throw new ServiceException("会员不存在", HttpStatus.NOT_FOUND);
        }
        int rows = memberAdminMapper.extendTrial(members, extendHours, StringUtils.trimToNull(remark), SecurityUtils.getUsername());
        afterChange(members.toArray(new Long[0]));
        return rows;
    }

    @Override
    public int updatePaidUntil(Long userId, Map<String, Object> body)
    {
        requireMember(userId);
        if (body == null || !body.containsKey("paidUntil"))
        {
            throw badRequest("paidUntil 必填，传 null 表示取消付费");
        }
        Date paidUntil = parseTime(body.get("paidUntil"));
        int rows = memberAdminMapper.updatePaidUntil(userId, paidUntil, SecurityUtils.getUsername());
        afterChange(userId);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStatus(Long userId, String status)
    {
        requireMember(userId);
        if (!"0".equals(status) && !"1".equals(status))
        {
            throw badRequest("status 只能是 0（启用）或 1（停用）");
        }
        int rows = memberAdminMapper.updateMemberStatus(userId, status, SecurityUtils.getUsername());
        userMapper.updateUserStatus(userId, status);
        afterChange(userId);
        if ("1".equals(status))
        {
            forceLogout(userId);
        }
        return rows;
    }

    @Override
    public int resetPwd(Long userId, String password)
    {
        requireMember(userId);
        if (password == null || password.length() < OptionMemberConstants.PASSWORD_MIN_LENGTH
                || password.length() > OptionMemberConstants.PASSWORD_MAX_LENGTH || password.chars().anyMatch(Character::isWhitespace)
                || password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit))
        {
            throw badRequest("密码需为 " + OptionMemberConstants.PASSWORD_MIN_LENGTH + "–" + OptionMemberConstants.PASSWORD_MAX_LENGTH
                    + " 位，且同时包含字母和数字");
        }
        int rows = userMapper.resetUserPwd(userId, SecurityUtils.encryptPassword(password));
        forceLogout(userId);
        return rows;
    }

    @Override
    public int forceLogout(Long userId)
    {
        int count = 0;
        for (String key : tokenKeys())
        {
            LoginUser loginUser = loginUser(key);
            if (loginUser != null && userId.equals(loginUser.getUserId()) && redisCache.deleteObject(key))
            {
                count++;
            }
        }
        accessService.clearAccessCache(userId);
        log.info("会员 {} 强制下线，删除会话 {} 个", userId, count);
        return count;
    }

    @Override
    public List<OptionMemberRegisterLog> selectRegisterLogList(OptionMemberRegisterLog registerLog)
    {
        return registerLogMapper.selectOptionMemberRegisterLogList(registerLog);
    }

    @Override
    public void touchLastAccess(Long userId)
    {
        if (userId == null)
        {
            return;
        }
        try
        {
            Boolean first = redisCache.redisTemplate.opsForValue().setIfAbsent(OptionMemberConstants.LAST_ACCESS_KEY + userId, "1",
                    OptionMemberConstants.LAST_ACCESS_INTERVAL_MINUTES, TimeUnit.MINUTES);
            if (Boolean.TRUE.equals(first))
            {
                memberAdminMapper.updateLastAccessTime(userId);
            }
        }
        catch (RuntimeException e)
        {
            log.warn("写最近访问时间失败 userId={}: {}", userId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 内部方法

    private OptionMemberAccess requireMember(Long userId)
    {
        OptionMemberAccess access = userId == null ? null : memberAccessMapper.selectOptionMemberAccessById(userId);
        SysUser user = access == null ? null : userMapper.selectUserById(userId);
        if (access == null || user == null || "2".equals(user.getDelFlag()))
        {
            throw new ServiceException("会员不存在", HttpStatus.NOT_FOUND);
        }
        return access;
    }

    /**
     * 修改后：删除身份缓存（下一次请求立即按新身份返回），并刷新表里用于统计的 level
     */
    private void afterChange(Long... userIds)
    {
        accessService.clearAccessCache(userIds);
        for (Long userId : userIds)
        {
            try
            {
                memberAccessMapper.updateOptionMemberAccessLevel(userId, accessService.resolve(userId).getCode());
            }
            catch (RuntimeException e)
            {
                log.warn("刷新会员 {} 身份字段失败：{}", userId, e.getMessage());
            }
        }
    }

    private Set<Long> onlineUserIds()
    {
        Set<Long> ids = new HashSet<>();
        for (String key : tokenKeys())
        {
            LoginUser loginUser = loginUser(key);
            if (loginUser != null && loginUser.getUserId() != null)
            {
                ids.add(loginUser.getUserId());
            }
        }
        return ids;
    }

    private int sessionCount(Long userId)
    {
        int count = 0;
        for (String key : tokenKeys())
        {
            LoginUser loginUser = loginUser(key);
            if (loginUser != null && userId.equals(loginUser.getUserId()))
            {
                count++;
            }
        }
        return count;
    }

    private Collection<String> tokenKeys()
    {
        Collection<String> keys = redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
        return keys == null ? List.of() : keys;
    }

    private LoginUser loginUser(String key)
    {
        try
        {
            Object value = redisCache.getCacheObject(key);
            return value instanceof LoginUser loginUser ? loginUser : null;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    /**
     * 付费到期：支持 ISO 带时区（2026-12-31T23:59:59+08:00）或 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd（按东八区）；null 表示取消
     */
    private static Date parseTime(Object value)
    {
        if (value == null || (value instanceof String text && StringUtils.isBlank(text)))
        {
            return null;
        }
        if (!(value instanceof String text))
        {
            throw badRequest("paidUntil 格式不正确");
        }
        String s = text.trim();
        try
        {
            if (s.length() == 10)
            {
                return beijingDate(LocalDate.parse(s).atTime(23, 59, 59));
            }
            if (s.length() == 19 && s.charAt(10) == ' ')
            {
                return beijingDate(LocalDateTime.parse(s, DATE_TIME));
            }
            return Date.from(OffsetDateTime.parse(s).toInstant());
        }
        catch (DateTimeParseException e)
        {
            throw badRequest("paidUntil 格式不正确，例如 2026-12-31T23:59:59+08:00");
        }
    }

    private static Date beijingDate(LocalDateTime time)
    {
        return Date.from(time.atZone(OptionMarketCalendar.BEIJING_ZONE).toInstant());
    }

    private static ServiceException badRequest(String message)
    {
        return new ServiceException(message, HttpStatus.BAD_REQUEST);
    }
}
