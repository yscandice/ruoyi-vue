-- ----------------------------------------------------------------------------
-- OSM「更新」菜单（/member/updates「功能更新」）：页头 / 更新条目
-- 页面调用的会员 API 中只有 /api/member/updates（阅读进度 seenSeq/currentSeq/seenAt/unseen，已由 option_res_update 同步，
-- 前端读 GET /api/member/updates）；更新文章列表随页面路由服务端渲染下发，没有独立 JSON 接口。
-- 定时抓取页面 HTML -> 解析为结构化数据 -> 存库（按内容哈希去重的版本表）
-- -> Redis（option:update:{kind} 列表、option:update:item:{kind} 单条、option:update:summary 汇总）
-- -> 前端只读 /api/member/updates/summary、/api/member/updates/{kind}、/api/member/updates/{kind}/{key}
-- 一个数据接口对应一张表；增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、更新菜单同步流水
create table if not exists option_update_sync (
  sync_id             bigint(20)     not null auto_increment    comment '同步ID',
  source_hash         char(64)       default null               comment '来源哈希（全部解析结果）',
  result              varchar(16)    not null                   comment '结果 UPDATED/UNCHANGED/PAUSED/FAILED',
  page_count          int(11)        default null               comment '抓取页面数',
  entry_count         int(11)        default null               comment '更新条目数',
  latest_date         varchar(10)    default null               comment '最新一条的上线日期 yyyy-MM-dd',
  current_seq         int(11)        default null               comment '同步时 /api/member/updates 的 currentSeq',
  transfer_bytes      int(11)        default null               comment '传输字节数（压缩后）',
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
  key idx_option_update_sync_time (sync_time),
  key idx_option_update_sync_result (result, sync_id)
) engine=innodb comment='OSM 更新菜单同步流水';

-- 2、页头
create table if not exists option_update_page (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（固定 updates）',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  page_path          varchar(255)   default null               comment '页面路径（含锚点）',
  title              varchar(200)   default null               comment '页面标题（功能更新）',
  sub_title          varchar(500)   default null               comment '页面说明',
  publish_date       varchar(10)    default null               comment '最新一条的上线日期 yyyy-MM-dd',
  target_path        varchar(255)   default null               comment '设置入口链接（关闭功能更新推送）',
  visual_type        varchar(16)    default null               comment '配图类型 video/svg/image/none',
  media_url          varchar(500)   default null               comment '吉祥物图片地址',
  item_count         int(11)        default null               comment '更新条目数',
  text_length        int(11)        default null               comment '正文字数（不含空白）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号（0 为最新）',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_update_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_update_page (item_key, content_hash),
  key idx_option_update_page_last (last_sync_id, sort_no),
  key idx_option_update_page_date (publish_date)
) engine=innodb row_format=compressed comment='更新菜单-页头：/member/updates 栏目名、标题、说明（含设置入口）、吉祥物、页脚，及条目数 / 日期范围 / 按月与目标链接分布';

-- 3、更新条目
create table if not exists option_update_entry (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（锚点去掉 u- 前缀，如 2026-10-19-roll-safer）',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  page_path          varchar(255)   default null               comment '页面路径（含锚点）',
  title              varchar(200)   default null               comment '标题',
  sub_title          varchar(500)   default null               comment '做了什么（前 500 字）',
  publish_date       varchar(10)    default null               comment '上线日期 yyyy-MM-dd',
  target_path        varchar(255)   default null               comment '「去用一下」目标链接',
  visual_type        varchar(16)    default null               comment '配图类型 video/svg/image/none',
  media_url          varchar(500)   default null               comment '视频地址（视频配图）',
  item_count         int(11)        default null               comment '子项数（固定 1）',
  text_length        int(11)        default null               comment '正文字数（不含空白）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号（0 为最新）',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_update_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_update_entry (item_key, content_hash),
  key idx_option_update_entry_last (last_sync_id, sort_no),
  key idx_option_update_entry_date (publish_date)
) engine=innodb row_format=compressed comment='更新菜单-更新条目：/member/updates 每条上线记录（日期、标题、做了什么、怎么用、去用一下目标链接、视频或示意图）';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 功能更新按天上线，每 30 分钟抓取一次（1 个页面），内容变化时写库
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM更新菜单同步', 'DEFAULT', 'optionSyncTask.syncUpdate()', '0 20/30 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 30 分钟抓取功能更新页面（页头 / 更新条目），内容变化时写库并刷新 Redis'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncUpdate()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM更新菜单强制同步', 'DEFAULT', 'optionSyncTask.syncUpdateNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，首次部署或需要时在定时任务页面点击"执行一次"'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncUpdateNow()');
