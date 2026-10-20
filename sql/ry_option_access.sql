-- ----------------------------------------------------------------------------
-- 「设置」功能 + 会员数据权限（体验期 / 盘后可见）：步骤 1 建表与基础数据
-- 见《‘设置’功能—数据权限功能-后端的技术文档.md》第 5 节、13.2 步骤 1
--   表：option_member_access / option_access_policy / option_market_calendar / option_close_snapshot
--       option_close_snapshot_data / option_member_profile / option_member_notification
--       option_member_update_seen / option_member_register_log
--   参数：option.access.* / option.member.* / option.settings.*（sys_config）
--   角色：option_member（会员）、option_staff（内部人员，看实时数据）；部门：会员
-- 策略初始数据（3.6）在步骤 6、交易日历初始数据在步骤 3 补充
-- 增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、会员权限档案 option_member_access（文档 5.1）
create table if not exists option_member_access (
  user_id            bigint(20)     not null                   comment '若依用户ID（sys_user.user_id）',
  email              varchar(128)   not null                   comment '注册邮箱（小写）',
  level              varchar(16)    not null default 'TRIAL'   comment '最近一次计算的身份 STAFF/PAID/TRIAL/DELAYED/DISABLED（仅用于统计）',
  register_time      datetime       not null                   comment '注册时间（东八区）',
  register_time_utc  datetime       default null               comment '注册时间（UTC）',
  trial_end_time     datetime       not null                   comment '体验期结束时间（东八区）',
  trial_end_time_utc datetime       default null               comment '体验期结束时间（UTC）',
  trial_extend_hours int(11)        not null default 0         comment '后台累计延长体验期的小时数',
  paid_until         datetime       default null               comment '付费到期时间（东八区，预留）',
  paid_until_utc     datetime       default null               comment '付费到期时间（UTC，预留）',
  status             char(1)        not null default '0'       comment '状态 0正常 1停用',
  register_ip        varchar(64)    default null               comment '注册IP',
  device_hash        char(64)       default null               comment '设备指纹哈希（可选）',
  last_access_time   datetime       default null               comment '最近访问时间（东八区）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (user_id),
  unique key uk_option_member_access_email (email),
  key idx_option_member_access_trial (trial_end_time),
  key idx_option_member_access_ip (register_ip, register_time)
) engine=innodb comment='会员权限档案（体验期/付费/盘后）';

-- 2、菜单权限策略 option_access_policy（文档 5.2）
create table if not exists option_access_policy (
  policy_id          bigint(20)     not null auto_increment    comment '策略ID',
  resource_code      varchar(32)    not null                   comment '菜单 today/info/conduct/follow/settlement/record/academy/update/settings',
  resource_name      varchar(32)    not null                   comment '菜单名称',
  path_pattern       varchar(500)   not null                   comment '会员接口路径匹配（Ant 风格，多个用英文逗号分隔）',
  redis_prefix       varchar(200)   default null               comment '实时视图 Redis 前缀（多个用英文逗号分隔），冻结用',
  realtime_mode      varchar(16)    not null default 'REALTIME' comment '体验期/付费 视图 REALTIME/DENY',
  delayed_mode       varchar(16)    not null default 'CLOSE_SNAPSHOT' comment '盘后版视图 CLOSE_SNAPSHOT/REALTIME/DENY/SELF',
  order_num          int(4)         default 0                  comment '匹配顺序',
  status             char(1)        not null default '0'       comment '状态 0启用 1停用',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (policy_id),
  unique key uk_option_access_policy_code (resource_code)
) engine=innodb comment='会员菜单数据权限策略';

-- 3、美股交易日历 option_market_calendar（文档 5.3）
create table if not exists option_market_calendar (
  trade_date         date           not null                   comment '日期（美东）',
  trading            char(1)        not null default '1'       comment '是否交易日 1是 0否',
  open_time          time           default '09:30:00'         comment '开盘时间（美东）',
  close_time         time           default '16:00:00'         comment '收盘时间（美东），提前收盘日为 13:00',
  early_close        char(1)        not null default '0'       comment '是否提前收盘 1是 0否',
  holiday_name       varchar(64)    default null               comment '节假日名称',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  remark             varchar(500)   default null               comment '备注',
  primary key (trade_date)
) engine=innodb comment='美股交易日历（只登记节假日与提前收盘日，未登记的工作日按正常交易日）';

