-- ----------------------------------------------------------------------------
-- OSM「战绩」菜单（/member/scorecard）：战绩明细 / 每日结算净额 / 按标的 / 战绩统计 / 账户模拟
-- 战绩页没有专属 JSON 接口，统计与明细来自 /api/member/feed（performance、history），「我跟了的」来自 /api/member/decisions
-- 定时同步 -> 按页面规则组装 -> 存库（按内容哈希去重的版本表）-> Redis（option:record:{kind}）
-- -> 前端只读 /api/member/record/{kind}
-- 一个数据接口对应一张表；增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、战绩菜单同步流水
create table if not exists option_record_sync (
  sync_id             bigint(20)     not null auto_increment    comment '同步ID',
  feed_sync_id        bigint(20)     default null               comment '来源 feed 批次（option_feed_sync.sync_id）',
  feed_result         varchar(16)    default null               comment 'feed 同步结果 UPDATED/NOT_MODIFIED/SKIPPED',
  decision_result     varchar(16)    default null               comment 'decisions 同步结果 UPDATED/UNCHANGED/SKIPPED',
  market_day          varchar(10)    default null               comment '美东交易日（marketEtDay，本月区间基准日）',
  source_hash         char(64)       default null               comment '来源哈希（feed 批次 + 交易日 + decisions 内容）',
  result              varchar(16)    not null                   comment '结果 UPDATED/UNCHANGED/PAUSED/FAILED',
  trade_count         int(11)        default null               comment '战绩明细条数',
  daily_count         int(11)        default null               comment '每日结算净额条数',
  symbol_count        int(11)        default null               comment '按标的条数',
  stat_count          int(11)        default null               comment '战绩统计条数',
  account_count       int(11)        default null               comment '账户模拟条数',
  settled_count       int(11)        default null               comment '已结算笔数（strategyTracking.settledTotal）',
  settled_pnl_usd     decimal(14,2)  default null               comment '结算盈亏（strategyTracking.settledPnlUsd）',
  managed_pnl_usd     decimal(14,2)  default null               comment '管理后累计盈亏（含在途，strategyTracking.managedPnlUsd）',
  raw_pnl_usd         decimal(14,2)  default null               comment '管理前对照（strategyTracking.rawPnlUsd）',
  win_rate            decimal(6,2)   default null               comment '胜率%（strategyTracking.winRate）',
  adopted_count       int(11)        default null               comment '我跟了且已结算的笔数',
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
  key idx_option_record_sync_time (sync_time),
  key idx_option_record_sync_result (result, sync_id)
) engine=innodb comment='OSM 战绩菜单同步流水';

-- 2、战绩明细
create table if not exists option_record_trade (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（明细：id；每日：yyyy-MM-dd；按标的：区间:标的；统计：all/month/week/management/midterm/buyer/accountSim；账户：账户规模）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（明细 signal；每日 day；按标的 symbol；统计 统计项；账户 account）',
  range_scope        varchar(16)    default null               comment '统计区间 all 全部 / month 本月 / week 本周',
  symbol             varchar(32)    default null               comment '标的',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  stat_day           varchar(10)    default null               comment '统计日 yyyy-MM-dd（明细/每日为结算日）',
  result_kind        varchar(32)    default null               comment '结果口径 EXPIRY 到期结算 / EARLY_TAKE_PROFIT 提前止盈',
  pnl_usd            decimal(14,2)  default null               comment '管理后净结果（finalNetPnlUsd 口径）',
  raw_pnl_usd        decimal(14,2)  default null               comment '原价差对照（researchPnlUsd 口径）',
  item_count         int(11)        default null               comment '笔数/组数',
  adopted_lots       int(11)        default null               comment '我跟了的组数（decisions ADOPT_INTENT quantity）',
  settled_time       datetime       default null               comment '结算时间（东八区）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_record_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_record_trade (item_key, content_hash),
  key idx_option_record_trade_last (last_sync_id, sort_no),
  key idx_option_record_trade_symbol (symbol, stat_day)
) engine=innodb row_format=compressed comment='战绩菜单-战绩明细：feed.history 中已结算信号（resultKind EXPIRY/EARLY_TAKE_PROFIT），管理后净结果按 spreadMarks EXPIRY 节点 ?? finalNetPnlUsd，附我跟了的组数';

