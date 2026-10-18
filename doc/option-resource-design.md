# OSM 会员接口 2–9 同步设计

与 feed（见 `option-feed-design.md`）同一套思路：**定时同步 → 存库 → Redis → 前端只读**，前端不再直连 OSM。

## 1. 接口与同步策略

| # | 接口 | 资源键 | 版本表 | 同步时段 | Quartz cron |
|---|------|--------|--------|----------|-------------|
| 2 | `/api/member/seller-guide` | seller-guide | option_res_seller_guide | 任意 | `0 10 8 * * ?` 每天 08:10 |
| 3 | `/api/member/entitlement` | entitlement | option_res_entitlement | 任意 | `0 2 * * * ?` 每小时 |
| 4 | `/api/member/decisions` | decisions | option_res_decision | 美东交易时段 | `0 0/5 * * * ?` |
| 5 | `/api/member/management-choices` | management-choices | option_res_management_choice | 美东交易时段 | `0 0/5 * * * ?` |
| 6 | `/api/member/premarket?d=` | premarket | option_res_premarket | 美东盘前 04:00–09:30（交易日） | `0 0/15 * * * ?` |
| 7 | `/api/member/updates` | updates | option_res_update | 任意 | `0 4 * * * ?` 每小时 |
| 8 | `/api/member/support` | support | option_res_support | 任意 | `0 6 * * * ?` 每小时 |
| 9 | `PUT /api/member/today/snapshot` | — | 导入到对应表 | — | — |

- 调用目标：`optionSyncTask.syncResource('decisions')`；`optionSyncTask.syncAllResourcesNow()` 为暂停任务，"执行一次"即忽略时段同步全部 7 个接口。
- 时段外返回 SKIPPED，不写流水；请求间隔 800ms；401/403 → 会话置为过期（与 feed 共用 `option:login:cookie`）。
- 这些接口无 ETag，体积 25B–13KB，因此按 **内容哈希去重**，不拆条目表。

## 2. 表设计（`sql/ry_option_resource.sql`）

**版本表（7 张，结构一致）**

| 列 | 说明 |
|----|------|
| version_id | 自增主键 |
| biz_key | 业务键：premarket 为美东日期 `yyyy-MM-dd`，其余为 `-` |
| content_hash | 原始 JSON 的 SHA-256；`uk_biz_hash(biz_key, content_hash)` 唯一 |
| payload | 原始 JSON（longtext，BigDecimal 精确，不做任何改写） |
| 摘要列 | 从 payload 按 JSON Pointer 抽取，便于 SQL 查询（见下） |
| first_sync_time / last_sync_time / hit_count | 首次出现、最近命中、命中次数 |

摘要列（`OptionResource` 枚举中定义，`#size` 表示数组长度）：

- seller_guide：completed_version、current_version、pending、completed_at
- entitlement：tier、plan_code、subscription_status、valid_until
- decision / management_choice：latest_count、event_count
- premarket：latest、received_at、access_level
- update：seen_seq、current_seq、unseen
- support：mode、unread、message_count

**流水表 `option_resource_sync`**：resource_key、biz_key、http_status、result（UPDATED/UNCHANGED/FAILED）、version_id、raw_bytes、cost_ms、message、trigger_type（JOB/MANUAL/IMPORT）、sync_time。

内容不变时只更新 `last_sync_time`、`hit_count`，不新增版本；历史版本永久保留。

## 3. Redis

| 键 | 内容 |
|----|------|
| `option:res:{key}` | 当前版本 JSON |
| `option:res:premarket:{yyyy-MM-dd}` | 指定日期盘前简报，TTL 7 天 |
| `option:res:sync:status` | Hash，各接口最近一次同步结果 |

Redis 未命中时从版本表取最新版本回填。

## 4. 对外接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/option/today/{resource}?d=` | 读取当前版本（Redis → 表），支持 ETag/304 |
| GET | `/api/member/{resource}` | 兼容旧前端路径，同上 |
| POST | `/option/sync/{resource}?d=` | 立即同步单个接口（忽略时段） |
| POST | `/option/sync/resource/all` | 立即同步全部 |
| GET | `/option/sync/resource/list` | 同步流水（分页） |
| GET | `/option/sync/resource/{resource}/versions` | 版本列表（不含 payload） |
| GET | `/option/sync/resource/{resource}/versions/{versionId}` | 版本详情 |
| PUT | `/api/member/today/snapshot` | 导入：`{resourceKey, businessDate?, payload}`；`resourceKey=feed` 走 feed 拆分入库 |
