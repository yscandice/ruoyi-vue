package com.ruoyi.option.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.option.domain.OptionAcademyItem;
import com.ruoyi.option.domain.OptionAcademySync;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;

/**
 * OSM「学堂」菜单（/academy：学堂首页 / 六节课程 / 一周问答 / 术语速查） 服务层
 * 
 * @author yscandice
 */
public interface IOptionAcademyService
{
    /**
     * 同步：抓取学堂各页面 -> 解析 -> 按类型存库（内容去重）-> 写 Redis
     * 
     * @param force 是否强制写入（来源未变化也重新发布）
     * @param trigger 触发方式 JOB/MANUAL
     * @param operator 操作人
     * @return 同步结果
     */
    public Map<String, Object> sync(boolean force, String trigger, String operator);

    /**
     * 读取学堂汇总（书目、课程清单、各类型条数、同步状态）
     * 
     * @return 响应体，无数据返回 null
     */
    public FeedBody getSummary();

    /**
     * 读取某类型列表（列表不含正文 HTML，正文见单条接口）
     * 
     * @param kind 类型 page/lesson/qa/glossary
     * @param keyword 关键字（可选）
     * @return 响应体，无数据返回 null
     */
    public FeedBody getKind(String kind, String keyword);

    /**
     * 读取单条完整内容（含正文 HTML 与纯文本）
     * 
     * @param kind 类型
     * @param key 条目键（课程 slug / 问答期号 / 术语 / 栏目页 home、weekly-qa、glossary）
     * @return 响应体，无数据返回 null
     */
    public FeedBody getItem(String kind, String key);

    /**
     * 查询同步流水
     */
    public List<OptionAcademySync> selectSyncList(OptionAcademySync sync);

    /**
     * 查询条目版本列表
     */
    public List<OptionAcademyItem> selectItemList(String kind, OptionAcademyItem item);

    /**
     * 查询条目版本详情
     */
    public OptionAcademyItem selectItem(String kind, Long versionId);
}
