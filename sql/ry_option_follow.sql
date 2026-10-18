-- ----------------------------------------------------------------------------
-- OSM「跟踪」菜单（/member/tracks，我的跟踪 / MY MARKS）：卖方信号 / 中期信号 / 买方信号 / 动态管理 标记
-- 页面数据来源（见 osmtrade-today-api.md「跟踪」菜单）：/api/member/decisions + /api/member/management-choices + /api/member/feed
-- 定时同步（decisions、management-choices 复用 option_res_decision / option_res_management_choice 原始版本表，
-- feed 复用 option_feed_*）-> 按页面规则合并标记与系统参考估值 -> 存库（按内容哈希去重的版本表）
-- -> Redis（option:follow:{kind}）-> 前端只读 /api/member/follow/{kind}
-- 一个标记接口对应一张表；增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、跟踪菜单同步流水
create table if not exists option_follow_sync (
  sync_id            bigint(20)    not null auto_increment    comment '同步ID',
  feed_sync_id       bigint(20)    default null               comment '来源 feed 批次（option_feed_sync.sync_id）',
  feed_result        varchar(16)   default null               comment 'feed 同步结果 UPDATED/NOT_MODIFIED/SKIPPED',
  decision_result    varchar(16)   default null               comment 'decisions 同步结果 UPDATED/UNCHANGED/SKIPPED',
  choice_result      varchar(16)   default null               comment 'management-choices 同步结果 UPDATED/UNCHANGED/SKIPPED',
  market_day         varchar(10)   default null               comment '美东交易日（marketEtDay）',
  source_hash        char(64)      default null               comment '来源内容哈希（feed 批次 + 交易日 + 两个标记接口内容）',
  result             varchar(16)   not null                   comment '结果 UPDATED/UNCHANGED/PAUSED/FAILED',
  signal_count       int(11)       default null               comment '卖方信号标记条数',
  midterm_count      int(11)       default null               comment '中期信号标记条数',
  buyer_count        int(11)       default null               comment '买方信号标记条数',
  manage_count       int(11)       default null               comment '动态管理标记条数',
  new_version_count  int(11)       default null               comment '新增版本数',
  changed_kinds      varchar(100)  default null               comment '有变化的标记类型',
  cost_ms            int(11)       default null               comment '耗时毫秒',
  message            varchar(500)  default null               comment '说明/错误信息',
  trigger_type       varchar(16)   default null               comment '触发方式 JOB/MANUAL',
  sync_time          datetime      not null                   comment '同步时间（东八区）',
  sync_time_utc      datetime      default null               comment '同步时间（UTC）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  primary key (sync_id),
  key idx_option_follow_sync_time (sync_time),
  key idx_option_follow_sync_result (result, sync_id)
) engine=innodb comment='OSM 跟踪菜单同步流水';

-- 2、卖方信号标记
create table if not exists option_follow_signal (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（eventId / cardId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  card_kind          varchar(32)   default null               comment '卡片类型（seller_signal / midterm_signal / buyer_signal / dynamic_management）',
  card_id            varchar(128)  default null               comment '卡片ID（management-choices.cardId）',
  event_id           varchar(128)  default null               comment '标记事件ID（eventId）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  decision_type      varchar(32)   default null               comment '标记类型 ADOPT_INTENT 跟了 / OBSERVE 观察 / SKIP 否决',
  raw_choice         varchar(32)   default null               comment '原始选择（decisionType / choice）',
  adopt_mode         varchar(16)   default null               comment '采用方式 AUTO 自动驾驶 / MANUAL 手动',
  quantity           int(11)       default null               comment '组数',
  skip_reason        varchar(64)   default null               comment '否决原因',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  category           varchar(16)   default null               comment '系统参考分类 settled/profit/protect/new/unknown',
  item_state         varchar(64)   default null               comment '信号状态（state）',
  floating_pnl       varchar(32)   default null               comment '系统参考浮动盈亏（floatingPnl）',
  settled_day        varchar(5)    default null               comment '结算日 MM/dd',
  marked_time        datetime      default null               comment '标记时间（东八区）',
  marked_time_utc    datetime      default null               comment '标记时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（标记 + 系统参考估值 + 来源卡片）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_follow_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_follow_signal (item_key, content_hash),
  key idx_option_follow_signal_last (last_sync_id, sort_no),
  key idx_option_follow_signal_symbol (symbol, decision_type)
) engine=innodb row_format=compressed comment='跟踪菜单-卖方信号标记：decisions.events（每个信号取最新一条，排除 autopilot_none）关联 feed signals/history 的系统参考估值';

