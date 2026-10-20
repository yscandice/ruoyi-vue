package com.ruoyi.option.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionAccessConstants;
import com.ruoyi.option.domain.OptionAccessPolicy;
import com.ruoyi.option.domain.OptionCloseSnapshot;
import com.ruoyi.option.domain.OptionCloseSnapshotData;
import com.ruoyi.option.mapper.OptionAccessPolicyMapper;
import com.ruoyi.option.mapper.OptionCloseSnapshotDataMapper;
import com.ruoyi.option.mapper.OptionCloseSnapshotMapper;
import com.ruoyi.option.service.IOptionCloseSnapshotService;
import com.ruoyi.option.utils.OptionMarketCalendar;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 盘后视图 收盘冻结 / 放出 / 恢复 / 清理 服务层实现（技术文档 3.4）
 * 
 * <pre>
 * 冻结：对策略表中 delayed_mode = CLOSE_SNAPSHOT 的菜单，SCAN 实时前缀，把每个键的原始值（gzip）存入
 *       option_close_snapshot_data，只存库，不写盘后视图；feed 只取 option:feed:current 指向的当前批次。
 * 放出：把冻结数据写入 option:delayed:tmp:*，全部写完后在一个 MULTI 里逐键 RENAME 为 option:delayed:*，
 *       并删除上一轮多出来的键，切换对用户是原子的。
 * 恢复：服务启动或 Redis 丢失时，按 option:access:release:current（丢失时取库里最新 RELEASED）从库回填。
 * 值按 Redis 原始字节读写，不经过序列化器，盘后视图与实时视图的值完全一致。
 * </pre>
 * 
 * @author yscandice
 */
@Service
public class OptionCloseSnapshotServiceImpl implements IOptionCloseSnapshotService
{
    private static final Logger log = LoggerFactory.getLogger(OptionCloseSnapshotServiceImpl.class);

    /** Redis 前缀 option: 的长度，option:conduct:active → conduct:active */
    private static final int OPTION_PREFIX_LENGTH = "option:".length();

    private static final Pattern SYNC_ID = Pattern.compile("\"syncId\"\\s*:\\s*(\\d+)");

    private static final Pattern ENTRY_RESULT = Pattern.compile("\"result\"\\s*:\\s*\"(\\w+)\"");

    private static final int SCAN_COUNT = 500;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private OptionMarketCalendar marketCalendar;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private OptionCloseSnapshotMapper snapshotMapper;

    @Autowired
    private OptionCloseSnapshotDataMapper snapshotDataMapper;

    @Autowired
    private OptionAccessPolicyMapper accessPolicyMapper;

    @Override
    public List<OptionCloseSnapshot> selectOptionCloseSnapshotList(OptionCloseSnapshot snapshot)
    {
        return snapshotMapper.selectOptionCloseSnapshotList(snapshot);
    }

