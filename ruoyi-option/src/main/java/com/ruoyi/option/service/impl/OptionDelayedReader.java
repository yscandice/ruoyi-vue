package com.ruoyi.option.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.ruoyi.option.service.IOptionAccessService;
import com.ruoyi.option.service.IOptionCloseSnapshotService;
import com.ruoyi.option.utils.OptionAccessContext;
import com.ruoyi.option.utils.OptionCacheKeys;
import com.ruoyi.option.utils.OptionJson;

/**
 * 盘后视图读取（技术文档 3.5）：各菜单 Service 读取会员数据时，盘后版只读 option:delayed:*
 * 
 * <pre>
 * 1. 读 OptionCacheKeys.delayed(key)
 * 2. 读不到且已有放出记录 → 按当前放出记录从 option_close_snapshot_data 回填后再读
 * 3. 仍读不到 → delayedFallback = REALTIME 时回落实时；默认 EMPTY 返回同结构空数据（不回落实时、不从业务表回填）
 * </pre>
 * 
 * @author yscandice
 */
@Component
public class OptionDelayedReader
{
    private static final Logger log = LoggerFactory.getLogger(OptionDelayedReader.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IOptionCloseSnapshotService closeSnapshotService;

    @Autowired
    private IOptionAccessService accessService;

    /**
     * 读取盘后视图，缺失时返回实时内容的空结构
     * 
     * @param realtimeKey 实时键
     * @param realtimeReader 实时读取（只用于取空结构的模板或 delayedFallback = REALTIME）
     * @return 内容，实时也没有数据时为 null
     */
    public String read(String realtimeKey, Supplier<String> realtimeReader)
    {
        return read(realtimeKey, realtimeReader, true);
    }

    /**
     * 读取盘后视图
     * 
     * @param realtimeKey 实时键
     * @param realtimeReader 实时读取
     * @param emptyStructure 缺失时是否返回空结构；false 时返回 null（按“该条目不存在”处理，如信息详情）
     * @return 内容
     */
    public String read(String realtimeKey, Supplier<String> realtimeReader, boolean emptyStructure)
    {
        String key = OptionCacheKeys.delayed(realtimeKey);
        String body = stringRedisTemplate.opsForValue().get(key);
        if (body == null && OptionAccessContext.hasRelease() && closeSnapshotService.restoreIfMissing())
        {
            body = stringRedisTemplate.opsForValue().get(key);
        }
        if (body != null)
        {
            return body;
        }
        if (accessService.isDelayedFallbackRealtime())
        {
            log.warn("盘后视图缺少 {}，按 delayedFallback=REALTIME 回落实时", key);
            return OptionAccessContext.realtime(realtimeReader);
        }
        OptionAccessContext.markEmpty();
        if (!emptyStructure)
        {
            return null;
        }
        // 只借用实时数据的结构，不带任何实际值
        String realtime = OptionAccessContext.realtime(realtimeReader);
        return realtime == null ? null : OptionJson.emptyStructure(realtime);
    }

    /**
     * 盘后视图的同步状态（代替实时同步状态，不暴露实时同步时间）
     * 
     * @return state / message / dataTradeDate / dataAsOf / releasedAt / stale
     */
    public Map<String, Object> status()
    {
        OptionAccessContext context = OptionAccessContext.get();
        Map<String, Object> status = new LinkedHashMap<>();
        String tradeDate = context == null ? null : context.getDataTradeDate();
        status.put("state", "DELAYED");
        status.put("message", tradeDate == null ? "盘后数据尚未放出" : "盘后数据（交易日 " + tradeDate + "）");
        status.put("dataTradeDate", tradeDate);
        status.put("dataAsOf", context == null ? null : context.getDataAsOf());
        status.put("releasedAt", context == null ? null : context.getReleasedAt());
        status.put("nextReleaseAt", context == null ? null : context.getNextReleaseAt());
        status.put("stale", context != null && context.isStale());
        return status;
    }
}