-- 3、每日结算净额
create table if not exists option_record_daily (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（明细：id；每日：yyyy-MM-dd；按标的：区间:标的；统计：all/month/week/management/midterm/buyer/accountSim；账户：账户规模）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（明细 signal；每日 day；按标的 symbol；统计 统计项；账户 account）',
  range_scope        varchar(16)    default null               comment '统计区间 all 全部 / month 本月 / week 本周',
  symbol             varchar(32)    default null               comment '标的',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  stat_day           varchar(10)    default null               comment '统计日 yyyy-MM-dd（明细/每日为结算日）',
  result_kind        varchar(32)    default null               comment '结果口径 EXPIRY 到期结算 / EARLY_TAKE_PROFIT 提前止盈',
  pnl_usd            decimal(14,2)  default null               comment '管理后净结果（finalNetPnlUsd 口径）',
  raw_pnl_usd        decimal(14,2)  default null               comment '原价差对照（researchPnlUsd 口径）',
  item_count         int(11)        default null               comment '笔数/组数',
  adopted_lots       int(11)        default null               comment '我跟了的组数（decisions ADOPT_INTENT quantity）',
  settled_time       datetime       default null               comment '结算时间（东八区）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_record_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_record_daily (item_key, content_hash),
  key idx_option_record_daily_last (last_sync_id, sort_no),
  key idx_option_record_daily_symbol (symbol, stat_day)
) engine=innodb row_format=compressed comment='战绩菜单-每日结算净额：按结算日归集的管理后净额 + 当日浮动盈亏（spreadMarks DAILY_CLOSE），含全部正式信号与我跟了的两种口径';

-- 4、按标的
create table if not exists option_record_symbol (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（明细：id；每日：yyyy-MM-dd；按标的：区间:标的；统计：all/month/week/management/midterm/buyer/accountSim；账户：账户规模）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（明细 signal；每日 day；按标的 symbol；统计 统计项；账户 account）',
  range_scope        varchar(16)    default null               comment '统计区间 all 全部 / month 本月 / week 本周',
  symbol             varchar(32)    default null               comment '标的',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  stat_day           varchar(10)    default null               comment '统计日 yyyy-MM-dd（明细/每日为结算日）',
  result_kind        varchar(32)    default null               comment '结果口径 EXPIRY 到期结算 / EARLY_TAKE_PROFIT 提前止盈',
  pnl_usd            decimal(14,2)  default null               comment '管理后净结果（finalNetPnlUsd 口径）',
  raw_pnl_usd        decimal(14,2)  default null               comment '原价差对照（researchPnlUsd 口径）',
  item_count         int(11)        default null               comment '笔数/组数',
  adopted_lots       int(11)        default null               comment '我跟了的组数（decisions ADOPT_INTENT quantity）',
  settled_time       datetime       default null               comment '结算时间（东八区）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_record_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_record_symbol (item_key, content_hash),
  key idx_option_record_symbol_last (last_sync_id, sort_no),
  key idx_option_record_symbol_symbol (symbol, stat_day)
) engine=innodb row_format=compressed comment='战绩菜单-按标的：strategyTracking.symbols（全部）/ monthSymbols（本月）：标的管理后净结果、已结算与在途';