-- 4、收盘冻结记录 option_close_snapshot（文档 5.4）
create table if not exists option_close_snapshot (
  snapshot_id        bigint(20)     not null auto_increment    comment '冻结ID',
  trade_date         date           not null                   comment '交易日（美东）',
  cutoff_et          datetime       not null                   comment '收盘冻结点（美东）',
  cutoff_time        datetime       not null                   comment '收盘冻结点（东八区）',
  cutoff_time_utc    datetime       not null                   comment '收盘冻结点（UTC）',
  result             varchar(16)    not null                   comment '冻结结果 SUCCESS/PARTIAL/FAILED',
  status             varchar(16)    not null default 'FROZEN'  comment '放出状态 FROZEN已冻结未放出/RELEASED已放出/EXPIRED已被后续放出替代',
  released_at        datetime       default null               comment '放出时间（东八区，= 下一个交易日收盘冻结点）',
  released_at_utc    datetime       default null               comment '放出时间（UTC）',
  resource_count     int(11)        default null               comment '冻结菜单数',
  key_count          int(11)        default null               comment '冻结 Redis 键数',
  total_bytes        bigint(20)     default null               comment '冻结数据字节数',
  stale_resources    varchar(200)   default null               comment '数据可能过期的菜单（最近同步失败或早于收盘）',
  source_sync        json           default null               comment '各菜单来源同步ID {"conduct":123,...}',
  cost_ms            int(11)        default null               comment '耗时毫秒',
  trigger_type       varchar(16)    default null               comment '触发方式 JOB/MANUAL',
  message            varchar(500)   default null               comment '说明/错误信息',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  primary key (snapshot_id),
  unique key uk_option_close_snapshot_date (trade_date),
  key idx_option_close_snapshot_release (status, released_at)
) engine=innodb comment='盘后视图冻结记录';

-- 5、冻结数据 option_close_snapshot_data（文档 5.5）
create table if not exists option_close_snapshot_data (
  data_id            bigint(20)     not null auto_increment    comment '主键',
  snapshot_id        bigint(20)     not null                   comment '冻结ID',
  resource_code      varchar(32)    not null                   comment '菜单',
  redis_key          varchar(255)   not null                   comment '实时视图键（不含 option:delayed: 前缀转换）',
  value_type         varchar(16)    not null                   comment 'Redis 类型 string/hash',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  payload            longblob       not null                   comment '值（gzip；hash 类型为 field→value 的 JSON）',
  create_time        datetime       default null               comment '创建时间（东八区）',
  primary key (data_id),
  unique key uk_option_close_snapshot_data (snapshot_id, redis_key),
  key idx_option_close_snapshot_data_res (resource_code, snapshot_id)
) engine=innodb comment='盘后视图冻结数据（用于 Redis 丢失后恢复）';

-- 6、会员资料扩展 option_member_profile（文档 5.6）
create table if not exists option_member_profile (
  user_id              bigint(20)    not null                  comment '若依用户ID',
  watch_symbols        json          default null              comment '关注标的 ["AAPL","TSLA"]',
  strategy_preferences json          default null              comment '策略偏好 {"seller":true,"midterm":true,"buyer":false}',
  strong_alerts        char(1)       not null default '0'      comment '强提醒 1开 0关',
  version              int(11)       not null default 0        comment '乐观锁版本',
  create_time          datetime      default null              comment '创建时间（东八区）',
  create_time_utc      datetime      default null              comment '创建时间（UTC）',
  update_time          datetime      default null              comment '更新时间（东八区）',
  update_time_utc      datetime      default null              comment '更新时间（UTC）',
  primary key (user_id)
) engine=innodb comment='会员资料扩展（昵称/头像/邮箱使用 sys_user）';

-- 7、通知偏好 option_member_notification（文档 5.7）
create table if not exists option_member_notification (
  user_id              bigint(20)    not null                  comment '若依用户ID',
  new_signal           char(1)       not null default '1'      comment '新信号',
  protection_window    char(1)       not null default '1'      comment '保护窗口',
  take_profit_window   char(1)       not null default '1'      comment '止盈窗口',
  settlement           char(1)       not null default '1'      comment '结算',
  weekly_scorecard     char(1)       not null default '1'      comment '每周战绩',
  product_update       char(1)       not null default '1'      comment '产品更新',
  silent_reminder      char(1)       not null default '0'      comment '静默提醒',
  management_stop      char(1)       not null default '0'      comment '动态管理止损',
  manage_adopted_only  char(1)       not null default '0'      comment '只提醒已采纳的信号',
  version              int(11)       not null default 0        comment '乐观锁版本',
  create_time          datetime      default null              comment '创建时间（东八区）',
  create_time_utc      datetime      default null              comment '创建时间（UTC）',
  update_time          datetime      default null              comment '更新时间（东八区）',
  update_time_utc      datetime      default null              comment '更新时间（UTC）',
  primary key (user_id)
) engine=innodb comment='会员通知偏好（字段名沿用 OSM notifications.prefs）';

-- 8、阅读进度 option_member_update_seen（文档 5.8）
create table if not exists option_member_update_seen (
  user_id            bigint(20)    not null                    comment '若依用户ID',
  seen_seq           int(11)       not null default 0          comment '已读到的序号',
  seen_at            datetime      default null                comment '最近标记已读时间（东八区）',
  seen_at_utc        datetime      default null                comment '最近标记已读时间（UTC）',
  primary key (user_id)
) engine=innodb comment='会员「更新」阅读进度';

