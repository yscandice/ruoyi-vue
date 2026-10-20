package com.ruoyi.option.service.impl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.constant.OptionMemberConstants;
import com.ruoyi.option.domain.OptionMemberAccess;
import com.ruoyi.option.domain.OptionMemberRegisterLog;
import com.ruoyi.option.domain.model.OptionRegisterBody;
import com.ruoyi.option.domain.model.OptionRegisterCodeBody;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.mapper.OptionMemberAccessMapper;
import com.ruoyi.option.mapper.OptionMemberRegisterLogMapper;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionMailService;
import com.ruoyi.option.service.IOptionMemberRegisterService;
import com.ruoyi.option.utils.OptionJson;
import com.ruoyi.option.utils.OptionMarketCalendar;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 会员注册 服务层实现
 * 
 * <pre>
 * 验证码：6 位数字，Redis 只存 SHA-256(邮箱:验证码) 与错误次数，10 分钟有效，错 5 次作废，60 秒内不能重发
 * 限频  ：同一邮箱每天发码 ≤ 10 次、同一 IP 每天发码 ≤ 20 次；同一 IP 每天成功注册 ≤ registerIpDailyLimit 次
 * 体验期：注册时间 + trialHours；同一设备指纹 30 天内已领过体验期的，直接进入盘后版
 * 日志  ：所有发码和注册行为（成功 / 拒绝 / 失败）写入 option_member_register_log
 * </pre>
 * 
 * @author yscandice
 */
