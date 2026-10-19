-- ----------------------------------------------------------------------------
-- OSM「学堂」菜单（/academy）：栏目页 / 课程 / 一周问答 / 术语速查
-- 学堂页面只调用公共 API（feed、seller-guide、updates、support，已由 option_res_* 同步），没有课程专属 JSON 接口；
-- 课程正文随页面路由服务端渲染下发，定时抓取页面 HTML -> 解析为结构化数据 -> 存库（按内容哈希去重的版本表）
-- -> Redis（option:academy:{kind} 列表、option:academy:item:{kind} 单条）-> 前端只读 /api/member/academy/{kind}
-- 一个数据接口对应一张表；增量脚本，可重复执行：create table if not exists + insert ... where not exists
-- ----------------------------------------------------------------------------

-- 1、学堂菜单同步流水
create table if not exists option_academy_sync (
  sync_id             bigint(20)     not null auto_increment    comment '同步ID',
  source_hash         char(64)       default null               comment '来源哈希（全部解析结果）',
  result              varchar(16)    not null                   comment '结果 UPDATED/UNCHANGED/PAUSED/FAILED',
  page_count          int(11)        default null               comment '抓取页面数',
  page_item_count     int(11)        default null               comment '栏目页条数',
  lesson_count        int(11)        default null               comment '课程条数',
  qa_count            int(11)        default null               comment '一周问答期数',
  glossary_count      int(11)        default null               comment '术语条数',
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
  key idx_option_academy_sync_time (sync_time),
  key idx_option_academy_sync_result (result, sync_id)
) engine=innodb comment='OSM 学堂菜单同步流水';

-- 2、栏目页
create table if not exists option_academy_page (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（home 学堂首页 / weekly-qa 问答书架 / glossary 术语页）',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  page_path          varchar(255)   default null               comment '页面路径',
  title              varchar(200)   default null               comment '标题',
  sub_title          varchar(500)   default null               comment '副标题/摘要',
  category           varchar(64)    default null               comment '分类：栏目页类型',
  access_level       varchar(16)    default null               comment '可见范围（公开 / 会员 / 最新）',
  item_count         int(11)        default null               comment '子项数（小节 / 问答 / 课程 / 术语）',
  text_length        int(11)        default null               comment '正文字数（不含空白）',
  publish_date       varchar(10)    default null               comment '出刊日期 yyyy-MM-dd（一周问答）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_academy_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_academy_page (item_key, content_hash),
  key idx_option_academy_page_last (last_sync_id, sort_no)
) engine=innodb row_format=compressed comment='学堂菜单-栏目页：/academy 学堂首页（书目、视频卡、札记、课程清单、参考卡、页脚）、/academy/weekly-qa 问答书架、/academy/glossary 术语页头';

-- 3、课程
create table if not exists option_academy_lesson (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（课程 slug，如 workbench / lifecycle / api-follow）',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  page_path          varchar(255)   default null               comment '页面路径',
  title              varchar(200)   default null               comment '标题',
  sub_title          varchar(500)   default null               comment '副标题/摘要',
  category           varchar(64)    default null               comment '分类：课序（第一课…第六课）',
  access_level       varchar(16)    default null               comment '可见范围（公开 / 会员 / 最新）',
  item_count         int(11)        default null               comment '子项数（小节 / 问答 / 课程 / 术语）',
  text_length        int(11)        default null               comment '正文字数（不含空白）',
  publish_date       varchar(10)    default null               comment '出刊日期 yyyy-MM-dd（一周问答）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_academy_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_academy_lesson (item_key, content_hash),
  key idx_option_academy_lesson_last (last_sync_id, sort_no)
) engine=innodb row_format=compressed comment='学堂菜单-课程：/academy/{slug} 六节课正文（课头、先读这四句、教与不教、小节、札记、安全化正文 HTML 与纯文本）';