-- 3、中期信号标记
create table if not exists option_follow_midterm (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（eventId / cardId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  card_kind          varchar(32)   default null               comment '卡片类型（seller_signal / midterm_signal / buyer_signal / dynamic_management）',
  card_id            varchar(128)  default null               comment '卡片ID（management-choices.cardId）',
  event_id           varchar(128)  default null               comment '标记事件ID（eventId）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  decision_type      varchar(32)   default null               comment '标记类型 ADOPT_INTENT 跟了 / OBSERVE 观察 / SKIP 否决',
  raw_choice         varchar(32)   default null               comment '原始选择（decisionType / choice）',
  adopt_mode         varchar(16)   default null               comment '采用方式 AUTO 自动驾驶 / MANUAL 手动',
  quantity           int(11)       default null               comment '组数',
  skip_reason        varchar(64)   default null               comment '否决原因',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  category           varchar(16)   default null               comment '系统参考分类 settled/profit/protect/new/unknown',
  item_state         varchar(64)   default null               comment '信号状态（state）',
  floating_pnl       varchar(32)   default null               comment '系统参考浮动盈亏（floatingPnl）',
  settled_day        varchar(5)    default null               comment '结算日 MM/dd',
  marked_time        datetime      default null               comment '标记时间（东八区）',
  marked_time_utc    datetime      default null               comment '标记时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（标记 + 系统参考估值 + 来源卡片）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_follow_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_follow_midterm (item_key, content_hash),
  key idx_option_follow_midterm_last (last_sync_id, sort_no),
  key idx_option_follow_midterm_symbol (symbol, decision_type)
) engine=innodb row_format=compressed comment='跟踪菜单-中期信号标记：management-choices.events 中 cardKind=midterm_signal（每卡取最新，排除机器否决）关联 feed midtermShadowCards';

-- 4、买方信号标记
create table if not exists option_follow_buyer (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（eventId / cardId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  card_kind          varchar(32)   default null               comment '卡片类型（seller_signal / midterm_signal / buyer_signal / dynamic_management）',
  card_id            varchar(128)  default null               comment '卡片ID（management-choices.cardId）',
  event_id           varchar(128)  default null               comment '标记事件ID（eventId）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  decision_type      varchar(32)   default null               comment '标记类型 ADOPT_INTENT 跟了 / OBSERVE 观察 / SKIP 否决',
  raw_choice         varchar(32)   default null               comment '原始选择（decisionType / choice）',
  adopt_mode         varchar(16)   default null               comment '采用方式 AUTO 自动驾驶 / MANUAL 手动',
  quantity           int(11)       default null               comment '组数',
  skip_reason        varchar(64)   default null               comment '否决原因',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  category           varchar(16)   default null               comment '系统参考分类 settled/profit/protect/new/unknown',
  item_state         varchar(64)   default null               comment '信号状态（state）',
  floating_pnl       varchar(32)   default null               comment '系统参考浮动盈亏（floatingPnl）',
  settled_day        varchar(5)    default null               comment '结算日 MM/dd',
  marked_time        datetime      default null               comment '标记时间（东八区）',
  marked_time_utc    datetime      default null               comment '标记时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（标记 + 系统参考估值 + 来源卡片）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_follow_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_follow_buyer (item_key, content_hash),
  key idx_option_follow_buyer_last (last_sync_id, sort_no),
  key idx_option_follow_buyer_symbol (symbol, decision_type)
) engine=innodb row_format=compressed comment='跟踪菜单-买方信号标记：management-choices.events 中 cardKind=buyer_signal（每卡取最新）关联 feed buyerSignals';

