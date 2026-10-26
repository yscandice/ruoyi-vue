# 期权 OSM feed 拆分存储设计（today 分支）

## 1. 流程

```text
Quartz（每分钟）optionSyncTask.syncFeed()
  └─ 美东交易时段 09:30–16:05（跳过周末、sys_config option.market.holidays）才请求
      └─ OsmClient GET /api/member/feed（Cookie 取自 Redis，带 If-None-Match，gzip）
           ├─ 304 / ETag 未变 → 只更新同步状态
           ├─ 401/403      → 删除 Cookie，状态 PAUSED，等待手动重新登录
           ├─ 429          → 本次失败，下一分钟重试
           └─ 200          → OptionFeedStorage 拆分写入多表（一个事务）
                              → OptionFeedAssembler 从多表拼接（完整 + 分组）
                              → OptionFeedCacheService 写入 Redis，最后切换当前版本
前端 GET /option/today/feed[/{group}] → 只读 Redis；未命中时从多表拼接并回填
```

## 2. 表结构（sql/ry_option_feed.sql，可重复执行，不删数据）

| 表 | 存放内容 | 去重键 |
|---|---|---|
| option_feed_sync | 同步批次：上游 ETag、generated_at、etDay、marketPhase 等；`field_order`（顶层字段顺序）、`scalar_data`（顶层标量）、`block_versions`（对象字段→版本ID）、`manifest`（数组字段→有序版本ID列表） | 每次 200 一行 |
| option_feed_block | 对象字段及未登记字段：todayDesk、narrativeSignal、brain、publicIntel、performance、scan、signalGroups、signalNews、buyerLifecycle、sourceHealth、systemStatus、terminalHealth、access、withdrawnCards、voiceFeed、buyerExpiryCloses、buyerTimedExits… | (block_key, content_hash) |
| option_feed_history | history | (list_type, item_id, content_hash) |
| option_feed_management | managementCards | 同上 |
| option_feed_protection | protections、sentProtections（list_type 区分） | 同上 |
| option_feed_settlement | settlementCards（主键 signal_id） | 同上 |
| option_feed_order_intent | orderIntents（主键 signal_id） | 同上 |
| option_feed_midterm | midtermShadowCards、midtermSettledCards | 同上 |
| option_feed_signal | signals、buyerSignals、buyerAlerts | 同上 |
| option_feed_event | events（event_id 有重复，item_id = h:内容哈希） | 同上 |

条目表公共列：`version_id`、`list_type`、`item_id`、`content_hash`、可查询列 `symbol / signal_id / item_state / item_kind / item_date`、`payload`（完整条目 JSON）、`volatile_data`、`first_sync_id`、`last_sync_id`、`create_time`、`update_time`。

规则：
- **历史永久保留**：内容哈希已存在时只更新 `last_sync_id`，不新增行；某批次的完整数据由该批次的 `manifest` + `block_versions` + `scalar_data` 还原（`GET /option/sync/feed/{syncId}`）。
- **易变字段**：条目顶层的 `*UpdatedAt`、`*AgeSeconds`、`lastUpdatedAtUtc` 不参与哈希，存入 `volatile_data` 原地更新为最新值（回放旧批次时这些时间戳显示最新值）。
- **报价变化计入哈希**：如 `sellerRow.currentPriceValue` 变化会产生新版本，可回放每分钟报价。
- payload 用 longtext（不用 JSON 类型），保证与上游字段顺序、数值写法完全一致；条目表 `row_format=compressed`。
- 新出现的顶层字段自动进入 option_feed_block / scalar_data，分组为 `extra`，不会丢失。

## 3. Redis

| Key | 内容 | 过期 |
|---|---|---|
| option:login:cookie | OSM 会话 Cookie | 上游 Max-Age（14 天） |
| option:feed:current | {syncId, generatedAt, syncTime, etags{分组:ETag}} | 不过期 |
| option:feed:data:{syncId}:full | 完整 feed JSON | 24 小时；新版本发布后旧版本 120 秒 |
| option:feed:data:{syncId}:{group} | 分组 JSON | 同上 |
| option:feed:sync:status | 同步状态 state(OK/PAUSED/ERROR)、message、lastPollAt、lastSuccessAt、lastSyncId | 不过期 |
| option:feed:sync:lock / option:feed:rebuild:lock | 同步互斥 / 回填互斥 | 120 / 30 秒 |

