-- ----------------------------------------------------------------------------
-- OSM 会员接口 2-8 同步存储（seller-guide / entitlement / decisions / management-choices / premarket / updates / support）
-- 设计：每个接口一张版本表，内容哈希去重（相同内容只累计 hit_count / last_sync_time），payload 保存原始 JSON
--       option_resource_sync 记录每次同步流水；Redis option:res:{key} 只存当前版本
-- 可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

create table if not exists option_resource_sync (
  sync_id       bigint(20)    not null auto_increment    comment '同步ID',
  resource_key  varchar(32)   not null                   comment '接口标识',
  biz_key       varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  http_status   int(4)        default null               comment 'HTTP 状态码',
  result        varchar(16)   not null                   comment '结果 UPDATED/UNCHANGED/FAILED（时段外 SKIPPED 不落库）',
  version_id    bigint(20)    default null               comment '命中/新增的版本ID',
  raw_bytes     int(11)       default null               comment '响应字节数（解压后）',
  cost_ms       int(11)       default null               comment '耗时毫秒',
  message       varchar(500)  default null               comment '说明/错误信息',
  trigger_type  varchar(16)   default null               comment '触发方式 JOB/MANUAL/IMPORT',
  sync_time     datetime      not null                   comment '同步时间',
  primary key (sync_id),
  key idx_res_time (resource_key, sync_time)
) engine=innodb comment='OSM 会员接口同步流水';

create table if not exists option_res_seller_guide (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  completed_version varchar(64)   default null               comment '已完成版本',
  current_version varchar(64)   default null               comment '当前版本',
  pending         varchar(64)   default null               comment '是否待完成',
  completed_at    varchar(64)   default null               comment '完成时间',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='卖方指南 /api/member/seller-guide 版本';

create table if not exists option_res_entitlement (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  tier            varchar(64)   default null               comment '会员等级',
  plan_code       varchar(64)   default null               comment '套餐代码',
  subscription_status varchar(64)   default null               comment '订阅状态',
  valid_until     varchar(64)   default null               comment '有效期至',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='会员权益 /api/member/entitlement 版本';

create table if not exists option_res_decision (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  latest_count    varchar(64)   default null               comment 'latest 条数',
  event_count     varchar(64)   default null               comment 'events 条数',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='决策 /api/member/decisions 版本';

create table if not exists option_res_management_choice (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  latest_count    varchar(64)   default null               comment 'latest 条数',
  event_count     varchar(64)   default null               comment 'events 条数',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='管理选项 /api/member/management-choices 版本';

create table if not exists option_res_premarket (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  latest          varchar(64)   default null               comment '是否最新',
  received_at     varchar(64)   default null               comment '接收时间',
  access_level    varchar(64)   default null               comment '访问级别',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='盘前简报 /api/member/premarket?d= 版本';

create table if not exists option_res_update (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  seen_seq        varchar(64)   default null               comment '已读序号',
  current_seq     varchar(64)   default null               comment '当前序号',
  unseen          varchar(64)   default null               comment '是否有未读',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='更新提醒 /api/member/updates 版本';

create table if not exists option_res_support (
  version_id      bigint(20)    not null auto_increment    comment '版本ID',
  biz_key         varchar(32)   not null default '-'       comment '业务键（premarket 为日期，其余为 -）',
  content_hash    char(64)      not null                   comment '内容 SHA-256',
  payload         longtext      not null                   comment '原始 JSON',
  mode            varchar(64)   default null               comment '模式',
  unread          varchar(64)   default null               comment '未读数',
  message_count   varchar(64)   default null               comment '消息条数',
  first_sync_time datetime      not null                   comment '首次同步时间',
  last_sync_time  datetime      not null                   comment '最近命中时间',
  hit_count       int(11)       not null default 1         comment '命中次数',
  primary key (version_id),
  unique key uk_biz_hash (biz_key, content_hash),
  key idx_biz_last (biz_key, last_sync_time)
) engine=innodb row_format=compressed comment='客服 /api/member/support 版本';

-- 定时任务
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM决策同步', 'DEFAULT', 'optionSyncTask.syncResource(''decisions'')', '0 0/5 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 5 分钟，仅美东交易时段'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''decisions'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM管理选项同步', 'DEFAULT', 'optionSyncTask.syncResource(''management-choices'')', '0 0/5 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 5 分钟，仅美东交易时段'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''management-choices'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM盘前简报同步', 'DEFAULT', 'optionSyncTask.syncResource(''premarket'')', '0 0/15 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 15 分钟，仅美东盘前 04:00-09:30'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''premarket'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM会员权益同步', 'DEFAULT', 'optionSyncTask.syncResource(''entitlement'')', '0 2 * * * ?', '3', '1', '0', 'admin', sysdate(), '每小时'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''entitlement'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM更新提醒同步', 'DEFAULT', 'optionSyncTask.syncResource(''updates'')', '0 4 * * * ?', '3', '1', '0', 'admin', sysdate(), '每小时'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''updates'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM客服消息同步', 'DEFAULT', 'optionSyncTask.syncResource(''support'')', '0 6 * * * ?', '3', '1', '0', 'admin', sysdate(), '每小时'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''support'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM卖方指南同步', 'DEFAULT', 'optionSyncTask.syncResource(''seller-guide'')', '0 10 8 * * ?', '3', '1', '0', 'admin', sysdate(), '每天 08:10'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncResource(''seller-guide'')');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM会员接口强制同步', 'DEFAULT', 'optionSyncTask.syncAllResourcesNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，在定时任务页面点击"执行一次"手动同步全部 7 个接口'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncAllResourcesNow()');
