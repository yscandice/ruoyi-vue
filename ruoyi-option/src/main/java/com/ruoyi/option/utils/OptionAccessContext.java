package com.ruoyi.option.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.ruoyi.option.enums.OptionAccessLevel;
import com.ruoyi.option.enums.OptionDataView;

/**
 * 会员请求的数据权限上下文（ThreadLocal），由 OptionAccessInterceptor 写入、请求结束后清理（技术文档 3.5）
 * 
 * 没有上下文（总开关关闭、定时同步、后台接口）时一律按实时视图处理，行为与接入前完全一样。
 * 
 * @author yscandice
 */
public class OptionAccessContext
{
    private static final ThreadLocal<OptionAccessContext> HOLDER = new ThreadLocal<>();

    /** 用户ID */
    private Long userId;

    /** 身份 */
    private OptionAccessLevel level;

    /** 数据视图 */
    private OptionDataView view;

    /** 匹配到的菜单 */
    private String resource;

    /** 体验期结束时间（东八区 ISO 8601） */
    private String trialEndsAt;

    /** 盘后视图的数据交易日（当前放出的冻结记录），还没有放出时为 null */
    private String dataTradeDate;

    /** 数据截止时间 close(数据交易日)（美东 ISO 8601） */
    private String dataAsOf;

    /** 放出时间（美东 ISO 8601） */
    private String releasedAt;

    /** 下次放出时间（美东 ISO 8601） */
    private String nextReleaseAt;

    /** 当前放出的冻结ID */
    private Long snapshotId;

    /** 该菜单的冻结数据可能过期（冻结时最近同步失败或早于收盘） */
    private boolean stale;

    /** 盘后视图读不到数据，已按 delayedFallback = EMPTY 返回空结构 */
    private boolean empty;

    public static OptionAccessContext get()
    {
        return HOLDER.get();
    }

    public static void set(OptionAccessContext context)
    {
        HOLDER.set(context);
    }

    public static void clear()
    {
        HOLDER.remove();
    }

    /**
     * 当前请求的数据视图，没有上下文时为实时视图
     */
    public static OptionDataView currentView()
    {
        OptionAccessContext context = HOLDER.get();
        return context == null || context.view == null ? OptionDataView.REALTIME : context.view;
    }

    /**
     * 当前请求是否读取盘后视图
     */
    public static boolean isDelayed()
    {
        return currentView() == OptionDataView.DELAYED;
    }

    /**
     * 临时按实时视图执行（只用于盘后视图缺失时取空结构的模板或 delayedFallback = REALTIME），执行完恢复原视图
     */
    public static <T> T realtime(Supplier<T> action)
    {
        OptionAccessContext context = HOLDER.get();
        if (context == null)
        {
            return action.get();
        }
        OptionDataView view = context.view;
        context.view = OptionDataView.REALTIME;
        try
        {
            return action.get();
        }
        finally
        {
            context.view = view;
        }
    }

    /**
     * 当前请求是否已有放出的盘后数据（还没有放出过时不必尝试从冻结表回填）
     */
    public static boolean hasRelease()
    {
        OptionAccessContext context = HOLDER.get();
        return context != null && context.snapshotId != null;
    }

    /**
     * 标记本次返回的是空结构
     */
    public static void markEmpty()
    {
        OptionAccessContext context = HOLDER.get();
        if (context != null)
        {
            context.empty = true;
        }
    }

    /**
     * 响应 access 段（技术文档 3.5）
     */
    public Map<String, Object> toAccessMap()
    {
        Map<String, Object> access = new LinkedHashMap<>();
        access.put("level", level == null ? null : level.getCode());
        access.put("view", view == null ? null : view.getCode());
        access.put("trialEndsAt", trialEndsAt);
        access.put("dataTradeDate", dataTradeDate);
        access.put("dataAsOf", dataAsOf);
        access.put("releasedAt", releasedAt);
        access.put("nextReleaseAt", nextReleaseAt);
        access.put("stale", stale);
        if (empty)
        {
            access.put("empty", true);
        }
        return access;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public OptionAccessLevel getLevel()
    {
        return level;
    }

    public void setLevel(OptionAccessLevel level)
    {
        this.level = level;
    }

    public OptionDataView getView()
    {
        return view;
    }

    public void setView(OptionDataView view)
    {
        this.view = view;
    }

    public String getResource()
    {
        return resource;
    }

    public void setResource(String resource)
    {
        this.resource = resource;
    }

    public String getTrialEndsAt()
    {
        return trialEndsAt;
    }

    public void setTrialEndsAt(String trialEndsAt)
    {
        this.trialEndsAt = trialEndsAt;
    }

    public String getDataTradeDate()
    {
        return dataTradeDate;
    }

    public void setDataTradeDate(String dataTradeDate)
    {
        this.dataTradeDate = dataTradeDate;
    }

    public String getDataAsOf()
    {
        return dataAsOf;
    }

    public void setDataAsOf(String dataAsOf)
    {
        this.dataAsOf = dataAsOf;
    }

    public String getReleasedAt()
    {
        return releasedAt;
    }

    public void setReleasedAt(String releasedAt)
    {
        this.releasedAt = releasedAt;
    }

    public String getNextReleaseAt()
    {
        return nextReleaseAt;
    }

    public void setNextReleaseAt(String nextReleaseAt)
    {
        this.nextReleaseAt = nextReleaseAt;
    }

    public Long getSnapshotId()
    {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId)
    {
        this.snapshotId = snapshotId;
    }

    public boolean isStale()
    {
        return stale;
    }

    public void setStale(boolean stale)
    {
        this.stale = stale;
    }

    public boolean isEmpty()
    {
        return empty;
    }
}