-- 5、战绩统计
create table if not exists option_record_stat (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（明细：id；每日：yyyy-MM-dd；按标的：区间:标的；统计：all/month/week/management/midterm/buyer/accountSim；账户：账户规模）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（明细 signal；每日 day；按标的 symbol；统计 统计项；账户 account）',
  range_scope        varchar(16)    default null               comment '统计区间 all 全部 / month 本月 / week 本周',
  symbol             varchar(32)    default null               comment '标的',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  stat_day           varchar(10)    default null               comment '统计日 yyyy-MM-dd（明细/每日为结算日）',
  result_kind        varchar(32)    default null               comment '结果口径 EXPIRY 到期结算 / EARLY_TAKE_PROFIT 提前止盈',
  pnl_usd            decimal(14,2)  default null               comment '管理后净结果（finalNetPnlUsd 口径）',
  raw_pnl_usd        decimal(14,2)  default null               comment '原价差对照（researchPnlUsd 口径）',
  item_count         int(11)        default null               comment '笔数/组数',
  adopted_lots       int(11)        default null               comment '我跟了的组数（decisions ADOPT_INTENT quantity）',
  settled_time       datetime       default null               comment '结算时间（东八区）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_record_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_record_stat (item_key, content_hash),
  key idx_option_record_stat_last (last_sync_id, sort_no),
  key idx_option_record_stat_symbol (symbol, stat_day)
) engine=innodb row_format=compressed comment='战绩菜单-战绩统计：strategyTracking 全部/本月/本周汇总 + 明细统计（胜负分布、单笔幅度、最大单日、结果口径）+ 管理战绩、中期、买方、账户模拟说明';

-- 6、账户模拟
create table if not exists option_record_account (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（明细：id；每日：yyyy-MM-dd；按标的：区间:标的；统计：all/month/week/management/midterm/buyer/accountSim；账户：账户规模）',
  content_hash       char(64)       not null                   comment '内容 SHA-256（不含易变时间戳）',
  record_type        varchar(32)    default null               comment '记录类型（明细 signal；每日 day；按标的 symbol；统计 统计项；账户 account）',
  range_scope        varchar(16)    default null               comment '统计区间 all 全部 / month 本月 / week 本周',
  symbol             varchar(32)    default null               comment '标的',
  recommendation_id  varchar(128)   default null               comment '推荐ID',
  intent             varchar(32)    default null               comment '方向（SELL PUT / SELL CALL）',
  structure          varchar(128)   default null               comment '价差结构',
  expiry_date        varchar(10)    default null               comment '到期日 yyyy-MM-dd',
  stat_day           varchar(10)    default null               comment '统计日 yyyy-MM-dd（明细/每日为结算日）',
  result_kind        varchar(32)    default null               comment '结果口径 EXPIRY 到期结算 / EARLY_TAKE_PROFIT 提前止盈',
  pnl_usd            decimal(14,2)  default null               comment '管理后净结果（finalNetPnlUsd 口径）',
  raw_pnl_usd        decimal(14,2)  default null               comment '原价差对照（researchPnlUsd 口径）',
  item_count         int(11)        default null               comment '笔数/组数',
  adopted_lots       int(11)        default null               comment '我跟了的组数（decisions ADOPT_INTENT quantity）',
  settled_time       datetime       default null               comment '结算时间（东八区）',
  settled_time_utc   datetime       default null               comment '结算时间（UTC）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_record_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_record_account (item_key, content_hash),
  key idx_option_record_account_last (last_sync_id, sort_no),
  key idx_option_record_account_symbol (symbol, stat_day)
) engine=innodb row_format=compressed comment='战绩菜单-账户模拟：strategyTracking.accountSim.accounts：按账户规模回放的收益曲线、回撤、最差周';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 每分钟触发：feed 仅在美东交易时段请求 OSM（沿用原规则），feed 批次、交易日或 decisions 变化时重算
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM战绩菜单同步', 'DEFAULT', 'optionSyncTask.syncRecord()', '55 * * * * ?', '3', '1', '0', 'admin', sysdate(), '每分钟触发；交易时段请求 feed / decisions，来源变化时重算 战绩明细 / 每日结算净额 / 按标的 / 战绩统计 / 账户模拟'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncRecord()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM战绩菜单强制同步', 'DEFAULT', 'optionSyncTask.syncRecordNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，首次部署或需要时在定时任务页面点击"执行一次"'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncRecordNow()');
