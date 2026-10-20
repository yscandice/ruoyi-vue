package com.ruoyi.option.service;

import java.util.Map;
import com.ruoyi.option.domain.OptionFeedSnapshot;

/**
 * feed Redis 缓存 服务层
 * 
 * @author yscandice
 */
public interface IOptionFeedCacheService
{
    /**
     * 发布新版本：写入全部分组后再切换当前版本
     * 
     * @param snapshot 拼接结果
     */
    public void publish(OptionFeedSnapshot snapshot);

    /**
     * 当前版本元数据（syncId、generatedAt、各分组 ETag）；Redis 未命中时从多表拼接并回填
     * 
     * @return 元数据，无数据返回 null
     */
    public Map<String, Object> getMeta();

    /**
     * 读取分组（full 为完整 feed）；Redis 未命中时从多表拼接并回填
     * 
     * @param group 分组名
     * @return 内容，无数据或分组不存在返回 null
     */
    public FeedBody get(String group);

    /**
     * 会员读取 feed 分组（按当前请求的数据视图）：
     * 实时视图同 get；盘后视图只读 option:delayed:feed:*，缺失时从冻结表回填，仍没有时返回空结构（不回落实时、不从多表拼接）
     * 
     * @param group 分组
     * @return 内容
     */
    public FeedBody getView(String group);

    /**
     * 分组内容
     */
    public record FeedBody(String body, String etag, Long syncId, String generatedAt)
    {
    }
}
