-- ----------------------------------------------------------------------------
-- OSM「进行」菜单（/member?view=active）：进行中 / 保护窗口 / 动态管理 / 已到期 / 历史信号
-- 定时同步（复用 /api/member/feed 的会话、ETag 与限流处理）-> 按页面规则分栏 -> 存库（按内容哈希去重的版本表）
-- -> Redis（option:conduct:{scope}）-> 前端只读 /api/member/conduct/{scope}
-- 一个栏目接口对应一张表；增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、进行菜单同步流水
create table if not exists option_conduct_sync (
  sync_id          bigint(20)    not null auto_increment    comment '同步ID',
  feed_sync_id     bigint(20)    default null               comment '来源 feed 批次（option_feed_sync.sync_id）',
  feed_result      varchar(16)   default null               comment 'feed 同步结果 UPDATED/NOT_MODIFIED/SKIPPED',
  market_day       varchar(10)   default null               comment '美东交易日（marketEtDay，分栏基准日）',
  result           varchar(16)   not null                   comment '结果 UPDATED/UNCHANGED/PAUSED/FAILED',
  active_count     int(11)       default null               comment '进行中条数',
  protection_count int(11)       default null               comment '保护窗口条数',
  manage_count     int(11)       default null               comment '动态管理条数',
  expired_count    int(11)       default null               comment '已到期条数',
  history_count    int(11)       default null               comment '历史信号条数',
  new_version_count int(11)      default null               comment '新增版本数',
  changed_scopes   varchar(100)  default null               comment '有变化的栏目',
  cost_ms          int(11)       default null               comment '耗时毫秒',
  message          varchar(500)  default null               comment '说明/错误信息',
  trigger_type     varchar(16)   default null               comment '触发方式 JOB/MANUAL',
  sync_time        datetime      not null                   comment '同步时间（东八区）',
  sync_time_utc    datetime      default null               comment '同步时间（UTC）',
  create_by        varchar(64)   default ''                 comment '创建者',
  create_time      datetime      default null               comment '创建时间（东八区）',
  create_time_utc  datetime      default null               comment '创建时间（UTC）',
  primary key (sync_id),
  key idx_option_conduct_sync_time (sync_time),
  key idx_option_conduct_sync_result (result, sync_id)
) engine=innodb comment='OSM 进行菜单同步流水';

-- 2、进行中
create table if not exists option_conduct_active (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（id / id:recommendationId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  thread_key         varchar(200)  default null               comment '生命周期键（RECOMMENDATION|id 或 标的|方向|到期|结构）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  intent             varchar(32)   default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  item_state         varchar(64)   default null               comment '状态（state / stage / eventKind）',
  status_code        varchar(64)   default null               comment '状态码（statusCode / kind）',
  result_kind        varchar(32)   default null               comment '结果类型（resultKind）',
  emitted_time       datetime      default null               comment '发出/开启时间（东八区）',
  emitted_time_utc   datetime      default null               comment '发出/开启时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（含栏目补充字段）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_conduct_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前栏目内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_conduct_active (item_key, content_hash),
  key idx_option_conduct_active_last (last_sync_id, sort_no),
  key idx_option_conduct_active_symbol (symbol, expiry_date)
) engine=innodb row_format=compressed comment='进行菜单-进行中：按最快到期排序的未到期正式信号（history 未到期 + 今日 signals）';

-- 3、保护窗口
create table if not exists option_conduct_protection (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（id / id:recommendationId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  thread_key         varchar(200)  default null               comment '生命周期键（RECOMMENDATION|id 或 标的|方向|到期|结构）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  intent             varchar(32)   default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  item_state         varchar(64)   default null               comment '状态（state / stage / eventKind）',
  status_code        varchar(64)   default null               comment '状态码（statusCode / kind）',
  result_kind        varchar(32)   default null               comment '结果类型（resultKind）',
  emitted_time       datetime      default null               comment '发出/开启时间（东八区）',
  emitted_time_utc   datetime      default null               comment '发出/开启时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（含栏目补充字段）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_conduct_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前栏目内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_conduct_protection (item_key, content_hash),
  key idx_option_conduct_protection_last (last_sync_id, sort_no),
  key idx_option_conduct_protection_symbol (symbol, expiry_date)
) engine=innodb row_format=compressed comment='进行菜单-保护窗口：当日出现/更新的保护窗口（protections，排除 eod 与已记录保护）';

