package com.ruoyi.option.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionFeedBlock;
import com.ruoyi.option.domain.OptionFeedItem;
import com.ruoyi.option.domain.OptionFeedSnapshot;
import com.ruoyi.option.domain.OptionFeedSync;
import com.ruoyi.option.enums.OptionFeedList;
import com.ruoyi.option.mapper.OptionFeedBlockMapper;
import com.ruoyi.option.mapper.OptionFeedItemMapper;
import com.ruoyi.option.mapper.OptionFeedSyncMapper;
import com.ruoyi.option.utils.OptionJson;

/**
 * 按批次清单从多张表拼接 feed，字段顺序与上游一致
 * 
 * @author yscandice
 */
@Component
public class OptionFeedAssembler
{
    private static final int QUERY_CHUNK = 500;

    @Autowired
    private OptionFeedSyncMapper syncMapper;

    @Autowired
    private OptionFeedBlockMapper blockMapper;

    @Autowired
    private OptionFeedItemMapper itemMapper;

    /**
     * 拼接最近一次成功同步
     * 
     * @return 拼接结果，无数据返回 null
     */
    public OptionFeedSnapshot assembleLatest()
    {
        OptionFeedSync sync = syncMapper.selectLatestOptionFeedSync();
        return sync == null ? null : assemble(sync);
    }

    /**
     * 拼接指定批次
     * 
     * @param syncId 批次ID
     * @return 拼接结果
     */
    public OptionFeedSnapshot assemble(Long syncId)
    {
        OptionFeedSync sync = syncMapper.selectOptionFeedSyncById(syncId);
        if (sync == null)
        {
            throw new ServiceException("同步批次不存在：" + syncId);
        }
        return assemble(sync);
    }

    private OptionFeedSnapshot assemble(OptionFeedSync sync)
    {
        JsonNode fieldOrder = OptionJson.read(sync.getFieldOrder());
        ObjectNode scalars = (ObjectNode) OptionJson.read(sync.getScalarData());
        JsonNode blockVersions = OptionJson.read(sync.getBlockVersions());
        JsonNode manifest = OptionJson.read(sync.getManifest());

        Map<String, String> blocks = loadBlocks(blockVersions);
        Map<String, String> lists = loadLists(manifest);

        Map<String, StringBuilder> builders = new LinkedHashMap<>();
        builders.put(OptionFeedConstants.GROUP_FULL, new StringBuilder(4 * 1024 * 1024).append('{'));
        for (JsonNode keyNode : fieldOrder)
        {
            String key = keyNode.asText();
            String value;
            if (lists.containsKey(key))
            {
                value = lists.get(key);
            }
            else if (blockVersions.has(key))
            {
                value = blocks.get(key);
            }
            else
            {
                value = OptionJson.write(scalars.get(key));
            }
            String pair = OptionJson.write(key) + ':' + value;
            append(builders.get(OptionFeedConstants.GROUP_FULL), pair);
            append(builders.computeIfAbsent(OptionFeedConstants.groupOf(key), g -> new StringBuilder().append('{')), pair);
        }

        OptionFeedSnapshot snapshot = new OptionFeedSnapshot();
        snapshot.setSyncId(sync.getSyncId());
        snapshot.setGeneratedAt(sync.getGeneratedAt());
        snapshot.setSyncTime(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, sync.getSyncTime()));
        for (Map.Entry<String, StringBuilder> entry : builders.entrySet())
        {
            String body = entry.getValue().append('}').toString();
            snapshot.getBodies().put(entry.getKey(), body);
            snapshot.getEtags().put(entry.getKey(), OptionJson.etag(body));
        }
        return snapshot;
    }

    private static void append(StringBuilder builder, String pair)
    {
        if (builder.length() > 1)
        {
            builder.append(',');
        }
        builder.append(pair);
    }

    private Map<String, String> loadBlocks(JsonNode blockVersions)
    {
        Map<String, String> result = new HashMap<>();
        Set<Long> ids = new LinkedHashSet<>();
        blockVersions.forEach(node -> ids.add(node.asLong()));
        if (ids.isEmpty())
        {
            return result;
        }
        Map<Long, String> byId = new HashMap<>();
        for (OptionFeedBlock block : blockMapper.selectBlockByVersionIds(ids))
        {
            byId.put(block.getVersionId(), block.getPayload());
        }
        Iterator<Map.Entry<String, JsonNode>> it = blockVersions.fields();
        while (it.hasNext())
        {
            Map.Entry<String, JsonNode> entry = it.next();
            String payload = byId.get(entry.getValue().asLong());
            if (payload == null)
            {
                throw new ServiceException("feed 对象字段版本缺失：" + entry.getKey());
            }
            result.put(entry.getKey(), payload);
        }
        return result;
    }

    private Map<String, String> loadLists(JsonNode manifest)
    {
        Map<String, String> result = new HashMap<>();
        Map<String, Set<Long>> idsByTable = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = manifest.fields();
        while (it.hasNext())
        {
            Map.Entry<String, JsonNode> entry = it.next();
            OptionFeedList spec = OptionFeedList.ofField(entry.getKey());
            if (spec == null)
            {
                throw new ServiceException("未登记的数组字段：" + entry.getKey());
            }
            Set<Long> ids = idsByTable.computeIfAbsent(spec.getTableName(), t -> new LinkedHashSet<>());
            entry.getValue().forEach(node -> ids.add(node.asLong()));
        }

        Map<String, Map<Long, String>> itemsByTable = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : idsByTable.entrySet())
        {
            Map<Long, String> byId = new HashMap<>();
            List<Long> ids = new ArrayList<>(entry.getValue());
            for (int i = 0; i < ids.size(); i += QUERY_CHUNK)
            {
                for (OptionFeedItem item : itemMapper.selectItemByVersionIds(entry.getKey(),
                        ids.subList(i, Math.min(ids.size(), i + QUERY_CHUNK))))
                {
                    byId.put(item.getVersionId(), merge(item));
                }
            }
            itemsByTable.put(entry.getKey(), byId);
        }

        it = manifest.fields();
        while (it.hasNext())
        {
            Map.Entry<String, JsonNode> entry = it.next();
            Map<Long, String> byId = itemsByTable.get(OptionFeedList.ofField(entry.getKey()).getTableName());
            StringBuilder builder = new StringBuilder().append('[');
            for (JsonNode idNode : entry.getValue())
            {
                String payload = byId.get(idNode.asLong());
                if (payload == null)
                {
                    throw new ServiceException("feed 数组条目版本缺失：" + entry.getKey() + "#" + idNode.asLong());
                }
                if (builder.length() > 1)
                {
                    builder.append(',');
                }
                builder.append(payload);
            }
            result.put(entry.getKey(), builder.append(']').toString());
        }
        return result;
    }

    /**
     * 把最新的易变字段写回条目（保持原字段位置）
     */
    private static String merge(OptionFeedItem item)
    {
        if (item.getVolatileData() == null)
        {
            return item.getPayload();
        }
        ObjectNode payload = (ObjectNode) OptionJson.read(item.getPayload());
        OptionJson.read(item.getVolatileData()).fields().forEachRemaining(e -> payload.set(e.getKey(), e.getValue()));
        return OptionJson.write(payload);
    }
}
