package com.ruoyi.option.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.option.constant.OptionFeedConstants;
import com.ruoyi.option.domain.OptionFeedBlock;
import com.ruoyi.option.domain.OptionFeedItem;
import com.ruoyi.option.domain.OptionFeedSync;
import com.ruoyi.option.enums.OptionFeedList;
import com.ruoyi.option.mapper.OptionFeedBlockMapper;
import com.ruoyi.option.mapper.OptionFeedItemMapper;
import com.ruoyi.option.mapper.OptionFeedSyncMapper;
import com.ruoyi.option.utils.OptionJson;

/**
 * feed 拆分写库：一次同步一个事务
 * 
 * <ul>
 * <li>顶层标量 → option_feed_sync.scalar_data</li>
 * <li>对象及未登记数组 → option_feed_block（按字段 + 内容哈希去重）</li>
 * <li>登记数组 → 各条目表（按条目主键 + 内容哈希去重，易变时间戳原地更新）</li>
 * <li>本次包含的版本及顺序 → option_feed_sync.manifest / block_versions</li>
 * </ul>
 * 
 * @author yscandice
 */
@Component
public class OptionFeedStorage
{
    /** 每批插入条数（history 单条最大约 20KB） */
    private static final int INSERT_CHUNK = 50;

    /** IN 查询每批数量 */
    private static final int QUERY_CHUNK = 500;

    @Autowired
    private OptionFeedSyncMapper syncMapper;

    @Autowired
    private OptionFeedBlockMapper blockMapper;

    @Autowired
    private OptionFeedItemMapper itemMapper;

    /**
     * 拆分并写库
     * 
     * @param root feed 根节点
     * @param upstreamEtag 上游 ETag
     * @param rawBytes 上游响应字节数
     * @param startMillis 开始时间
     * @return 批次
     */
    @Transactional(rollbackFor = Exception.class)
    public OptionFeedSync store(ObjectNode root, String upstreamEtag, int rawBytes, long startMillis)
    {
        OptionFeedSync sync = new OptionFeedSync();
        sync.setSyncTime(new Date());
        sync.setUpstreamEtag(upstreamEtag);
        sync.setGeneratedAt(text(root, "generated_at"));
        sync.setEtDay(text(root, "etDay"));
        sync.setMarketEtDay(text(root, "marketEtDay"));
        sync.setMarketPhase(text(root, "marketPhase"));
        sync.setMode(text(root, "mode"));
        sync.setSchemaVersion(root.path("schema_version").isInt() ? root.get("schema_version").asInt() : null);
        sync.setSourceStatus(text(root, "status"));
        sync.setRawBytes(rawBytes);
        sync.setStatus("0");
        syncMapper.insertOptionFeedSync(sync);
        Long syncId = sync.getSyncId();

        ArrayNode fieldOrder = OptionJson.MAPPER.createArrayNode();
        ObjectNode scalars = OptionJson.MAPPER.createObjectNode();
        Map<String, JsonNode> blocks = new LinkedHashMap<>();
        Map<OptionFeedList, ArrayNode> lists = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            fieldOrder.add(key);
            OptionFeedList spec = OptionFeedList.ofField(key);
            if (!value.isContainerNode())
            {
                scalars.set(key, value);
            }
            else if (spec != null && value.isArray() && allObjects(value))
            {
                lists.put(spec, (ArrayNode) value);
            }
            else
            {
                blocks.put(key, value);
            }
        }

        Set<String> changedGroups = new LinkedHashSet<>();
        int[] counter = new int[2];
        ObjectNode blockVersions = storeBlocks(syncId, blocks, changedGroups, counter);
        ObjectNode manifest = OptionJson.MAPPER.createObjectNode();
        for (Map.Entry<OptionFeedList, ArrayNode> entry : lists.entrySet())
        {
            ArrayNode ids = storeList(syncId, entry.getKey(), entry.getValue(), changedGroups, counter);
            manifest.set(entry.getKey().getField(), ids);
        }

