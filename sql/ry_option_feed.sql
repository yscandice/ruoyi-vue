-- ----------------------------------------------------------------------------
-- 期权 OSM feed 拆分存储（today 分支）
-- 同步时拆开写入多张表 -> 从多表拼接 -> 写入 Redis -> 前端只读 Redis
-- 历史永久保留：内容未变化时只更新 last_sync_id，不新增行
-- 脚本可重复执行：只建不存在的表，不删除任何数据
-- 适用：MySQL 5.7+/8.0、MariaDB 10.5+（payload 使用 longtext 保留上游字段顺序）
-- ----------------------------------------------------------------------------

-- 1、同步批次
create table if not exists option_feed_sync (
  sync_id            bigint(20)    not null auto_increment    comment '批次ID',
  sync_time          datetime      not null                   comment '同步时间',
  upstream_etag      varchar(128)  default null               comment '上游 ETag',
  generated_at       varchar(40)   default null               comment '上游 generated_at',
  et_day             varchar(10)   default null               comment '上游 etDay',
  market_et_day      varchar(10)   default null               comment '上游 marketEtDay',
  market_phase       varchar(32)   default null               comment '上游 marketPhase',
  mode               varchar(32)   default null               comment '上游 mode',
  schema_version     int(11)       default null               comment '上游 schema_version',
  source_status      varchar(32)   default null               comment '上游 status',
  field_order        text          not null                   comment '顶层字段顺序 JSON 数组',
  scalar_data        text          not null                   comment '顶层标量字段 JSON 对象',
  block_versions     text          not null                   comment '对象字段版本 {字段:版本ID}',
  manifest           mediumtext    not null                   comment '数组清单 {字段:[版本ID...]}，保留顺序',
  changed_groups     varchar(255)  default null               comment '本次有变化的分组',
  item_count         int(11)       default 0                  comment '数组条目总数',
  new_version_count  int(11)       default 0                  comment '本次新增版本数',
  raw_bytes          int(11)       default 0                  comment '上游传输字节数',
  cost_ms            int(11)       default 0                  comment '拆分写库耗时（毫秒）',
  status             char(1)       default '0'                comment '状态（0成功 1失败）',
  create_time        datetime      default null               comment '创建时间',
  primary key (sync_id),
  key idx_option_feed_sync_day (et_day, sync_time)
) engine=innodb auto_increment=1 comment = '期权feed同步批次';

-- 2、对象字段版本（todayDesk、narrativeSignal、performance、scan、signalGroups、access、withdrawnCards 等）
create table if not exists option_feed_block (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  block_key          varchar(64)   not null                   comment '顶层字段名',
  content_hash       char(64)      not null                   comment '内容 SHA-256',
  payload            longtext      not null                   comment '字段内容 JSON',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  primary key (version_id),
  unique key uk_option_feed_block (block_key, content_hash),
  key idx_option_feed_block_hash (content_hash)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed对象字段版本';

-- 3、feed历史交易条目版本（history）
create table if not exists option_feed_history (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_history (list_type, item_id, content_hash),
  key idx_option_feed_history_hash (list_type, content_hash),
  key idx_option_feed_history_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed历史交易条目版本（history）';

-- 4、feed管理卡片条目版本（managementCards）
create table if not exists option_feed_management (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_management (list_type, item_id, content_hash),
  key idx_option_feed_management_hash (list_type, content_hash),
  key idx_option_feed_management_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed管理卡片条目版本（managementCards）';

-- 5、feed保护条目版本（protections / sentProtections）
create table if not exists option_feed_protection (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_protection (list_type, item_id, content_hash),
  key idx_option_feed_protection_hash (list_type, content_hash),
  key idx_option_feed_protection_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed保护条目版本（protections / sentProtections）';

-- 6、feed结算卡片条目版本（settlementCards）
create table if not exists option_feed_settlement (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_settlement (list_type, item_id, content_hash),
  key idx_option_feed_settlement_hash (list_type, content_hash),
  key idx_option_feed_settlement_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed结算卡片条目版本（settlementCards）';

-- 7、feed下单意向条目版本（orderIntents）
create table if not exists option_feed_order_intent (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_order_intent (list_type, item_id, content_hash),
  key idx_option_feed_order_intent_hash (list_type, content_hash),
  key idx_option_feed_order_intent_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed下单意向条目版本（orderIntents）';

-- 8、feed中期卡片条目版本（midtermShadowCards / midtermSettledCards）
create table if not exists option_feed_midterm (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_midterm (list_type, item_id, content_hash),
  key idx_option_feed_midterm_hash (list_type, content_hash),
  key idx_option_feed_midterm_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed中期卡片条目版本（midtermShadowCards / midtermSettledCards）';

-- 9、feed信号条目版本（signals / buyerSignals / buyerAlerts）
create table if not exists option_feed_signal (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_signal (list_type, item_id, content_hash),
  key idx_option_feed_signal_hash (list_type, content_hash),
  key idx_option_feed_signal_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed信号条目版本（signals / buyerSignals / buyerAlerts）';

-- 10、feed事件条目版本（events，按内容哈希去重）
create table if not exists option_feed_event (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  list_type          varchar(32)   not null                   comment '所属数组字段',
  item_id            varchar(128)  not null                   comment '条目主键（id / signal_id，无主键时为 h:内容哈希）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  item_state         varchar(64)   default null               comment '状态（state / status）',
  item_kind          varchar(64)   default null               comment '类型（kind / category / tier / event_type）',
  item_date          varchar(32)   default null               comment '业务日期（etDate / openedEtDay / expiryDate / occurred_at / emitted_at）',
  payload            longtext      not null                   comment '条目内容 JSON（首次出现时的完整内容）',
  volatile_data      text          default null               comment '易变时间戳 JSON（原地更新为最新值）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次',
  create_time        datetime      default null               comment '创建时间',
  update_time        datetime      default null               comment '更新时间',
  primary key (version_id),
  unique key uk_option_feed_event (list_type, item_id, content_hash),
  key idx_option_feed_event_hash (list_type, content_hash),
  key idx_option_feed_event_symbol (symbol, item_date)
) engine=innodb row_format=compressed auto_increment=1 comment = '期权feed事件条目版本（events，按内容哈希去重）';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 每分钟触发，任务内部按美东交易时段（09:30-16:05，跳过周末与节假日）决定是否请求 OSM
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM今日feed同步', 'DEFAULT', 'optionSyncTask.syncFeed()', '0 * * * * ?', '3', '1', '0', 'admin', sysdate(), '每分钟触发，仅美东交易时段实际请求'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncFeed()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM今日feed强制同步', 'DEFAULT', 'optionSyncTask.syncFeedNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，在定时任务页面点击"执行一次"手动同步'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncFeedNow()');

-- 美股休市日（逗号分隔 yyyy-MM-dd，美东日期，按年维护）
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-美股休市日', 'option.market.holidays', '2026-01-01,2026-01-19,2026-02-16,2026-04-03,2026-05-25,2026-06-19,2026-07-03,2026-09-07,2026-11-26,2026-12-25', 'Y', 'admin', sysdate(), 'feed 同步跳过这些日期'
from dual where not exists (select 1 from sys_config where config_key = 'option.market.holidays');