@Service
public class OptionMemberRegisterServiceImpl implements IOptionMemberRegisterService
{
    private static final Logger log = LoggerFactory.getLogger(OptionMemberRegisterServiceImpl.class);

    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+\\-]+@[a-z0-9\\-]+(\\.[a-z0-9\\-]+)*\\.[a-z]{2,}$");

    private static final Pattern DEVICE_HASH = Pattern.compile("^[0-9a-f]{64}$");

    private static final Pattern DIGITS = Pattern.compile("^\\d{" + OptionMemberConstants.CODE_LENGTH + "}$");

    /** 会员部门名称（ry_option_access.sql 初始化） */
    private static final String MEMBER_DEPT_NAME = "会员";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OptionMemberAccessMapper memberAccessMapper;

    @Autowired
    private OptionMemberRegisterLogMapper registerLogMapper;

    @Autowired
    private IOptionAccessService accessService;

    @Autowired
    private IOptionMailService mailService;

    @Override
    public Map<String, Object> sendCode(OptionRegisterCodeBody body)
    {
        String ip = IpUtils.getIpAddr();
        String email = normalizeEmail(body == null ? null : body.getEmail());
        String action = OptionMemberConstants.ACTION_SEND_CODE;
        validateEmailFormat(email, action, ip, null);
        validateCaptcha(email, body.getCode(), body.getUuid(), ip);
        validateRegisterOpen(email, action, ip, null);
        if (memberAccessMapper.selectOptionMemberAccessByEmail(email) != null || !userNameUnique(email))
        {
            throw reject(email, action, ip, null, "邮箱已注册", HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
        }
        Date today = todayStart();
        if (registerLogMapper.countRegisterLog(null, email, action, OptionMemberConstants.RESULT_SUCCESS, today)
                >= OptionMemberConstants.CODE_EMAIL_DAILY_LIMIT)
        {
            throw reject(email, action, ip, null, "邮箱当天发码超限", OptionMemberConstants.TOO_MANY_REQUESTS, "该邮箱今天获取验证码次数过多，请明天再试");
        }
        if (registerLogMapper.countRegisterLog(ip, null, action, OptionMemberConstants.RESULT_SUCCESS, today)
                >= OptionMemberConstants.CODE_IP_DAILY_LIMIT)
        {
            throw reject(email, action, ip, null, "IP 当天发码超限", OptionMemberConstants.TOO_MANY_REQUESTS, "当前网络今天获取验证码次数过多，请明天再试");
        }
        String limitKey = OptionMemberConstants.CODE_LIMIT_KEY + email;
        Boolean first = redisCache.redisTemplate.opsForValue().setIfAbsent(limitKey, "1",
                OptionMemberConstants.CODE_RESEND_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first))
        {
            long wait = Math.max(1, redisCache.getExpire(limitKey));
            throw reject(email, action, ip, null, "60 秒内重发", OptionMemberConstants.TOO_MANY_REQUESTS, "发送太频繁，请 " + wait + " 秒后再试");
        }

        String code = String.format("%0" + OptionMemberConstants.CODE_LENGTH + "d",
                RANDOM.nextInt((int) Math.pow(10, OptionMemberConstants.CODE_LENGTH)));
        String codeKey = OptionMemberConstants.CODE_KEY + email;
        JSONObject stored = new JSONObject();
        stored.put("hash", codeHash(email, code));
        stored.put("fails", 0);
        redisCache.setCacheObject(codeKey, stored.toJSONString(), OptionMemberConstants.CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        if (!mailService.sendRegisterCode(email, code, OptionMemberConstants.CODE_EXPIRE_SECONDS / 60))
        {
            // 发送失败：作废验证码和重发间隔，用户可以马上重试
            redisCache.deleteObject(Arrays.asList(codeKey, limitKey));
            writeLog(email, action, OptionMemberConstants.RESULT_FAILED, "邮件发送失败", ip, null);
            throw new ServiceException("发送失败，请稍后再试");
        }
        writeLog(email, action, OptionMemberConstants.RESULT_SUCCESS, null, ip, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resendAfter", OptionMemberConstants.CODE_RESEND_SECONDS);
        data.put("expiresIn", OptionMemberConstants.CODE_EXPIRE_SECONDS);
        return data;
    }

    @Override
    public Map<String, Object> register(OptionRegisterBody body)
    {
        String ip = IpUtils.getIpAddr();
        String email = normalizeEmail(body == null ? null : body.getEmail());
        String deviceHash = normalizeDevice(body == null ? null : body.getDeviceHash());
        String action = OptionMemberConstants.ACTION_REGISTER;
        validateEmailFormat(email, action, ip, deviceHash);
        validateRegisterOpen(email, action, ip, deviceHash);
        validatePassword(email, body.getPassword(), ip, deviceHash);
        int ipLimit = intConfig(OptionMemberConstants.CONFIG_REGISTER_IP_DAILY_LIMIT,
                OptionMemberConstants.DEFAULT_REGISTER_IP_DAILY_LIMIT);
        if (registerLogMapper.countRegisterLog(ip, null, action, OptionMemberConstants.RESULT_SUCCESS, todayStart()) >= ipLimit)
        {
            throw reject(email, action, ip, deviceHash, "IP 当天注册超限", OptionMemberConstants.TOO_MANY_REQUESTS, "当前网络今天注册次数已达上限，请明天再试");
        }

        String lockKey = OptionMemberConstants.REGISTER_LOCK_KEY + email;
        Boolean locked = redisCache.redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked))
        {
            throw new ServiceException("正在注册，请勿重复提交", OptionMemberConstants.TOO_MANY_REQUESTS);
        }
        try
        {
            if (memberAccessMapper.selectOptionMemberAccessByEmail(email) != null || !userNameUnique(email))
            {
                throw reject(email, action, ip, deviceHash, "邮箱已注册", HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
            }
            verifyEmailCode(email, body.getEmailCode(), ip, deviceHash);
            SysUser user;
            try
            {
                user = createMember(email, body.getPassword(), ip, deviceHash);
            }
            catch (DuplicateKeyException e)
            {
                throw reject(email, action, ip, deviceHash, "邮箱已注册（唯一键）", HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
            }
            redisCache.deleteObject(OptionMemberConstants.CODE_KEY + email);
            writeLog(email, action, OptionMemberConstants.RESULT_SUCCESS, null, ip, deviceHash);
            return login(user);
        }
        finally
        {
            redisCache.deleteObject(lockKey);
        }
    }

    /**
     * 一个事务里写 sys_user、sys_user_role（option_member）、option_member_access
     */
    private SysUser createMember(String email, String password, String ip, String deviceHash)
    {
        SysRole role = roleMapper.checkRoleKeyUnique(OptionAccessConstants.ROLE_MEMBER);
        if (role == null)
        {
            log.error("缺少会员角色 {}，请先执行 sql/ry_option_access.sql", OptionAccessConstants.ROLE_MEMBER);
            throw new ServiceException("注册暂不可用，请稍后再试");
        }
        Long deptId = memberDeptId();
        Date now = new Date();
        int trialHours = intConfig(OptionAccessConstants.CONFIG_TRIAL_HOURS, 72);
        boolean deviceUsed = deviceHash != null && memberAccessMapper.countMemberByDevice(deviceHash,
                new Date(now.getTime() - TimeUnit.DAYS.toMillis(OptionMemberConstants.DEVICE_TRIAL_DAYS))) > 0;
        // 同一设备 30 天内已领过体验期：体验期结束时间 = 注册时间，直接进入盘后版
        Date trialEnd = deviceUsed ? now : new Date(now.getTime() + TimeUnit.HOURS.toMillis(trialHours));

        SysUser user = new SysUser();
        user.setUserName(email);
        user.setNickName(StringUtils.substring(email.substring(0, email.indexOf('@')), 0, 30));
        user.setEmail(email);
        user.setPassword(SecurityUtils.encryptPassword(password));
        user.setDeptId(deptId);
        user.setStatus("0");
        user.setPwdUpdateDate(now);
        user.setRoleIds(new Long[] { role.getRoleId() });
        user.setCreateBy("register");
        user.setRemark("网站注册会员");

        OptionMemberAccess access = new OptionMemberAccess();
        access.setEmail(email);
        access.setLevel(deviceUsed ? OptionAccessLevel.DELAYED.getCode() : OptionAccessLevel.TRIAL.getCode());
        access.setRegisterTime(now);
        access.setRegisterTimeUtc(utcWall(now));
        access.setTrialEndTime(trialEnd);
        access.setTrialEndTimeUtc(utcWall(trialEnd));
        access.setTrialExtendHours(0);
        access.setStatus("0");
        access.setRegisterIp(ip);
        access.setDeviceHash(deviceHash);
        access.setCreateBy("register");
        access.setRemark(deviceUsed ? "同一设备 30 天内已领过体验期" : null);

        transactionTemplate.executeWithoutResult(status -> {
            userService.insertUser(user);
            access.setUserId(user.getUserId());
            memberAccessMapper.insertOptionMemberAccess(access);
        });
        accessService.clearAccessCache(user.getUserId());
        return userService.selectUserById(user.getUserId());
    }

    /**
     * 自动登录：签发若依 token（与 /login 相同），写登录日志与最近登录信息
     */
    private Map<String, Object> login(SysUser user)
    {
        Set<String> permissions = permissionService.getMenuPermission(user);
        LoginUser loginUser = new LoginUser(user.getUserId(), user.getDeptId(), user, permissions);
        String token = tokenService.createToken(loginUser);
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(user.getUserName(), Constants.LOGIN_SUCCESS, "注册成功并自动登录"));
        userService.updateLoginInfo(user.getUserId(), IpUtils.getIpAddr(), DateUtils.getNowDate());

        Map<String, Object> info = accessService.selectAccessInfo(user.getUserId());
        Map<String, Object> access = new LinkedHashMap<>();
        for (String field : new String[] { "level", "view", "trialEndsAt", "trialRemainingSeconds", "dataTradeDate",
                "dataAsOf", "releasedAt", "nextReleaseAt" })
        {
            access.put(field, info.get(field));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(Constants.TOKEN, token);
        data.put("access", access);
        return data;
    }

    private void verifyEmailCode(String email, String emailCode, String ip, String deviceHash)
    {
        String action = OptionMemberConstants.ACTION_REGISTER;
        String codeKey = OptionMemberConstants.CODE_KEY + email;
        Object value = redisCache.getCacheObject(codeKey);
        if (value == null)
        {
            throw reject(email, action, ip, deviceHash, "验证码过期", HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        JSONObject stored = JSON.parseObject(String.valueOf(value));
        String code = StringUtils.trim(emailCode);
        if (code != null && DIGITS.matcher(code).matches() && codeHash(email, code).equals(stored.getString("hash")))
        {
            return;
        }
        int fails = stored.getIntValue("fails") + 1;
        if (fails >= OptionMemberConstants.CODE_MAX_FAILS)
        {
            redisCache.deleteObject(codeKey);
            throw reject(email, action, ip, deviceHash, "验证码错误次数过多", HttpStatus.BAD_REQUEST, "验证码错误次数过多，请重新获取");
        }
        stored.put("fails", fails);
        long ttl = redisCache.getExpire(codeKey);
        redisCache.setCacheObject(codeKey, stored.toJSONString(), (int) Math.max(1, ttl), TimeUnit.SECONDS);
        throw reject(email, action, ip, deviceHash, "验证码错误", HttpStatus.BAD_REQUEST,
                "验证码错误，还可以尝试 " + (OptionMemberConstants.CODE_MAX_FAILS - fails) + " 次");
    }

    /**
     * 图形验证码（与若依登录相同：sys.account.captchaEnabled 关闭时不校验）
     */
    private void validateCaptcha(String email, String code, String uuid, String ip)
    {
        if (!configService.selectCaptchaEnabled())
        {
            return;
        }
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        String captcha = redisCache.getCacheObject(verifyKey);
        if (captcha == null)
        {
            throw reject(email, OptionMemberConstants.ACTION_SEND_CODE, ip, null, "图形码过期", HttpStatus.BAD_REQUEST, "图形验证码已失效");
        }
        redisCache.deleteObject(verifyKey);
        if (StringUtils.isEmpty(code) || !code.trim().equalsIgnoreCase(captcha))
        {
            throw reject(email, OptionMemberConstants.ACTION_SEND_CODE, ip, null, "图形码错误", HttpStatus.BAD_REQUEST, "图形验证码错误");
        }
    }

    private void validateEmailFormat(String email, String action, String ip, String deviceHash)
    {
        if (StringUtils.isEmpty(email) || email.length() > OptionMemberConstants.EMAIL_MAX_LENGTH
                || !EMAIL.matcher(email).matches())
        {
            throw reject(StringUtils.isEmpty(email) ? "-" : StringUtils.substring(email, 0, 128), action, ip, deviceHash,
                    "邮箱格式错误", HttpStatus.BAD_REQUEST, "邮箱格式不正确（最长 " + OptionMemberConstants.EMAIL_MAX_LENGTH + " 个字符）");
        }
    }

    /**
     * 注册开关与邮箱域名黑名单
     */
    private void validateRegisterOpen(String email, String action, String ip, String deviceHash)
    {
        if ("false".equalsIgnoreCase(StringUtils.trim(configService.selectConfigByKey(OptionMemberConstants.CONFIG_REGISTER_ENABLED))))
        {
            throw reject(email, action, ip, deviceHash, "注册已关闭", HttpStatus.FORBIDDEN, "当前暂未开放注册");
        }
        String domain = email.substring(email.indexOf('@') + 1);
        String blocked = StringUtils.defaultString(configService.selectConfigByKey(OptionMemberConstants.CONFIG_BLOCKED_EMAIL_DOMAINS));
        for (String item : blocked.split("[,，\\s]+"))
        {
            String rule = item.trim().toLowerCase(Locale.ROOT);
            if (rule.startsWith("@"))
            {
                rule = rule.substring(1);
            }
            if (!rule.isEmpty() && (domain.equals(rule) || domain.endsWith("." + rule)))
            {
                throw reject(email, action, ip, deviceHash, "黑名单域名 " + rule, HttpStatus.FORBIDDEN, "该邮箱域名不支持注册，请更换邮箱");
            }
        }
    }

    /**
     * 密码强度：8–20 位，同时包含字母和数字，不能包含空格
     */
    private void validatePassword(String email, String password, String ip, String deviceHash)
    {
        if (password == null || password.length() < OptionMemberConstants.PASSWORD_MIN_LENGTH
                || password.length() > OptionMemberConstants.PASSWORD_MAX_LENGTH || password.chars().anyMatch(Character::isWhitespace)
                || password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit))
        {
            throw reject(email, OptionMemberConstants.ACTION_REGISTER, ip, deviceHash, "密码强度不够", HttpStatus.BAD_REQUEST,
                    "密码需为 " + OptionMemberConstants.PASSWORD_MIN_LENGTH + "–" + OptionMemberConstants.PASSWORD_MAX_LENGTH
                            + " 位，且同时包含字母和数字");
        }
    }

    private boolean userNameUnique(String email)
    {
        SysUser query = new SysUser();
        query.setUserName(email);
        query.setEmail(email);
        return userService.checkUserNameUnique(query) && userService.checkEmailUnique(query);
    }

    private Long memberDeptId()
    {
        SysDept query = new SysDept();
        query.setDeptName(MEMBER_DEPT_NAME);
        List<SysDept> depts = deptMapper.selectDeptList(query);
        return depts.stream().filter(d -> MEMBER_DEPT_NAME.equals(d.getDeptName())).map(SysDept::getDeptId).findFirst()
                .orElseThrow(() -> {
                    log.error("缺少会员部门「{}」，请先执行 sql/ry_option_access.sql", MEMBER_DEPT_NAME);
                    return new ServiceException("注册暂不可用，请稍后再试");
                });
    }

    /**
     * 写拒绝日志并返回异常（code 为 400 / 403 / 409 / 429）
     */
    private ServiceException reject(String email, String action, String ip, String deviceHash, String reason, int code,
            String message)
    {
        writeLog(email, action, OptionMemberConstants.RESULT_REJECTED, reason, ip, deviceHash);
        return new ServiceException(message, code);
    }

    private void writeLog(String email, String action, String result, String reason, String ip, String deviceHash)
    {
        try
        {
            OptionMemberRegisterLog record = new OptionMemberRegisterLog();
            record.setEmail(StringUtils.substring(StringUtils.isEmpty(email) ? "-" : email, 0, 128));
            record.setAction(action);
            record.setResult(result);
            record.setReason(StringUtils.substring(reason, 0, 100));
            record.setIp(StringUtils.substring(ip, 0, 64));
            record.setDeviceHash(deviceHash);
            registerLogMapper.insertOptionMemberRegisterLog(record);
        }
        catch (Exception e)
        {
            log.error("写注册日志失败 {} {}: {}", action, result, e.getMessage());
        }
    }

    private static String normalizeEmail(String email)
    {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDevice(String deviceHash)
    {
        String value = StringUtils.trim(deviceHash);
        return value != null && DEVICE_HASH.matcher(value.toLowerCase(Locale.ROOT)).matches() ? value.toLowerCase(Locale.ROOT) : null;
    }

    private static String codeHash(String email, String code)
    {
        return OptionJson.sha256(email + ":" + code);
    }

    /**
     * 东八区当天 0 点（注册日志 create_time 为东八区）
     */
    private static Date todayStart()
    {
        return Date.from(LocalDate.now(OptionMarketCalendar.BEIJING_ZONE).atStartOfDay(OptionMarketCalendar.BEIJING_ZONE).toInstant());
    }

    /**
     * *_utc 列：JDBC 按东八区写入，传入“UTC 墙上时间按东八区解释”的 Date
     */
    private static Date utcWall(Date time)
    {
        LocalDateTime utc = LocalDateTime.ofInstant(time.toInstant(), ZoneOffset.UTC);
        return Date.from(utc.atZone(OptionMarketCalendar.BEIJING_ZONE).toInstant());
    }

    private int intConfig(String key, int defaultValue)
    {
        try
        {
            String value = configService.selectConfigByKey(key);
            return StringUtils.isEmpty(value) ? defaultValue : Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}