        sync.setFieldOrder(OptionJson.write(fieldOrder));
        sync.setScalarData(OptionJson.write(scalars));
        sync.setBlockVersions(OptionJson.write(blockVersions));
        sync.setManifest(OptionJson.write(manifest));
        sync.setChangedGroups(String.join(",", changedGroups));
        sync.setItemCount(counter[0]);
        sync.setNewVersionCount(counter[1]);
        sync.setCostMs((int) (System.currentTimeMillis() - startMillis));
        syncMapper.updateOptionFeedSync(sync);
        return sync;
    }

    private ObjectNode storeBlocks(Long syncId, Map<String, JsonNode> blocks, Set<String> changedGroups, int[] counter)
    {
        ObjectNode versions = OptionJson.MAPPER.createObjectNode();
        if (blocks.isEmpty())
        {
            return versions;
        }
        Map<String, String> payloads = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : blocks.entrySet())
        {
            String payload = OptionJson.write(entry.getValue());
            payloads.put(entry.getKey(), payload);
            hashes.put(entry.getKey(), OptionJson.sha256(payload));
        }
        Map<String, Long> existing = new HashMap<>();
        for (OptionFeedBlock block : blockMapper.selectBlockByHashes(new LinkedHashSet<>(hashes.values())))
        {
            existing.put(block.getBlockKey() + "|" + block.getContentHash(), block.getVersionId());
        }
        List<Long> touched = new ArrayList<>();
        for (Map.Entry<String, String> entry : hashes.entrySet())
        {
            String key = entry.getKey();
            Long versionId = existing.get(key + "|" + entry.getValue());
            if (versionId == null)
            {
                OptionFeedBlock block = new OptionFeedBlock();
                block.setBlockKey(key);
                block.setContentHash(entry.getValue());
                block.setPayload(payloads.get(key));
                block.setFirstSyncId(syncId);
                block.setLastSyncId(syncId);
                blockMapper.insertBlock(block);
                versionId = block.getVersionId();
                changedGroups.add(OptionFeedConstants.groupOf(key));
                counter[1]++;
            }
            else
            {
                touched.add(versionId);
            }
            versions.put(key, versionId);
        }
        if (!touched.isEmpty())
        {
            blockMapper.touchBlocks(syncId, touched);
        }
        return versions;
    }

    private ArrayNode storeList(Long syncId, OptionFeedList spec, ArrayNode array, Set<String> changedGroups,
            int[] counter)
    {
        String table = spec.getTableName();
        List<OptionFeedItem> items = new ArrayList<>(array.size());
        for (JsonNode node : array)
        {
            items.add(toItem(syncId, spec, (ObjectNode) node));
        }
        counter[0] += items.size();

        Map<String, OptionFeedItem> existing = new HashMap<>();
        List<String> hashes = new ArrayList<>(new LinkedHashSet<>(items.stream().map(OptionFeedItem::getContentHash).toList()));
        for (int i = 0; i < hashes.size(); i += QUERY_CHUNK)
        {
            List<String> part = hashes.subList(i, Math.min(hashes.size(), i + QUERY_CHUNK));
            for (OptionFeedItem old : itemMapper.selectItemByHashes(table, spec.getField(), part))
            {
                existing.put(old.getItemId() + "|" + old.getContentHash(), old);
            }
        }

        Map<String, OptionFeedItem> fresh = new LinkedHashMap<>();
        Set<Long> touched = new LinkedHashSet<>();
        Map<Long, OptionFeedItem> volatileChanged = new LinkedHashMap<>();
        for (OptionFeedItem item : items)
        {
            String key = item.getItemId() + "|" + item.getContentHash();
            OptionFeedItem old = existing.get(key);
            if (old != null)
            {
                item.setVersionId(old.getVersionId());
                touched.add(old.getVersionId());
                if (!Objects.equals(old.getVolatileData(), item.getVolatileData()))
                {
                    OptionFeedItem patch = new OptionFeedItem();
                    patch.setVersionId(old.getVersionId());
                    patch.setVolatileData(item.getVolatileData());
                    volatileChanged.put(old.getVersionId(), patch);
                    old.setVolatileData(item.getVolatileData());
                }
            }
            else
            {
                fresh.putIfAbsent(key, item);
            }
        }

        List<OptionFeedItem> inserts = new ArrayList<>(fresh.values());
        for (int i = 0; i < inserts.size(); i += INSERT_CHUNK)
        {
            itemMapper.insertItemBatch(table, inserts.subList(i, Math.min(inserts.size(), i + INSERT_CHUNK)));
        }
        List<Long> touchedIds = new ArrayList<>(touched);
        for (int i = 0; i < touchedIds.size(); i += QUERY_CHUNK)
        {
            itemMapper.touchItems(table, syncId, touchedIds.subList(i, Math.min(touchedIds.size(), i + QUERY_CHUNK)));
        }
        List<OptionFeedItem> patches = new ArrayList<>(volatileChanged.values());
        for (int i = 0; i < patches.size(); i += 200)
        {
            itemMapper.updateVolatileBatch(table, patches.subList(i, Math.min(patches.size(), i + 200)));
        }
        if (!inserts.isEmpty() || !patches.isEmpty())
        {
            changedGroups.add(OptionFeedConstants.groupOf(spec.getField()));
        }
        counter[1] += inserts.size();

        ArrayNode ids = OptionJson.MAPPER.createArrayNode();
        for (OptionFeedItem item : items)
        {
            Long versionId = item.getVersionId();
            if (versionId == null)
            {
                versionId = fresh.get(item.getItemId() + "|" + item.getContentHash()).getVersionId();
            }
            ids.add(versionId);
        }
        return ids;
    }

    private OptionFeedItem toItem(Long syncId, OptionFeedList spec, ObjectNode node)
    {
        ObjectNode stable = node.deepCopy();
        ObjectNode volatileNode = OptionJson.MAPPER.createObjectNode();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext())
        {
            String field = names.next();
            if (OptionFeedConstants.isVolatileItemField(field))
            {
                volatileNode.set(field, stable.remove(field));
            }
        }
        String hash = OptionJson.sha256(OptionJson.write(stable));
        OptionFeedItem item = new OptionFeedItem();
        item.setListType(spec.getField());
        String id = spec.getIdField() == null ? null : text(node, spec.getIdField());
        item.setItemId(StringUtils.isEmpty(id) ? "h:" + hash.substring(0, 40) : StringUtils.substring(id, 0, 128));
        item.setContentHash(hash);
        item.setSymbol(first(node, OptionFeedList.SYMBOL_FIELDS, 32));
        item.setSignalId(first(node, OptionFeedList.SIGNAL_FIELDS, 128));
        item.setItemState(first(node, OptionFeedList.STATE_FIELDS, 64));
        item.setItemKind(first(node, OptionFeedList.KIND_FIELDS, 64));
        item.setItemDate(first(node, OptionFeedList.DATE_FIELDS, 32));
        item.setPayload(OptionJson.write(node));
        item.setVolatileData(volatileNode.isEmpty() ? null : OptionJson.write(volatileNode));
        item.setFirstSyncId(syncId);
        item.setLastSyncId(syncId);
        return item;
    }

    private static boolean allObjects(JsonNode array)
    {
        for (JsonNode node : array)
        {
            if (!node.isObject())
            {
                return false;
            }
        }
        return true;
    }

    private static String first(JsonNode node, List<String> fields, int maxLength)
    {
        for (String field : fields)
        {
            String value = text(node, field);
            if (StringUtils.isNotEmpty(value))
            {
                return StringUtils.substring(value, 0, maxLength);
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isContainerNode() ? null : value.asText();
    }
}
