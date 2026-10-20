package com.ruoyi.option.utils;

import com.ruoyi.option.constant.OptionAccessConstants;

/**
 * 会员读取数据时的 Redis 键切换（技术文档 3.5）
 * 
 * <pre>
 * view == REALTIME（或没有上下文）：原样返回
 * view == DELAYED                ：option:conduct:active → option:delayed:conduct:active
 * </pre>
 * 只在读取会员数据的方法里使用，同步、写入、管理接口仍然用实时键。
 * 
 * @author yscandice
 */
public class OptionCacheKeys
{
    private static final String OPTION_PREFIX = "option:";

    private OptionCacheKeys()
    {
    }

    /**
     * 按当前请求的数据视图返回要读取的键
     * 
     * @param realtimeKey 实时视图键
     * @return 实际读取的键
     */
    public static String view(String realtimeKey)
    {
        return OptionAccessContext.isDelayed() ? delayed(realtimeKey) : realtimeKey;
    }

    /**
     * 实时键对应的盘后视图键
     * 
     * @param realtimeKey 实时视图键
     * @return 盘后视图键
     */
    public static String delayed(String realtimeKey)
    {
        if (realtimeKey == null || !realtimeKey.startsWith(OPTION_PREFIX)
                || realtimeKey.startsWith(OptionAccessConstants.DELAYED_PREFIX))
        {
            return realtimeKey;
        }
        return OptionAccessConstants.DELAYED_PREFIX + realtimeKey.substring(OPTION_PREFIX.length());
    }
}