-- 9、注册与风控日志 option_member_register_log（文档 5.9）
create table if not exists option_member_register_log (
  log_id             bigint(20)    not null auto_increment     comment '主键',
  email              varchar(128)  not null                    comment '邮箱（小写）',
  action             varchar(16)   not null                    comment 'SEND_CODE/REGISTER',
  result             varchar(16)   not null                    comment 'SUCCESS/REJECTED/FAILED',
  reason             varchar(100)  default null                comment '拒绝原因（限频/黑名单域名/验证码错误等）',
  ip                 varchar(64)   default null                comment 'IP',
  device_hash        char(64)      default null                comment '设备指纹哈希',
  create_time        datetime      default null                comment '创建时间（东八区）',
  create_time_utc    datetime      default null                comment '创建时间（UTC）',
  primary key (log_id),
  key idx_option_member_register_log_ip (ip, create_time),
  key idx_option_member_register_log_email (email, create_time)
) engine=innodb comment='会员注册与验证码风控日志';

-- 10、参数配置（文档 5.10）
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-体验时长（小时）', 'option.access.trialHours', '72', 'Y', 'admin', sysdate(), '注册后体验期时长，按自然时间计算'
from dual where not exists (select 1 from sys_config where config_key = 'option.access.trialHours');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-盘后滞后交易日数', 'option.access.delayedLagSessions', '1', 'Y', 'admin', sysdate(), '1=收盘后放出前一个交易日；0=收盘后放出当天'
from dual where not exists (select 1 from sys_config where config_key = 'option.access.delayedLagSessions');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-收盘冻结缓冲（分钟）', 'option.access.closeBufferMinutes', '10', 'Y', 'admin', sysdate(), '收盘后多少分钟冻结并放出'
from dual where not exists (select 1 from sys_config where config_key = 'option.access.closeBufferMinutes');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-盘后无冻结数据处理', 'option.access.delayedFallback', 'EMPTY', 'Y', 'admin', sysdate(), '还没有冻结数据时 EMPTY 返回空结构 / REALTIME 返回实时'
from dual where not exists (select 1 from sys_config where config_key = 'option.access.delayedFallback');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-冻结数据保留天数', 'option.access.snapshotKeepDays', '30', 'Y', 'admin', sysdate(), '清理任务删除过期冻结数据'
from dual where not exists (select 1 from sys_config where config_key = 'option.access.snapshotKeepDays');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-数据权限总开关', 'option.access.enabled', 'false', 'Y', 'admin', sysdate(), 'true 开启数据权限；false 全员实时（步骤 11 再打开）'
from dual where not exists (select 1 from sys_config where config_key = 'option.access.enabled');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-开放会员注册', 'option.member.registerEnabled', 'true', 'Y', 'admin', sysdate(), '是否开放会员注册'
from dual where not exists (select 1 from sys_config where config_key = 'option.member.registerEnabled');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-同IP每日注册上限', 'option.member.registerIpDailyLimit', '3', 'Y', 'admin', sysdate(), '同一 IP 每天最多成功注册数'
from dual where not exists (select 1 from sys_config where config_key = 'option.member.registerIpDailyLimit');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-禁止注册邮箱域名', 'option.member.blockedEmailDomains', '', 'Y', 'admin', sysdate(), '英文逗号分隔，如 temp-mail.org'
from dual where not exists (select 1 from sys_config where config_key = 'option.member.blockedEmailDomains');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '期权-通知默认值', 'option.settings.notifyDefaults', '{"new_signal": true, "protection_window": true, "take_profit_window": true, "settlement": true, "weekly_scorecard": true, "product_update": true, "silent_reminder": false, "management_stop": false, "manage_adopted_only": false}', 'Y', 'admin', sysdate(), '会员通知偏好默认值 JSON'
from dual where not exists (select 1 from sys_config where config_key = 'option.settings.notifyDefaults');

-- 11、角色：会员（不分配后台菜单，数据范围仅本人）、内部人员（看实时数据）
insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '网站会员', 'option_member', 10, '5', 1, 1, '0', '0', 'admin', sysdate(), '网站注册会员，按体验期/盘后版控制数据权限'
from dual where not exists (select 1 from sys_role where role_key = 'option_member');
insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '期权内部人员', 'option_staff', 11, '5', 1, 1, '0', '0', 'admin', sysdate(), '内部人员，网站数据始终为实时视图'
from dual where not exists (select 1 from sys_role where role_key = 'option_staff');

-- 12、部门：会员（挂在顶级部门下，注册会员统一归属）
insert into sys_dept (parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time)
select d.dept_id, concat(d.ancestors, ',', d.dept_id), '会员', 99, '0', '0', 'admin', sysdate()
from sys_dept d
where d.parent_id = 0 and d.del_flag = '0'
  and not exists (select 1 from sys_dept where dept_name = '会员' and del_flag = '0')
order by d.dept_id limit 1;