-- 4、一周问答
create table if not exists option_academy_qa (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（期次，即 /academy/weekly-qa/{n} 的 n）',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  page_path          varchar(255)   default null               comment '页面路径',
  title              varchar(200)   default null               comment '标题',
  sub_title          varchar(500)   default null               comment '副标题/摘要',
  category           varchar(64)    default null               comment '分类：期号（No.01）',
  access_level       varchar(16)    default null               comment '可见范围（公开 / 会员 / 最新）',
  item_count         int(11)        default null               comment '子项数（小节 / 问答 / 课程 / 术语）',
  text_length        int(11)        default null               comment '正文字数（不含空白）',
  publish_date       varchar(10)    default null               comment '出刊日期 yyyy-MM-dd（一周问答）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_academy_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_academy_qa (item_key, content_hash),
  key idx_option_academy_qa_last (last_sync_id, sort_no)
) engine=innodb row_format=compressed comment='学堂菜单-一周问答：/academy/weekly-qa/{n} 各期（章节、问答、群友问、回答 HTML、划重点）';

-- 5、术语速查
create table if not exists option_academy_glossary (
  version_id         bigint(20)     not null auto_increment    comment '版本ID',
  item_key           varchar(160)   not null                   comment '条目键（术语，即 /academy/glossary 锚点）',
  content_hash       char(64)       not null                   comment '内容 SHA-256',
  page_path          varchar(255)   default null               comment '页面路径',
  title              varchar(200)   default null               comment '标题',
  sub_title          varchar(500)   default null               comment '副标题/摘要',
  category           varchar(64)    default null               comment '分类：英文名',
  access_level       varchar(16)    default null               comment '可见范围（公开 / 会员 / 最新）',
  item_count         int(11)        default null               comment '子项数（小节 / 问答 / 课程 / 术语）',
  text_length        int(11)        default null               comment '正文字数（不含空白）',
  publish_date       varchar(10)    default null               comment '出刊日期 yyyy-MM-dd（一周问答）',
  sort_no            int(11)        default null               comment '最近一次同步中的排序号',
  payload            longtext       not null                   comment '条目内容 JSON',
  first_sync_id      bigint(20)     not null                   comment '首次出现批次（option_academy_sync.sync_id）',
  last_sync_id       bigint(20)     not null                   comment '最后出现批次（等于最新批次即为当前内容）',
  create_by          varchar(64)    default ''                 comment '创建者',
  create_time        datetime       default null               comment '创建时间（东八区）',
  create_time_utc    datetime       default null               comment '创建时间（UTC）',
  update_by          varchar(64)    default ''                 comment '更新者',
  update_time        datetime       default null               comment '更新时间（东八区）',
  update_time_utc    datetime       default null               comment '更新时间（UTC）',
  remark             varchar(500)   default null               comment '备注',
  primary key (version_id),
  unique key uk_option_academy_glossary (item_key, content_hash),
  key idx_option_academy_glossary_last (last_sync_id, sort_no)
) engine=innodb row_format=compressed comment='学堂菜单-术语速查：/academy/glossary 术语（释义、在卡上、不是）';

-- ----------------------------------------------------------------------------
-- 定时任务（调用目标须在 com.ruoyi.quartz.task 包内）
-- 学堂内容按周更新，每 30 分钟抓取一次（约 10 个页面，间隔 800ms），内容变化时写库
-- ----------------------------------------------------------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM学堂菜单同步', 'DEFAULT', 'optionSyncTask.syncAcademy()', '0 15/30 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 30 分钟抓取学堂首页 / 课程 / 一周问答 / 术语速查页面，内容变化时写库并刷新 Redis'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncAcademy()');

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select 'OSM学堂菜单强制同步', 'DEFAULT', 'optionSyncTask.syncAcademyNow()', '0 0 0 1 1 ? 2099', '3', '1', '1', 'admin', sysdate(), '暂停状态，首次部署或需要时在定时任务页面点击"执行一次"'
from dual where not exists (select 1 from sys_job where invoke_target = 'optionSyncTask.syncAcademyNow()');