## 4. 分组

| 分组 | 顶层字段 |
|---|---|
| meta | source_id、status、mode、schema_version、generated_at、polledAt、serverReceivedAt、ageSeconds、etDay、marketEtDay、marketPhase、usingPriorTradingDay、historyFingerprint |
| desk | todayDesk、narrativeSignal、brain、publicIntel |
| signal | signals、buyerSignals、buyerAlerts、signalGroups、signalNews、voiceFeed |
| management | managementCards、protections、sentProtections、midtermShadowCards、midtermSettledCards、buyerLifecycle、buyerExpiryCloses、buyerTimedExits、withdrawnCards |
| settlement | settlementCards、orderIntents |
| history | history、events、performance、scan |
| health | sourceHealth、systemStatus、terminalHealth |
| session | access |
| extra | 未登记的新字段 |

## 5. 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /option/session/login | option:session:login | {email,password} 登录 OSM，Cookie 存 Redis |
| DELETE | /option/session/logout | option:session:login | 删除 Cookie |
| GET | /option/session/status | option:session:status | 会话与同步状态（后台「会员管理 → OSM 会话」菜单） |
| GET | /option/today/feed | 登录即可 | 完整 feed，与 OSM 结构一致；ETag/304、X-Feed-Generated-At、X-Feed-Sync-Id |
| GET | /option/today/feed/{group} | 登录即可 | 分组 feed，ETag/304 |
| GET | /option/today/meta | 登录即可 | 当前 syncId、各分组 ETag、分组定义（AjaxResult） |
| POST | /option/sync/feed | option:sync:run | 立即同步一次（忽略交易时段） |
| GET | /option/sync/feed/list | option:sync:query | 同步批次分页 |
| GET | /option/sync/feed/{syncId} | option:sync:query | 历史回放：从多表拼接指定批次 |
| GET | /option/sync/feed/item/list | option:sync:query | 条目版本分页（listType、symbol、signalId…） |
| GET | /api/member/feed | 登录即可 | 旧路径，改为读取同一份 Redis 缓存（期权网站无需改动） |

## 6. 定时任务（sys_job）

| 名称 | 调用目标 | Cron | 默认状态 |
|---|---|---|---|
| OSM今日feed同步 | optionSyncTask.syncFeed() | 0 * * * * ? | 正常 |
| OSM今日feed强制同步 | optionSyncTask.syncFeedNow() | 0 0 0 1 1 ? 2099 | 暂停（用"执行一次"手动触发） |

## 7. 实测（2026-10-18，休市时段）

- 一次完整同步：793 个条目、约 3.7MB，拆分写库 + 拼接 + 写 Redis 约 2–3.5 秒；首次入库约 3.5MB。
- 之后每次同步新增约 18–19 个版本（约 150KB，主要是 midtermShadowCards 的报价），manifest 约 3KB。
- 本地返回与 OSM 原始响应逐字段一致（含嵌套字段顺序与数值写法）；gzip 后传输约 0.4MB。

## 时间列约定（东八区 + UTC）

所有 option_feed_* / option_res* / option_resource_sync 表的时间都存两列：

| 东八区（原列名） | UTC |
|---|---|
| sync_time / create_time / update_time / first_sync_time / last_sync_time | 同名加 `_utc` 后缀 |

- 写入时在 SQL 中用 `utc_timestamp()` 计算 UTC，东八区 = UTC + 8 小时，**不依赖数据库服务器时区和 JDBC serverTimezone**。
- 接口返回同时带两个字段，如 `syncTime` / `syncTimeUtc`（UTC 字段为字面值字符串 `yyyy-MM-dd HH:mm:ss`）。
- 已建表的库执行 `sql/ry_option_time_migration.sql`：补齐 `*_utc` 列并回填历史数据（可重复执行）。
- 上游原始时间字段（generated_at、receivedAt、completedAt 等）保持 OSM 原值，不做转换。
