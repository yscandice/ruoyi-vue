-- ----------------------------------------------------------------------------
-- OSM「结算」菜单（/member/settlement）：已结算 / 结算单 / 结算统计
-- 结算页没有专属接口，数据全部来自 /api/member/feed（复用 feed 的会话、ETag 与限流处理）
-- 定时同步 -> 按页面规则组装 -> 存库（按内容哈希去重的版本表）-> Redis（option:settlement:{kind}）
-- -> 前端只读 /api/member/settlement/{kind}
-- 一个数据接口对应一张表；增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、结算菜单同步流水
create table if not exists option_settlement_sync (
  sync_id             bigint(20)     not null auto_increment    comment '同步ID',
  feed_sync_id        bigint(20)     default null               comment '来源 feed 批次（option_feed_sync.sync_id）',
  feed_result         varchar(16)    default null               comment 'feed 同步结果 UPDATED/NOT_MODIFIED/SKIPPED',
  market_day          varchar(10)    default null               comment '美东交易日（marketEtDay，本周/本月区间基准日）',
  result              varchar(16)    not null                   comment '结果 UPDATED/UNCHANGED/PAUSED/FAILED',
  record_count        int(11)        default null               comment '已结算记录条数',
  midterm_count       int(11)        default null               comment '其中中期已结算条数',
  card_count          int(11)        default null               comment '结算单条数',
  stat_count          int(11)        default null               comment '结算统计条数',
  settled_count       int(11)        default null               comment '已结算笔数（strategyTracking.settledTotal）',
  settled_pnl_usd     decimal(14,2)  default null               comment '结算盈亏（管理执行之后）',
  raw_pnl_usd         decimal(14,2)  default null               comment '管理前对照',
  protection_cost_usd decimal(14,2)  default null               comment '保护成本',
  manage_cost_usd     decimal(14,2)  default null               comment '动态管理成本',
  manage_credit_usd   decimal(14,2)  default null               comment '动态管理收入',
  new_version_count   int(11)        default null               comment '新增版本数',
  changed_kinds       varchar(100)   default null               comment '有变化的类型',
  cost_ms             int(11)        default null               comment '耗时毫秒',
  message             varchar(500)   default null               comment '说明/错误信息',
  trigger_type        varchar(16)    default null               comment '触发方式 JOB/MANUAL',
  sync_time           datetime       not null                   comment '同步时间（东八区）',
  sync_time_utc       datetime       default null               comment '同步时间（UTC）',
  create_by           varchar(64)    default ''                 comment '创建者',
  create_time         datetime       default null               comment '创建时间（东八区）',
  create_time_utc     datetime       default null               comment '创建时间（UTC）',
  primary key (sync_id),
  key idx_option_settlement_sync_time (sync_time),
  key idx_option_settlement_sync_result (result, sync_id)
) engine=innodb comment='OSM 结算菜单同步流水';

-- 2、已结算
create table if not exists option_settlement_record (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（已结算：id / id:recommendationId；结算单：signal_id；统计：strategy）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（已结算：signal 卖方信号 / midterm 中期信号；结算单：event；统计：stat）',
  symbol             varchar(32)    default null               comment '标的',
  signal_id          varchar(128)   default null               comment '信号ID',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  parent_signal_id   varchar(128)   default null               comment '父信号ID（结算单 parent_signal_id）',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  status_code        varchar(64)    default null               comment '状态码（statusCode；结算单 settlement.stage）',
  result_kind        varchar(32)    default null               comment '结果类型（resultKind；结算单 settlement.reason）',
  settled_day        varchar(5)     default null               comment '结算日 MM/dd（resultObservedAt ?? updatedAt ?? emittedAt）',
  issued_day         varchar(5)     default null               comment '发报日 MM/dd（emittedAt）',
  pnl_usd            decimal(14,2)  default null               comment '盈亏美元（已结算：finalNetPnlUsd ?? researchPnlUsd；统计：settledPnlUsd）',
  emitted_time       datetime       default null               comment '发报时间（东八区）',
  emitted_time_utc   datetime       default null               comment '发报时间（UTC）',
  settled_time       datetime       default null               comment '结算时间（东八区；结算单为发出时间 emitted_at）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_settlement_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_settlement_record (item_key, content_hash),
  key idx_option_settlement_record_last (last_sync_id, sort_no),
  key idx_option_settlement_record_symbol (symbol, settled_day)
) engine=innodb row_format=compressed comment='结算菜单-已结算：feed.history 中已了结的信号（resultKind EXPIRY/EARLY_TAKE_PROFIT 或 statusCode SETTLED/EARLY_TAKE_PROFIT/CLOSED）+ 已了结的中期影子卡，附所属动态管理卡与保护记录';

