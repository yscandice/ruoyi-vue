# OSM「信息」菜单同步设计

方案：定时任务从 OSM 拉取 → 按内容 SHA-256 去重存版本表 → 写 Redis → 前端接口只读 Redis（未命中从表回填）。

## 数据源

| 标识 | 来源 | OSM 地址 | 业务键 | 表 |
|---|---|---|---|---|
| info-feed | JSON 接口 | /api/member/info-feed | - | option_info_feed |
| referrals | JSON 接口 | /api/member/referrals | - | option_info_referral |
| premarket | 页面 RSC（PremarketClient props） | /member/premarket?d= | 交易日；- 为日期索引 | option_info_premarket |
| earnings | 页面 RSC（EarningsClient props） | /member/earnings?id= | 财报ID；- 为列表索引 | option_info_earnings |
| notes | 页面脚本（NotesClient-*.js 内置列表） | /member/notes?id= | 札记ID；- 为列表索引 | option_info_note |

盘前简报、财报解读、札记没有独立 JSON 接口，数据由 `OptionPageParser` 从页面 `__VINEXT_RSC_CHUNKS__` / 前端脚本中解析。OSM 改版页面结构时需同步调整解析器（同步结果会记为 FAILED 并写入流水）。

同一接口同时只允许一个同步在跑（定时与手动重叠时后者返回 SKIPPED）。

页面类同步：默认同步最新一篇 + 索引，并补齐库里缺失的历史篇目（每次最多 20 篇，间隔 800ms）。

## 表结构

- 版本表：`version_id, biz_key, content_hash, payload, 摘要列…, first/last_sync_time(+_utc), hit_count, create_by, create_time(+_utc), update_by, update_time(+_utc), remark`，`unique(biz_key, content_hash)`。
- 流水表：`option_info_sync`。
- 时间：不带后缀为东八区，`_utc` 为 UTC。

## Redis

- `option:info:{info}:{bizKey}`：单篇 TTL 30 天，索引 / 整份不过期。
- `option:info:sync:status`：各接口最近同步状态（Hash）。

## 接口

前端只读（返回与 OSM 同形 JSON，无数据 503）：

- `GET /api/member/info-feed`
- `GET /api/member/referrals`
- `GET /api/member/info/premarket?d=yyyy-MM-dd`（不传为最新）
- `GET /api/member/info/earnings?id=`（不传为最新）
- `GET /api/member/info/notes?id=`（不传为列表）

管理：

- `POST /option/sync/info/{info}?bizKey=`、`POST /option/sync/info-all`（option:sync:run）
- `GET /option/sync/info/list`、`GET /option/sync/info/{info}/versions`、`GET /option/sync/info/{info}/versions/{versionId}`（option:sync:query）

## 定时任务（sql/ry_option_information.sql）

| 任务 | 调用目标 | cron |
|---|---|---|
| OSM信息流同步 | optionSyncTask.syncInfo('info-feed') | 每 10 分钟 |
| OSM推荐信息同步 | optionSyncTask.syncInfo('referrals') | 每天 08:20 |
| OSM盘前简报页同步 | optionSyncTask.syncInfo('premarket') | 每 15 分钟 |
| OSM财报解读同步 | optionSyncTask.syncInfo('earnings') | 每 30 分钟 |
| OSM札记同步 | optionSyncTask.syncInfo('notes') | 每天 08:30 |
| OSM信息菜单强制同步 | optionSyncTask.syncAllInfoNow() | 暂停，手动“执行一次” |

部署：在现有库执行增量脚本 `sql/ry_option_information.sql`（可重复执行），无需重建库。