    @Override
    public Map<String, Object> selectOptionCloseSnapshotById(Long snapshotId)
    {
        OptionCloseSnapshot snapshot = requireSnapshot(snapshotId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("snapshot", snapshot);
        data.put("resources", snapshotDataMapper.selectSnapshotDataStats(snapshotId));
        return data;
    }

    // ------------------------------------------------------------------ 定时任务

    @Override
    public Map<String, Object> freezeClose()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        ZonedDateTime now = ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE);
        int buffer = marketCalendar.closeBufferMinutes();
        LocalDate session = marketCalendar.latestClosedSession(now, buffer);
        result.put("session", session.toString());
        String token = lock();
        if (token == null)
        {
            result.put("skipped", "冻结任务正在执行");
            return result;
        }
        try
        {
            // 第一步：今天是交易日且已过收盘冻结点，当天还没有成功的冻结记录时冻结
            if (session.equals(now.toLocalDate()))
            {
                OptionCloseSnapshot existing = snapshotMapper.selectOptionCloseSnapshotByTradeDate(session);
                if (existing == null || OptionAccessConstants.RESULT_FAILED.equals(existing.getResult()))
                {
                    result.put("freeze", doFreeze(session, existing, OptionAccessConstants.TRIGGER_JOB, "system"));
                }
                else
                {
                    result.put("freeze", "已冻结（" + existing.getResult() + "），跳过");
                }
            }
            else
            {
                result.put("freeze", "今天不是交易日或未到收盘冻结点，跳过");
            }
            // 第二步：放出 session 往前数 lag 个交易日的冻结数据
            result.put("release", releaseForSession(session, marketCalendar.closeCutoff(session, buffer)));
        }
        catch (Exception e)
        {
            log.error("盘后视图冻结任务失败", e);
            result.put("error", e.getMessage());
        }
        finally
        {
            unlock(token);
        }
        return result;
    }

    /**
     * 放出：目标为 session 往前数 lag 个交易日；没有冻结记录时往前找最近一份成功的并告警；只向前切换，不回退
     */
    private Object releaseForSession(LocalDate session, ZonedDateTime releasedAt)
    {
        int lag = marketCalendar.delayedLagSessions();
        LocalDate dataDate = lag == 0 ? session : marketCalendar.previousSession(session, lag);
        OptionCloseSnapshot target = snapshotMapper.selectSuccessSnapshotOnOrBefore(dataDate);
        if (target == null)
        {
            log.warn("盘后视图放出：{} 及之前没有成功的冻结记录，盘后版按 delayedFallback 处理", dataDate);
            return "没有可放出的冻结记录（目标交易日 " + dataDate + "）";
        }
        Map<String, Object> current = currentRelease();
        if (current != null)
        {
            Long currentId = toLong(current.get("snapshotId"));
            if (target.getSnapshotId().equals(currentId))
            {
                return "已放出 " + target.getTradeDate() + "，跳过";
            }
            String currentDate = String.valueOf(current.get("tradeDate"));
            if (currentDate.compareTo(target.getTradeDate().toString()) > 0)
            {
                return "当前放出 " + currentDate + " 晚于目标 " + target.getTradeDate() + "，跳过";
            }
        }
        if (!dataDate.equals(target.getTradeDate()))
        {
            log.warn("盘后视图放出：{} 没有冻结记录，改为放出最近一份 {}", dataDate, target.getTradeDate());
        }
        return doRelease(target, releasedAt, OptionAccessConstants.TRIGGER_JOB);
    }

    // ------------------------------------------------------------------ 手动操作

    @Override
    public Map<String, Object> freeze(boolean force, String operator)
    {
        ZonedDateTime now = ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate session = marketCalendar.isTradingDay(today) ? today : marketCalendar.previousSession(today, 1);
        OptionCloseSnapshot existing = snapshotMapper.selectOptionCloseSnapshotByTradeDate(session);
        if (!force)
        {
            if (existing != null && !OptionAccessConstants.RESULT_FAILED.equals(existing.getResult()))
            {
                throw new ServiceException(session + " 已冻结，如需重新冻结请选择强制冻结");
            }
            ZonedDateTime cutoff = marketCalendar.closeCutoff(session);
            if (now.isBefore(cutoff))
            {
                throw new ServiceException("未到收盘冻结点 " + cutoff.toLocalDateTime() + "（美东），盘中冻结请选择强制冻结");
            }
        }
        String token = requireLock();
        try
        {
            return doFreeze(session, existing, OptionAccessConstants.TRIGGER_MANUAL, operator);
        }
        finally
        {
            unlock(token);
        }
    }

    @Override
    public Map<String, Object> release(Long snapshotId)
    {
        OptionCloseSnapshot snapshot = requireSnapshot(snapshotId);
        if (OptionAccessConstants.RESULT_FAILED.equals(snapshot.getResult()))
        {
            throw new ServiceException("冻结失败的记录不能放出");
        }
        String token = requireLock();
        try
        {
            return doRelease(snapshot, ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE),
                    OptionAccessConstants.TRIGGER_MANUAL);
        }
        finally
        {
            unlock(token);
        }
    }

    @Override
    public Map<String, Object> restore(Long snapshotId)
    {
        OptionCloseSnapshot snapshot = requireSnapshot(snapshotId);
        if (OptionAccessConstants.RESULT_FAILED.equals(snapshot.getResult()))
        {
            throw new ServiceException("冻结失败的记录不能用于恢复");
        }
        String token = requireLock();
        try
        {
            return doRestore(snapshot);
        }
        finally
        {
            unlock(token);
        }
    }

    @Override
    public boolean restoreIfMissing()
    {
        Map<String, Object> current = currentRelease();
        boolean hasView = !scanKeys(OptionAccessConstants.DELAYED_PREFIX + "*", 1).isEmpty();
        if (hasView && redisCache.hasKey(OptionAccessConstants.RELEASE_CURRENT_KEY))
        {
            return false;
        }
        OptionCloseSnapshot snapshot = null;
        if (current != null)
        {
            snapshot = snapshotMapper.selectOptionCloseSnapshotById(toLong(current.get("snapshotId")));
        }
        if (snapshot == null)
        {
            snapshot = snapshotMapper.selectLatestReleasedSnapshot();
        }
        if (snapshot == null)
        {
            return false;
        }
        String token = lock();
        if (token == null)
        {
            return false;
        }
        try
        {
            Map<String, Object> result = doRestore(snapshot);
            log.info("盘后视图已从库里回填：{}", result);
            return true;
        }
        finally
        {
            unlock(token);
        }
    }

    /**
     * 服务启动后检查盘后视图，Redis 丢失时从库里回填，不需要重新冻结
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup()
    {
        try
        {
            restoreIfMissing();
        }
        catch (Exception e)
        {
            log.error("启动时回填盘后视图失败", e);
        }
    }

    @Override
    public int cleanSnapshots()
    {
        int keepDays = intConfig(OptionAccessConstants.CONFIG_SNAPSHOT_KEEP_DAYS,
                OptionAccessConstants.DEFAULT_SNAPSHOT_KEEP_DAYS);
        LocalDate boundary = LocalDate.now(OptionMarketCalendar.MARKET_ZONE).minusDays(keepDays);
        List<Long> ids = snapshotMapper.selectExpiredSnapshotIds(boundary);
        if (ids.isEmpty())
        {
            return 0;
        }
        Long[] array = ids.toArray(new Long[0]);
        snapshotDataMapper.deleteOptionCloseSnapshotDataBySnapshotIds(array);
        return snapshotMapper.deleteOptionCloseSnapshotByIds(array);
    }

    @Override
    public Map<String, Object> selectViewStatus()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("freezeStatus", readJson(OptionAccessConstants.FREEZE_STATUS_KEY));
        data.put("releaseCurrent", currentRelease());
        List<Map<String, Object>> resources = new ArrayList<>();
        int realtimeTotal = 0;
        int delayedTotal = 0;
        for (OptionAccessPolicy policy : freezePolicies())
        {
            int realtime = 0;
            int delayed = 0;
            for (String prefix : prefixes(policy))
            {
                realtime += collectKeys(prefix).size();
                delayed += scanKeys(delayedKey(prefix) + "*", 0).size();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", policy.getResourceCode());
            item.put("name", policy.getResourceName());
            item.put("prefix", policy.getRedisPrefix());
            item.put("realtimeKeys", realtime);
            item.put("delayedKeys", delayed);
            resources.add(item);
            realtimeTotal += realtime;
            delayedTotal += delayed;
        }
        data.put("resources", resources);
        data.put("realtimeKeys", realtimeTotal);
        data.put("delayedKeys", delayedTotal);
        data.put("tmpKeys", scanKeys(OptionAccessConstants.DELAYED_TMP_PREFIX + "*", 0).size());
        return data;
    }

    // ------------------------------------------------------------------ 冻结

    private Map<String, Object> doFreeze(LocalDate tradeDate, OptionCloseSnapshot existing, String trigger,
            String operator)
    {
        long start = System.currentTimeMillis();
        ZonedDateTime cutoff = marketCalendar.closeCutoff(tradeDate);
        Date marketClose = Date.from(ZonedDateTime
                .of(tradeDate, marketCalendar.closeTime(tradeDate), OptionMarketCalendar.MARKET_ZONE).toInstant());

        OptionCloseSnapshot snapshot = existing;
        if (snapshot == null)
        {
            snapshot = new OptionCloseSnapshot();
            snapshot.setTradeDate(tradeDate);
            fillCutoff(snapshot, cutoff);
            snapshot.setResult(OptionAccessConstants.RESULT_FAILED);
            snapshot.setStatus(OptionAccessConstants.STATUS_FROZEN);
            snapshot.setTriggerType(trigger);
            snapshot.setMessage("冻结中");
            snapshot.setCreateBy(operator);
            snapshotMapper.insertOptionCloseSnapshot(snapshot);
        }
        else
        {
            snapshotDataMapper.deleteOptionCloseSnapshotDataBySnapshotIds(new Long[] { snapshot.getSnapshotId() });
        }
        Long snapshotId = snapshot.getSnapshotId();

        int resourceCount = 0;
        int keyCount = 0;
        long totalBytes = 0;
        List<String> stale = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Object> sourceSync = new LinkedHashMap<>();
        List<OptionAccessPolicy> policies = freezePolicies();
        try
        {
            for (OptionAccessPolicy policy : policies)
            {
                String resource = policy.getResourceCode();
                try
                {
                    int resourceKeys = 0;
                    String staleReason = null;
                    Long syncId = null;
                    for (String prefix : prefixes(policy))
                    {
                        Map<String, String> syncStatus = readHash(prefix + OptionAccessConstants.SYNC_STATUS_SUFFIX);
                        String reason = staleReason(syncStatus, marketClose);
                        if (reason != null && staleReason == null)
                        {
                            staleReason = prefix + " " + reason;
                        }
                        if (syncId == null)
                        {
                            syncId = syncId(syncStatus);
                        }
                        for (String key : collectKeys(prefix))
                        {
                            OptionCloseSnapshotData row = readData(snapshotId, resource, key);
                            if (row == null)
                            {
                                continue;
                            }
                            totalBytes += (Integer) row.getParams().get("rawBytes");
                            snapshotDataMapper.insertOptionCloseSnapshotData(row);
                            resourceKeys++;
                        }
                    }
                    keyCount += resourceKeys;
                    resourceCount++;
                    sourceSync.put(resource, syncId);
                    if (resourceKeys == 0)
                    {
                        errors.add(resource + " 没有可冻结的键");
                    }
                    if (staleReason != null)
                    {
                        stale.add(resource);
                        log.warn("盘后视图冻结 STALE：{} {} 菜单 {}（{}），仍然冻结", tradeDate, resource,
                                policy.getResourceName(), staleReason);
                    }
                }
                catch (Exception e)
                {
                    log.error("盘后视图冻结失败：{} {}", tradeDate, resource, e);
                    errors.add(resource + " " + e.getMessage());
                }
            }
        }
        finally
        {
            String result = keyCount == 0 ? OptionAccessConstants.RESULT_FAILED
                    : errors.isEmpty() ? OptionAccessConstants.RESULT_SUCCESS : OptionAccessConstants.RESULT_PARTIAL;
            OptionCloseSnapshot update = new OptionCloseSnapshot();
            update.setSnapshotId(snapshotId);
            fillCutoff(update, cutoff);
            update.setResult(result);
            update.setResourceCount(resourceCount);
            update.setKeyCount(keyCount);
            update.setTotalBytes(totalBytes);
            update.setStaleResources(String.join(",", stale));
            update.setSourceSync(JSON.toJSONString(sourceSync));
            update.setCostMs((int) (System.currentTimeMillis() - start));
            update.setTriggerType(trigger);
            update.setMessage(StringUtils.substring(policies.isEmpty() ? "策略表没有 CLOSE_SNAPSHOT 菜单"
                    : errors.isEmpty() ? "冻结成功" : String.join("；", errors), 0, 500));
            snapshotMapper.updateOptionCloseSnapshot(update);
            snapshot = snapshotMapper.selectOptionCloseSnapshotById(snapshotId);
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("tradeDate", tradeDate.toString());
        status.put("snapshotId", snapshotId);
        status.put("result", snapshot.getResult());
        status.put("stale", !stale.isEmpty());
        status.put("staleResources", stale);
        status.put("resourceCount", resourceCount);
        status.put("keyCount", keyCount);
        status.put("totalBytes", totalBytes);
        status.put("costMs", snapshot.getCostMs());
        status.put("triggerType", trigger);
        status.put("message", snapshot.getMessage());
        status.put("frozenAt", DateUtils.getTime());
        redisCache.setCacheObject(OptionAccessConstants.FREEZE_STATUS_KEY, JSON.toJSONString(status));
        log.info("盘后视图冻结 {}", status);
        return status;
    }

    /**
     * 同步状态判断：最后一次同步失败，或最近成功时间早于收盘时间，视为数据可能过期（STALE）
     */
    private static String staleReason(Map<String, String> status, Date marketClose)
    {
        if (status.isEmpty())
        {
            return null;
        }
        String state = status.get("state");
        if (state != null)
        {
            if ("FAILED".equalsIgnoreCase(state) || "ERROR".equalsIgnoreCase(state))
            {
                return "最近一次同步失败：" + StringUtils.defaultString(status.get("message"));
            }
            Date lastSuccess = DateUtils.parseDate(status.get("lastSuccessAt"));
            if (lastSuccess == null || lastSuccess.before(marketClose))
            {
                return "最近成功同步 " + status.get("lastSuccessAt") + " 早于收盘";
            }
            return null;
        }
        // 按接口记录的状态（信息、资源）：任一接口最近一次失败
        for (Map.Entry<String, String> entry : status.entrySet())
        {
            Matcher matcher = ENTRY_RESULT.matcher(entry.getValue());
            if (matcher.find() && "FAILED".equalsIgnoreCase(matcher.group(1)))
            {
                return entry.getKey() + " 最近一次同步失败";
            }
        }
        return null;
    }

    private static Long syncId(Map<String, String> status)
    {
        for (String field : new String[] { "syncId", "lastSyncId" })
        {
            Long id = toLong(status.get(field));
            if (id != null)
            {
                return id;
            }
        }
        return null;
    }

    /**
     * 收集某个实时前缀下需要冻结的键：跳过同步状态、锁等运行键；feed 只取当前批次
     */
    private List<String> collectKeys(String prefix)
    {
        Set<String> keys = new TreeSet<>();
        if (OptionAccessConstants.FEED_PREFIX.equals(prefix))
        {
            byte[] current = getRaw(OptionAccessConstants.FEED_CURRENT_KEY);
            if (current != null)
            {
                keys.add(OptionAccessConstants.FEED_CURRENT_KEY);
                Matcher matcher = SYNC_ID.matcher(new String(current, StandardCharsets.UTF_8));
                if (matcher.find())
                {
                    keys.addAll(scanKeys(OptionAccessConstants.FEED_DATA_PREFIX + matcher.group(1) + ":*", 0));
                }
            }
            return new ArrayList<>(keys);
        }
        for (String key : scanKeys(prefix + "*", 0))
        {
            if (!skipKey(key))
            {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    private static boolean skipKey(String key)
    {
        for (String segment : OptionAccessConstants.FREEZE_SKIP_SEGMENTS)
        {
            if (key.contains(segment))
            {
                return true;
            }
        }
        for (String suffix : OptionAccessConstants.FREEZE_SKIP_SUFFIXES)
        {
            if (key.endsWith(suffix))
            {
                return true;
            }
        }
        for (String prefix : OptionAccessConstants.FREEZE_SKIP_PREFIXES)
        {
            if (key.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }

    private OptionCloseSnapshotData readData(Long snapshotId, String resource, String key)
    {
        DataType type = redisTemplate.execute((RedisCallback<DataType>) conn -> conn.keyCommands().type(bytes(key)));
        byte[] raw;
        String valueType;
        if (type == DataType.STRING)
        {
            raw = getRaw(key);
            valueType = "string";
        }
        else if (type == DataType.HASH)
        {
            Map<String, String> hash = readHash(key, false);
            raw = JSON.toJSONString(hash).getBytes(StandardCharsets.UTF_8);
            valueType = "hash";
        }
        else
        {
            if (type != null && type != DataType.NONE)
            {
                log.warn("盘后视图冻结：{} 类型 {} 暂不支持，跳过", key, type);
            }
            return null;
        }
        if (raw == null)
        {
            return null;
        }
        OptionCloseSnapshotData row = new OptionCloseSnapshotData();
        row.setSnapshotId(snapshotId);
        row.setResourceCode(resource);
        row.setRedisKey(key);
        row.setValueType(valueType);
        row.setContentHash(sha256(raw));
        row.setPayload(gzip(raw));
        row.getParams().put("rawBytes", raw.length);
        return row;
    }

    // ------------------------------------------------------------------ 放出 / 恢复

    private Map<String, Object> doRelease(OptionCloseSnapshot snapshot, ZonedDateTime releasedAt, String trigger)
    {
        Map<String, Object> result = writeView(snapshot);
        OptionCloseSnapshot update = new OptionCloseSnapshot();
        update.setSnapshotId(snapshot.getSnapshotId());
        update.setStatus(OptionAccessConstants.STATUS_RELEASED);
        update.setReleasedAt(Date.from(releasedAt.toInstant()));
        update.setReleasedAtUtc(wall(releasedAt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
        snapshotMapper.updateOptionCloseSnapshot(update);
        snapshotMapper.expireReleasedSnapshots(snapshot.getSnapshotId());
        setCurrentRelease(snapshot, releasedAt);
        result.put("releasedAt", iso(releasedAt));
        result.put("triggerType", trigger);
        log.info("盘后视图放出 {}", result);
        return result;
    }

    private Map<String, Object> doRestore(OptionCloseSnapshot snapshot)
    {
        Map<String, Object> result = writeView(snapshot);
        ZonedDateTime releasedAt = snapshot.getReleasedAt() == null
                ? ZonedDateTime.now(OptionMarketCalendar.MARKET_ZONE)
                : snapshot.getReleasedAt().toInstant().atZone(OptionMarketCalendar.MARKET_ZONE);
        setCurrentRelease(snapshot, releasedAt);
        result.put("restored", true);
        return result;
    }

    /**
     * 写入临时前缀，校验内容哈希，全部成功后在一个 MULTI 里 RENAME 为正式前缀并删除上一轮多出来的键
     */
    private Map<String, Object> writeView(OptionCloseSnapshot snapshot)
    {
        long start = System.currentTimeMillis();
        List<OptionCloseSnapshotData> rows = snapshotDataMapper
                .selectOptionCloseSnapshotDataBySnapshotId(snapshot.getSnapshotId());
        if (rows.isEmpty())
        {
            throw new ServiceException("冻结记录 " + snapshot.getSnapshotId() + " 没有冻结数据");
        }
        deleteKeys(scanKeys(OptionAccessConstants.DELAYED_TMP_PREFIX + "*", 0));
        Set<String> formalKeys = new LinkedHashSet<>();
        for (OptionCloseSnapshotData row : rows)
        {
            byte[] raw = gunzip(row.getPayload());
            if (!sha256(raw).equals(row.getContentHash()))
            {
                deleteKeys(scanKeys(OptionAccessConstants.DELAYED_TMP_PREFIX + "*", 0));
                throw new ServiceException("冻结数据校验失败：" + row.getRedisKey());
            }
            String rest = row.getRedisKey().substring(OPTION_PREFIX_LENGTH);
            String tmpKey = OptionAccessConstants.DELAYED_TMP_PREFIX + rest;
            if ("hash".equals(row.getValueType()))
            {
                JSONObject hash = JSON.parseObject(new String(raw, StandardCharsets.UTF_8));
                Map<byte[], byte[]> values = new LinkedHashMap<>();
                hash.forEach((field, value) -> values.put(bytes(field), bytes(String.valueOf(value))));
                redisTemplate.execute((RedisCallback<Object>) conn -> {
                    conn.keyCommands().del(bytes(tmpKey));
                    if (!values.isEmpty())
                    {
                        conn.hashCommands().hMSet(bytes(tmpKey), values);
                    }
                    return null;
                });
            }
            else
            {
                redisTemplate.execute((RedisCallback<Object>) conn -> conn.stringCommands().set(bytes(tmpKey), raw));
            }
            formalKeys.add(OptionAccessConstants.DELAYED_PREFIX + rest);
        }
        List<String> obsolete = new ArrayList<>();
        Set<String> formalSet = new HashSet<>(formalKeys);
        for (String key : scanKeys(OptionAccessConstants.DELAYED_PREFIX + "*", 0))
        {
            if (!key.startsWith(OptionAccessConstants.DELAYED_TMP_PREFIX) && !formalSet.contains(key))
            {
                obsolete.add(key);
            }
        }
        redisTemplate.execute((RedisCallback<Object>) conn -> {
            conn.multi();
            for (String formal : formalKeys)
            {
                String rest = formal.substring(OptionAccessConstants.DELAYED_PREFIX.length());
                conn.keyCommands().rename(bytes(OptionAccessConstants.DELAYED_TMP_PREFIX + rest), bytes(formal));
            }
            for (String key : obsolete)
            {
                conn.keyCommands().del(bytes(key));
            }
            return conn.exec();
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotId", snapshot.getSnapshotId());
        result.put("tradeDate", snapshot.getTradeDate().toString());
        result.put("keyCount", formalKeys.size());
        result.put("removedKeys", obsolete.size());
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    private void setCurrentRelease(OptionCloseSnapshot snapshot, ZonedDateTime releasedAt)
    {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("tradeDate", snapshot.getTradeDate().toString());
        current.put("snapshotId", snapshot.getSnapshotId());
        current.put("releasedAt", iso(releasedAt));
        current.put("staleResources", StringUtils.defaultString(snapshot.getStaleResources()));
        redisCache.setCacheObject(OptionAccessConstants.RELEASE_CURRENT_KEY, JSON.toJSONString(current));
    }

    /**
     * 当前放出记录：优先 Redis，丢失时取库里 released_at 最新的 RELEASED 记录
     */
    private Map<String, Object> currentRelease()
    {
        Map<String, Object> current = readJson(OptionAccessConstants.RELEASE_CURRENT_KEY);
        if (current != null)
        {
            return current;
        }
        OptionCloseSnapshot released = snapshotMapper.selectLatestReleasedSnapshot();
        if (released == null)
        {
            return null;
        }
        current = new LinkedHashMap<>();
        current.put("tradeDate", released.getTradeDate().toString());
        current.put("snapshotId", released.getSnapshotId());
        current.put("releasedAt", released.getReleasedAt() == null ? null
                : iso(released.getReleasedAt().toInstant().atZone(OptionMarketCalendar.MARKET_ZONE)));
        current.put("staleResources", StringUtils.defaultString(released.getStaleResources()));
        return current;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 读取以 JSON 文本存放的状态键（冻结状态、当前放出记录），供步骤 6 的拦截器直接解析
     */
    private Map<String, Object> readJson(String key)
    {
        Object value = redisCache.getCacheObject(key);
        if (value == null)
        {
            return null;
        }
        return JSON.parseObject(String.valueOf(value));
    }

    private OptionCloseSnapshot requireSnapshot(Long snapshotId)
    {
        OptionCloseSnapshot snapshot = snapshotMapper.selectOptionCloseSnapshotById(snapshotId);
        if (snapshot == null)
        {
            throw new ServiceException("冻结记录不存在：" + snapshotId);
        }
        return snapshot;
    }

    /**
     * 需要冻结的菜单：启用状态、delayed_mode = CLOSE_SNAPSHOT 且登记了实时前缀
     */
    private List<OptionAccessPolicy> freezePolicies()
    {
        OptionAccessPolicy query = new OptionAccessPolicy();
        query.setStatus("0");
        query.setDelayedMode(OptionAccessConstants.MODE_CLOSE_SNAPSHOT);
        List<OptionAccessPolicy> list = new ArrayList<>();
        for (OptionAccessPolicy policy : accessPolicyMapper.selectOptionAccessPolicyList(query))
        {
            if (!prefixes(policy).isEmpty())
            {
                list.add(policy);
            }
        }
        return list;
    }

    private static List<String> prefixes(OptionAccessPolicy policy)
    {
        List<String> list = new ArrayList<>();
        if (StringUtils.isEmpty(policy.getRedisPrefix()))
        {
            return list;
        }
        for (String item : policy.getRedisPrefix().split(","))
        {
            String prefix = item.trim();
            if (prefix.startsWith("option:") && prefix.length() > OPTION_PREFIX_LENGTH
                    && !prefix.startsWith(OptionAccessConstants.DELAYED_PREFIX))
            {
                list.add(prefix.endsWith(":") ? prefix : prefix + ":");
            }
        }
        return list;
    }

    private static String delayedKey(String realtimeKey)
    {
        return OptionAccessConstants.DELAYED_PREFIX + realtimeKey.substring(OPTION_PREFIX_LENGTH);
    }

    private static void fillCutoff(OptionCloseSnapshot snapshot, ZonedDateTime cutoff)
    {
        snapshot.setCutoffEt(wall(cutoff.toLocalDateTime()));
        snapshot.setCutoffTime(Date.from(cutoff.toInstant()));
        snapshot.setCutoffTimeUtc(wall(cutoff.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
    }

    /**
     * 按“墙上时间”写库：数据库连接时区为 GMT+8，构造一个在东八区显示为该时间的 Date
     */
    private static String iso(ZonedDateTime time)
    {
        return time.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static Date wall(LocalDateTime time)
    {
        return Date.from(time.atZone(OptionMarketCalendar.BEIJING_ZONE).toInstant());
    }

    private String lock()
    {
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(OptionAccessConstants.FREEZE_LOCK_KEY, token,
                Duration.ofMinutes(OptionAccessConstants.FREEZE_LOCK_MINUTES));
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    private String requireLock()
    {
        String token = lock();
        if (token == null)
        {
            throw new ServiceException("冻结 / 放出任务正在执行，请稍后再试");
        }
        return token;
    }

    private void unlock(String token)
    {
        if (token != null && token.equals(redisTemplate.opsForValue().get(OptionAccessConstants.FREEZE_LOCK_KEY)))
        {
            redisTemplate.delete(OptionAccessConstants.FREEZE_LOCK_KEY);
        }
    }

    private List<String> scanKeys(String pattern, int limit)
    {
        return redisTemplate.execute((RedisCallback<List<String>>) conn -> scan(conn, pattern, limit));
    }

    private static List<String> scan(RedisConnection conn, String pattern, int limit)
    {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build();
        try (Cursor<byte[]> cursor = conn.keyCommands().scan(options))
        {
            while (cursor.hasNext())
            {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (limit > 0 && keys.size() >= limit)
                {
                    break;
                }
            }
        }
        return keys;
    }

    private void deleteKeys(List<String> keys)
    {
        if (keys.isEmpty())
        {
            return;
        }
        byte[][] array = keys.stream().map(OptionCloseSnapshotServiceImpl::bytes).toArray(byte[][]::new);
        redisTemplate.execute((RedisCallback<Long>) conn -> conn.keyCommands().del(array));
    }

    private byte[] getRaw(String key)
    {
        return redisTemplate.execute((RedisCallback<byte[]>) conn -> conn.stringCommands().get(bytes(key)));
    }

    private Map<String, String> readHash(String key)
    {
        return readHash(key, true);
    }

    /**
     * 读取 hash 原始值；unquote = true 时去掉 FastJson 字符串引号（只用于读同步状态），冻结时保留原值
     */
    private Map<String, String> readHash(String key, boolean unquote)
    {
        Map<byte[], byte[]> raw = redisTemplate
                .execute((RedisCallback<Map<byte[], byte[]>>) conn -> conn.hashCommands().hGetAll(bytes(key)));
        Map<String, String> map = new LinkedHashMap<>();
        if (raw != null)
        {
            raw.forEach((field, value) -> {
                String text = new String(value, StandardCharsets.UTF_8);
                map.put(new String(field, StandardCharsets.UTF_8), unquote ? unquote(text) : text);
            });
        }
        return map;
    }

    /**
     * 值经 FastJson 序列化：字符串带引号，Long 带 L 后缀；这里只用于读取同步状态
     */
    private static String unquote(String value)
    {
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\""))
        {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.endsWith("L"))
        {
            text = text.substring(0, text.length() - 1);
        }
        try
        {
            return Long.valueOf(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private int intConfig(String key, int defaultValue)
    {
        String value = configService.selectConfigByKey(key);
        try
        {
            int parsed = StringUtils.isEmpty(value) ? defaultValue : Integer.parseInt(value.trim());
            return parsed < 0 ? defaultValue : parsed;
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private static byte[] bytes(String text)
    {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] data)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        }
        catch (Exception e)
        {
            throw new ServiceException("计算内容哈希失败：" + e.getMessage());
        }
    }

    private static byte[] gzip(byte[] data)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 4));
        try (GZIPOutputStream gzip = new GZIPOutputStream(out))
        {
            gzip.write(data);
        }
        catch (IOException e)
        {
            throw new ServiceException("压缩冻结数据失败：" + e.getMessage());
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data)
    {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data)))
        {
            return gzip.readAllBytes();
        }
        catch (IOException e)
        {
            throw new ServiceException("解压冻结数据失败：" + e.getMessage());
        }
    }
}
