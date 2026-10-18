-- ----------------------------------------------------------------------------
-- OSM「信息」菜单：盘前简报 / 财报解读 / 札记 / 信息流 / 推荐信息
-- 定时同步 -> 存库（按内容哈希去重的版本表）-> Redis -> 前端只读
-- 增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

create table if not exists option_info_sync (
  sync_id       bigint(20)    not null auto_increment    comment '同步ID',
  info_key      varchar(32)   not null                   comment '接口标识 info-feed/referrals/premarket/earnings/notes',
  biz_key       varchar(64)   not null default '-'       comment '业务键（日期/财报ID/札记ID，- 为整份或索引）',
  http_status   int(4)        default null               comment 'HTTP 状态码',
  result        varchar(16)   not null                   comment '结果 UPDATED/UNCHANGED/FAILED',
  version_id    bigint(20)    default null               comment '命中/新增的版本ID',
  raw_bytes     int(11)       default null               comment '响应字节数',
  cost_ms       int(11)       default null               comment '耗时毫秒',
  message       varchar(500)  default null               comment '说明/错误信息',
  trigger_type  varchar(16)   default null               comment '触发方式 JOB/MANUAL',
  sync_time     datetime      not null                   comment '同步时间（东八区）',
  sync_time_utc datetime      default null               comment '同步时间（UTC）',
  create_by     varchar(64)   default ''                 comment '创建者',
  create_time   datetime      default null               comment '创建时间',
  primary key (sync_id),
  key idx_info_time (info_key, sync_time)
) engine=innodb comment='OSM 信息菜单同步流水';