-- 3、结算单
create table if not exists option_settlement_card (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（已结算：id / id:recommendationId；结算单：signal_id；统计：strategy）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（已结算：signal 卖方信号 / midterm 中期信号；结算单：event；统计：stat）',
  symbol             varchar(32)    default null               comment '标的',
  signal_id          varchar(128)   default null               comment '信号ID',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  parent_signal_id   varchar(128)   default null               comment '父信号ID（结算单 parent_signal_id）',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  status_code        varchar(64)    default null               comment '状态码（statusCode；结算单 settlement.stage）',
  result_kind        varchar(32)    default null               comment '结果类型（resultKind；结算单 settlement.reason）',
  settled_day        varchar(5)     default null               comment '结算日 MM/dd（resultObservedAt ?? updatedAt ?? emittedAt）',
  issued_day         varchar(5)     default null               comment '发报日 MM/dd（emittedAt）',
  pnl_usd            decimal(14,2)  default null               comment '盈亏美元（已结算：finalNetPnlUsd ?? researchPnlUsd；统计：settledPnlUsd）',
  emitted_time       datetime       default null               comment '发报时间（东八区）',
  emitted_time_utc   datetime       default null               comment '发报时间（UTC）',
  settled_time       datetime       default null               comment '结算时间（东八区；结算单为发出时间 emitted_at）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_settlement_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_settlement_card (item_key, content_hash),
  key idx_option_settlement_card_last (last_sync_id, sort_no),
  key idx_option_settlement_card_symbol (symbol, settled_day)
) engine=innodb row_format=compressed comment='结算菜单-结算单：feed.settlementCards（到期结算/平仓指令）';

-- 4、结算统计
create table if not exists option_settlement_stat (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（已结算：id / id:recommendationId；结算单：signal_id；统计：strategy）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（已结算：signal 卖方信号 / midterm 中期信号；结算单：event；统计：stat）',
  symbol             varchar(32)    default null               comment '标的',
  signal_id          varchar(128)   default null               comment '信号ID',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  parent_signal_id   varchar(128)   default null               comment '父信号ID（结算单 parent_signal_id）',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  status_code        varchar(64)    default null               comment '状态码（statusCode；结算单 settlement.stage）',
  result_kind        varchar(32)    default null               comment '结果类型（resultKind；结算单 settlement.reason）',
  settled_day        varchar(5)     default null               comment '结算日 MM/dd（resultObservedAt ?? updatedAt ?? emittedAt）',
  issued_day         varchar(5)     default null               comment '发报日 MM/dd（emittedAt）',
  pnl_usd            decimal(14,2)  default null               comment '盈亏美元（已结算：finalNetPnlUsd ?? researchPnlUsd；统计：settledPnlUsd）',
  emitted_time       datetime       default null               comment '发报时间（东八区）',
  emitted_time_utc   datetime       default null               comment '发报时间（UTC）',
  settled_time       datetime       default null               comment '结算时间（东八区；结算单为发出时间 emitted_at）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_settlement_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_settlement_stat (item_key, content_hash),
  key idx_option_settlement_stat_last (last_sync_id, sort_no),
  key idx_option_settlement_stat_symbol (symbol, settled_day)
) engine=innodb row_format=compressed comment='结算菜单-结算统计：strategyTracking 结算盈亏/管理前对照 + 已结算信号的保护成本与动态管理成本';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 每分钟触发：feed 仅在美东交易时段请求 OSM（沿用原规则），feed 批次或交易日变化时重算
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM结算菜单同步', 'DEFAULT', 'optionSyncTask.syncSettlement()', '50 * * * * ?', '3', '1', '0', 'admin', sysdate(), '每分钟触发；交易时段请求 feed，批次或交易日变化时重算 已结算 / 结算单 / 结算统计'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncSettlement()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM结算菜单强制同步', 'DEFAULT', 'optionSyncTask.syncSettlementNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，首次部署或需要时在定时任务页面点击"执行一次"'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncSettlementNow()');
