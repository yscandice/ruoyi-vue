package com.ruoyi.web.controller.option;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionFeedItem;
import com.ruoyi.option.domain.OptionFeedSnapshot;
import com.ruoyi.option.domain.OptionFeedSync;
import com.ruoyi.option.service.IOptionFeedCacheService;
import com.ruoyi.option.service.IOptionFeedCacheService.FeedBody;
import com.ruoyi.option.service.IOptionFeedSyncService;
import com.ruoyi.option.service.impl.OptionFeedAssembler;

/**
 * 期权今日 feed
 * 
 * 读接口只读 Redis（未命中时由服务层从多表拼接回填），返回与 OSM feed 相同结构的 JSON。
 * 
 * @author yscandice
 */
@RestController
@RequestMapping("/option")
public class OptionFeedController extends BaseController
{
    @Autowired
    private IOptionFeedCacheService cacheService;

    @Autowired
    private IOptionFeedSyncService syncService;

    @Autowired
    private OptionFeedAssembler assembler;

    /**
     * 完整 feed（与 OSM /api/member/feed 结构一致）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/today/feed")
    public void feed(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OptionFeedWriter.write(cacheService.get(OptionFeedConstants.GROUP_FULL), request, response);
    }

    /**
     * 当前版本元数据：syncId、generatedAt、各分组 ETag（前端轮询用）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/today/meta")
    public AjaxResult meta()
    {
        Map<String, Object> meta = cacheService.getMeta();
        if (meta == null)
        {
            return error("feed 暂无数据，请先登录 OSM 并执行同步");
        }
        meta.put("groups", OptionFeedConstants.GROUPS);
        return success(meta);
    }

    /**
     * 分组 feed：meta / desk / signal / management / settlement / history / health / session / extra
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/today/feed/{group}")
    public void group(@PathVariable("group") String group, HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        if (!OptionFeedConstants.GROUPS.containsKey(group) && !OptionFeedConstants.GROUP_EXTRA.equals(group))
        {
            OptionFeedWriter.notFound(response, "分组不存在：" + group);
            return;
        }
        FeedBody body = cacheService.get(group);
        if (body == null && cacheService.getMeta() != null)
        {
            OptionFeedWriter.notFound(response, "当前版本没有该分组：" + group);
            return;
        }
        OptionFeedWriter.write(body, request, response);
    }

    /**
     * 手动同步一次（忽略交易时段）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:run')")
    @Log(title = "期权feed同步", businessType = BusinessType.OTHER)
    @PostMapping("/sync/feed")
    public AjaxResult syncNow()
    {
        return success(syncService.syncFeed(true));
    }

    /**
     * 同步批次列表
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/sync/feed/list")
    public TableDataInfo syncList(OptionFeedSync sync)
    {
        startPage();
        List<OptionFeedSync> list = syncService.selectSyncList(sync);
        return getDataTable(list);
    }

    /**
     * 历史回放：从多表拼接指定批次的完整 feed
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/sync/feed/{syncId}")
    public void syncData(@PathVariable("syncId") Long syncId, HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        OptionFeedSnapshot snapshot = assembler.assemble(syncId);
        OptionFeedWriter.write(new FeedBody(snapshot.getBodies().get(OptionFeedConstants.GROUP_FULL),
                snapshot.getEtags().get(OptionFeedConstants.GROUP_FULL), syncId, snapshot.getGeneratedAt()),
                request, response);
    }

    /**
     * 条目版本列表（listType 必填，如 history、managementCards）
     */
    @PreAuthorize("@ss.hasPermi('option:sync:query')")
    @GetMapping("/sync/feed/item/list")
    public TableDataInfo itemList(OptionFeedItem item)
    {
        if (item == null || StringUtils.isEmpty(item.getListType()))
        {
            item = new OptionFeedItem();
            item.setListType("history");
        }
        startPage();
        List<OptionFeedItem> list = syncService.selectItemList(item);
        return getDataTable(list);
    }
}