-- 4、动态管理
create table if not exists option_conduct_manage (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（id / id:recommendationId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  thread_key         varchar(200)  default null               comment '生命周期键（RECOMMENDATION|id 或 标的|方向|到期|结构）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  intent             varchar(32)   default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  item_state         varchar(64)   default null               comment '状态（state / stage / eventKind）',
  status_code        varchar(64)   default null               comment '状态码（statusCode / kind）',
  result_kind        varchar(32)   default null               comment '结果类型（resultKind）',
  emitted_time       datetime      default null               comment '发出/开启时间（东八区）',
  emitted_time_utc   datetime      default null               comment '发出/开启时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（含栏目补充字段）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_conduct_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前栏目内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_conduct_manage (item_key, content_hash),
  key idx_option_conduct_manage_last (last_sync_id, sort_no),
  key idx_option_conduct_manage_symbol (symbol, expiry_date)
) engine=innodb row_format=compressed comment='进行菜单-动态管理：未退役的动态管理卡（managementCards.retired != true）';

-- 5、已到期
create table if not exists option_conduct_expired (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（id / id:recommendationId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  thread_key         varchar(200)  default null               comment '生命周期键（RECOMMENDATION|id 或 标的|方向|到期|结构）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  intent             varchar(32)   default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  item_state         varchar(64)   default null               comment '状态（state / stage / eventKind）',
  status_code        varchar(64)   default null               comment '状态码（statusCode / kind）',
  result_kind        varchar(32)   default null               comment '结果类型（resultKind）',
  emitted_time       datetime      default null               comment '发出/开启时间（东八区）',
  emitted_time_utc   datetime      default null               comment '发出/开启时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（含栏目补充字段）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_conduct_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前栏目内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_conduct_expired (item_key, content_hash),
  key idx_option_conduct_expired_last (last_sync_id, sort_no),
  key idx_option_conduct_expired_symbol (symbol, expiry_date)
) engine=innodb row_format=compressed comment='进行菜单-已到期：自然到期/已结算的正式信号（history）';

-- 6、历史信号
create table if not exists option_conduct_history (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（id / id:recommendationId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  thread_key         varchar(200)  default null               comment '生命周期键（RECOMMENDATION|id 或 标的|方向|到期|结构）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  intent             varchar(32)   default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  item_state         varchar(64)   default null               comment '状态（state / stage / eventKind）',
  status_code        varchar(64)   default null               comment '状态码（statusCode / kind）',
  result_kind        varchar(32)   default null               comment '结果类型（resultKind）',
  emitted_time       datetime      default null               comment '发出/开启时间（东八区）',
  emitted_time_utc   datetime      default null               comment '发出/开启时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（含栏目补充字段）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_conduct_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前栏目内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_conduct_history (item_key, content_hash),
  key idx_option_conduct_history_last (last_sync_id, sort_no),
  key idx_option_conduct_history_symbol (symbol, expiry_date)
) engine=innodb row_format=compressed comment='进行菜单-历史信号：全部历史正式信号，每个生命周期一张卡（history）';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 每分钟触发：feed 仅在美东交易时段请求 OSM（沿用 feed 规则），分栏在 feed 批次或交易日变化时重算
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM进行菜单同步', 'DEFAULT', 'optionSyncTask.syncConduct()', '30 * * * * ?', '3', '1', '0', 'admin', sysdate(), '每分钟触发；交易时段请求 feed，批次或交易日变化时重算 5 个栏目'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncConduct()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM进行菜单强制同步', 'DEFAULT', 'optionSyncTask.syncConductNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，首次部署或需要时在定时任务页面点击"执行一次"'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncConductNow()');