create table if not exists option_info_feed (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(64)   not null default '-'       comment '业务键（-）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  item_count      varchar(16)   default null               comment '条目数',
  unread          varchar(16)   default null               comment '未读数',
  latest_key      varchar(64)   default null               comment '最新条目键',
  latest_title    varchar(200)  default null               comment '最新条目标题',
  latest_day      varchar(16)   default null               comment '最新条目日期',
  first_sync_time datetime      not null                   comment '首次同步时间（东八区）',
  first_sync_time_utc datetime  default null               comment '首次同步时间（UTC）',
  last_sync_time  datetime      not null                   comment '最近命中时间（东八区）',
  last_sync_time_utc datetime   default null               comment '最近命中时间（UTC）',
  hit_count       int(11)       not null default 1         comment '命中次数',
  create_by       varchar(64)   default ''                 comment '创建者',
  create_time     datetime      default null               comment '创建时间（东八区）',
  create_time_utc datetime      default null               comment '创建时间（UTC）',
  update_by       varchar(64)   default ''                 comment '更新者',
  update_time     datetime      default null               comment '更新时间（东八区）',
  update_time_utc datetime      default null               comment '更新时间（UTC）',
  remark          varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='信息流 /api/member/info-feed 版本';

create table if not exists option_info_referral (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(64)   not null default '-'       comment '业务键（-）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  reward_mode     varchar(32)   default null               comment '奖励模式',
  registered_count varchar(16)   default null               comment '已注册人数',
  paid_count      varchar(16)   default null               comment '已付费人数',
  invited_count   varchar(16)   default null               comment '邀请记录数',
  coupon_count    varchar(16)   default null               comment '优惠券数',
  first_sync_time datetime      not null                   comment '首次同步时间（东八区）',
  first_sync_time_utc datetime  default null               comment '首次同步时间（UTC）',
  last_sync_time  datetime      not null                   comment '最近命中时间（东八区）',
  last_sync_time_utc datetime   default null               comment '最近命中时间（UTC）',
  hit_count       int(11)       not null default 1         comment '命中次数',
  create_by       varchar(64)   default ''                 comment '创建者',
  create_time     datetime      default null               comment '创建时间（东八区）',
  create_time_utc datetime      default null               comment '创建时间（UTC）',
  update_by       varchar(64)   default ''                 comment '更新者',
  update_time     datetime      default null               comment '更新时间（东八区）',
  update_time_utc datetime      default null               comment '更新时间（UTC）',
  remark          varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='推荐信息 /api/member/referrals 版本';

create table if not exists option_info_premarket (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(64)   not null default '-'       comment '业务键（交易日 yyyy-MM-dd，- 为日期索引）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  trade_date      varchar(16)   default null               comment '交易日',
  date_label      varchar(32)   default null               comment '日期标签',
  headline        varchar(500)  default null               comment '标题',
  access_level    varchar(16)   default null               comment '访问级别',
  block_count     varchar(16)   default null               comment '板块数',
  story_count     varchar(16)   default null               comment '要闻数',
  first_sync_time datetime      not null                   comment '首次同步时间（东八区）',
  first_sync_time_utc datetime  default null               comment '首次同步时间（UTC）',
  last_sync_time  datetime      not null                   comment '最近命中时间（东八区）',
  last_sync_time_utc datetime   default null               comment '最近命中时间（UTC）',
  hit_count       int(11)       not null default 1         comment '命中次数',
  create_by       varchar(64)   default ''                 comment '创建者',
  create_time     datetime      default null               comment '创建时间（东八区）',
  create_time_utc datetime      default null               comment '创建时间（UTC）',
  update_by       varchar(64)   default ''                 comment '更新者',
  update_time     datetime      default null               comment '更新时间（东八区）',
  update_time_utc datetime      default null               comment '更新时间（UTC）',
  remark          varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='盘前简报 /member/premarket 版本';

create table if not exists option_info_earnings (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(64)   not null default '-'       comment '业务键（财报ID，- 为列表索引）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  ticker          varchar(16)   default null               comment '标的代码',
  company         varchar(64)   default null               comment '公司',
  period          varchar(32)   default null               comment '财报期',
  report_date     varchar(16)   default null               comment '发布日期',
  session         varchar(16)   default null               comment '发布时段',
  headline        varchar(500)  default null               comment '标题',
  first_sync_time datetime      not null                   comment '首次同步时间（东八区）',
  first_sync_time_utc datetime  default null               comment '首次同步时间（UTC）',
  last_sync_time  datetime      not null                   comment '最近命中时间（东八区）',
  last_sync_time_utc datetime   default null               comment '最近命中时间（UTC）',
  hit_count       int(11)       not null default 1         comment '命中次数',
  create_by       varchar(64)   default ''                 comment '创建者',
  create_time     datetime      default null               comment '创建时间（东八区）',
  create_time_utc datetime      default null               comment '创建时间（UTC）',
  update_by       varchar(64)   default ''                 comment '更新者',
  update_time     datetime      default null               comment '更新时间（东八区）',
  update_time_utc datetime      default null               comment '更新时间（UTC）',
  remark          varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='财报解读 /member/earnings 版本';

create table if not exists option_info_note (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(64)   not null default '-'       comment '业务键（札记ID，- 为列表索引）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  kind            varchar(32)   default null               comment '类型',
  title           varchar(200)  default null               comment '标题',
  note_date       varchar(16)   default null               comment '日期',
  summary         varchar(500)  default null               comment '摘要',
  image_url       varchar(255)  default null               comment '正文图片',
  cover_url       varchar(255)  default null               comment '封面图片',
  first_sync_time datetime      not null                   comment '首次同步时间（东八区）',
  first_sync_time_utc datetime  default null               comment '首次同步时间（UTC）',
  last_sync_time  datetime      not null                   comment '最近命中时间（东八区）',
  last_sync_time_utc datetime   default null               comment '最近命中时间（UTC）',
  hit_count       int(11)       not null default 1         comment '命中次数',
  create_by       varchar(64)   default ''                 comment '创建者',
  create_time     datetime      default null               comment '创建时间（东八区）',
  create_time_utc datetime      default null               comment '创建时间（UTC）',
  update_by       varchar(64)   default ''                 comment '更新者',
  update_time     datetime      default null               comment '更新时间（东八区）',
  update_time_utc datetime      default null               comment '更新时间（UTC）',
  remark          varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='札记 /member/notes 版本';

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM信息流同步', 'DEFAULT', 'optionSyncTask.syncInfo(''info-feed'')', '0 0/10 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 10 分钟'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncInfo(''info-feed'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM推荐信息同步', 'DEFAULT', 'optionSyncTask.syncInfo(''referrals'')', '0 20 8 * * ?', '3', '1', '0', 'admin', sysdate(), '每天 08:20'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncInfo(''referrals'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM盘前简报页同步', 'DEFAULT', 'optionSyncTask.syncInfo(''premarket'')', '0 0/15 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 15 分钟，含缺失历史补齐'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncInfo(''premarket'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM财报解读同步', 'DEFAULT', 'optionSyncTask.syncInfo(''earnings'')', '0 0/30 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 30 分钟，含缺失历史补齐'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncInfo(''earnings'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM札记同步', 'DEFAULT', 'optionSyncTask.syncInfo(''notes'')', '0 30 8 * * ?', '3', '1', '0', 'admin', sysdate(), '每天 08:30'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncInfo(''notes'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM信息菜单强制同步', 'DEFAULT', 'optionSyncTask.syncAllInfoNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，在定时任务页面点击"执行一次"手动同步信息菜单全部 5 个接口'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncAllInfoNow()');