-- 5、动态管理标记
create table if not exists option_follow_manage (
  version_id         bigint(20)    not null auto_increment    comment '版本ID',
  item_key           varchar(160)  not null                   comment '条目键（eventId / cardId）',
  content_hash       char(64)      not null                   comment '内容 SHA-256（不含易变时间戳）',
  card_kind          varchar(32)   default null               comment '卡片类型（seller_signal / midterm_signal / buyer_signal / dynamic_management）',
  card_id            varchar(128)  default null               comment '卡片ID（management-choices.cardId）',
  event_id           varchar(128)  default null               comment '标记事件ID（eventId）',
  symbol             varchar(32)   default null               comment '标的',
  signal_id          varchar(128)  default null               comment '信号ID',
  recommendation_id  varchar(128)  default null               comment '推荐ID',
  decision_type      varchar(32)   default null               comment '标记类型 ADOPT_INTENT 跟了 / OBSERVE 观察 / SKIP 否决',
  raw_choice         varchar(32)   default null               comment '原始选择（decisionType / choice）',
  adopt_mode         varchar(16)   default null               comment '采用方式 AUTO 自动驾驶 / MANUAL 手动',
  quantity           int(11)       default null               comment '组数',
  skip_reason        varchar(64)   default null               comment '否决原因',
  structure          varchar(128)  default null               comment '价差结构',
  expiry_date        varchar(10)   default null               comment '到期日 yyyy-MM-dd',
  category           varchar(16)   default null               comment '系统参考分类 settled/profit/protect/new/unknown',
  item_state         varchar(64)   default null               comment '信号状态（state）',
  floating_pnl       varchar(32)   default null               comment '系统参考浮动盈亏（floatingPnl）',
  settled_day        varchar(5)    default null               comment '结算日 MM/dd',
  marked_time        datetime      default null               comment '标记时间（东八区）',
  marked_time_utc    datetime      default null               comment '标记时间（UTC）',
  sort_no            int(11)       default null               comment '最近一次同步中的排序号',
  payload            longtext      not null                   comment '条目内容 JSON（标记 + 系统参考估值 + 来源卡片）',
  first_sync_id      bigint(20)    not null                   comment '首次出现批次（option_follow_sync.sync_id）',
  last_sync_id       bigint(20)    not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)   default ''                 comment '创建者',
  create_time        datetime      default null               comment '创建时间（东八区）',
  create_time_utc    datetime      default null               comment '创建时间（UTC）',
  update_by          varchar(64)   default ''                 comment '更新者',
  update_time        datetime      default null               comment '更新时间（东八区）',
  update_time_utc    datetime      default null               comment '更新时间（UTC）',
  remark             varchar(500)  default null               comment '备注',
  primary key (version_id),
  unique key uk_option_follow_manage (item_key, content_hash),
  key idx_option_follow_manage_last (last_sync_id, sort_no),
  key idx_option_follow_manage_symbol (symbol, decision_type)
) engine=innodb row_format=compressed comment='跟踪菜单-动态管理标记：management-choices.events 中 cardKind=dynamic_management（每卡取最新，排除可覆盖的否决）关联 feed managementCards';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 每分钟触发：feed、decisions、management-choices 仅在美东交易时段请求 OSM（沿用原规则），来源内容变化时重算标记
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM跟踪菜单同步', 'DEFAULT', 'optionSyncTask.syncFollow()', '45 * * * * ?', '3', '1', '0', 'admin', sysdate(), '每分钟触发；交易时段请求 feed / decisions / management-choices，来源变化时重算 4 类标记'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncFollow()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM跟踪菜单强制同步', 'DEFAULT', 'optionSyncTask.syncFollowNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，首次部署或需要时在定时任务页面点击"执行一次"'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncFollowNow()');
