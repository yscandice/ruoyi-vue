-- 开源 Skill 文件管理模块
create table if not exists sys_open_skill (
  skill_id bigint(20) not null auto_increment comment 'Skill ID',
  skill_name varchar(100) not null comment 'Skill 名称',
  version varchar(50) default '1.0.0' comment '版本',
  description varchar(1000) default '' comment '描述',
  author varchar(100) default '' comment '作者',
  repo_url varchar(500) default '' comment '开源仓库地址',
  file_name varchar(255) not null comment '上传文件名',
  file_path varchar(500) not null comment '服务器相对资源路径',
  file_size bigint(20) default 0 comment '文件大小（字节）',
  download_count bigint(20) default 0 comment '下载次数',
  status char(1) default '0' comment '状态（0正常 1停用）',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime default null comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  remark varchar(500) default '' comment '备注',
  primary key (skill_id),
  key idx_open_skill_name (skill_name),
  key idx_open_skill_status (status)
) engine=innodb default charset=utf8mb4 comment='开源 Skill 文件表';

-- AgentDock 网站左侧导航菜单
create table if not exists sys_site_navigation (
  navigation_id bigint(20) not null auto_increment comment '导航 ID',
  menu_key varchar(50) not null comment '菜单标识',
  menu_name varchar(80) not null comment '菜单名称',
  icon varchar(80) not null comment '图标标识',
  route_path varchar(255) not null comment '前端路由',
  order_num int(4) not null default 0 comment '显示顺序',
  menu_type varchar(20) not null default 'menu' comment '展示类型（menu菜单 button按钮）',
  status char(1) not null default '0' comment '状态（0正常 1停用）',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime default null comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime default null comment '更新时间',
  remark varchar(500) default null comment '备注',
  primary key (navigation_id),
  unique key uk_site_navigation_key (menu_key),
  key idx_site_navigation_status_order (status, order_num)
) engine=innodb default charset=utf8mb4 comment='AgentDock 网站左侧导航菜单';

insert into sys_site_navigation (menu_key, menu_name, icon, route_path, order_num, menu_type, status, create_by, create_time)
select seed.menu_key, seed.menu_name, seed.icon, seed.route_path, seed.order_num, seed.menu_type, '0', 'admin', sysdate()
from (
    select 'home' as menu_key, '首页' as menu_name, 'home' as icon, '/' as route_path, 1 as order_num, 'menu' as menu_type union all
    select 'explore', '探索', 'search', '/explore', 2, 'menu' union all
    select 'notifications', '通知', 'bell', '/notifications', 3, 'menu' union all
    select 'following', '关注', 'user-plus', '/following', 4, 'menu' union all
    select 'messages', '私信', 'message-circle', '/messages', 5, 'menu' union all
    select 'grok', 'Grok', 'sparkles', '/grok', 6, 'menu' union all
    select 'bookmarks', '收藏', 'bookmark', '/bookmarks', 7, 'menu' union all
    select 'profile', '个人资料', 'user', '/profile', 8, 'menu' union all
    select 'more', '更多', 'ellipsis', '/more', 9, 'menu' union all
    select 'submit_project', '提交项目', 'plus', '/submit', 10, 'button'
) seed
where not exists (select 1 from sys_site_navigation n where n.menu_key = seed.menu_key);

-- 添加“开源工具”顶级菜单，按钮权限在页面操作时校验。
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 118, '开源工具', 0, 4, 'opensource', 'opensource/index', '', '', 1, 0,
    'C', '0', '0', 'tool:opensource:list', 'guide', 'admin', sysdate(), '', null, '开源 Agent 与 Skill 管理菜单'
where not exists (select 1 from sys_menu where menu_id = 118 or (parent_id = 0 and path = 'opensource'));

-- 兼容已经执行过旧版本脚本的数据库：把原“系统工具”子菜单提升到顶级菜单。
update sys_menu set parent_id = 0, order_num = 4, component = 'opensource/index'
where menu_id = 118 or (menu_name = '开源工具' and path = 'opensource');

set @open_skill_menu_id = (select menu_id from sys_menu where menu_id = 118 or (parent_id = 0 and path = 'opensource') limit 1);

-- 导航管理后台页面
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 119, '导航管理', 0, 5, 'navigation', 'opensource/navigation/index', '', '', 1, 0,
    'C', '0', '0', '', 'tree', 'admin', sysdate(), '', null, 'AgentDock 网站左侧导航管理'
where not exists (select 1 from sys_menu where menu_id = 119 or (parent_id = 0 and path = 'navigation'));

set @navigation_admin_menu_id = (select menu_id from sys_menu where menu_id = 119 or (parent_id = 0 and path = 'navigation') limit 1);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select ids.menu_id, ids.menu_name, @open_skill_menu_id, ids.order_num, '', '', '', '', 1, 0, 'F', '0', '0', ids.perms, '#', 'admin', sysdate(), '', null, ''
from (
    select 1061 as menu_id, 'Skill 上传' as menu_name, 1 as order_num, 'tool:opensource:upload' as perms union all
    select 1062, 'Skill 下载', 2, 'tool:opensource:download' union all
    select 1063, 'Skill 删除', 3, 'tool:opensource:remove' union all
    select 1064, '状态修改', 4, 'tool:opensource:edit' union all
    select 1065, '导航查询', 5, 'tool:navigation:list' union all
    select 1066, '导航详情', 6, 'tool:navigation:query' union all
    select 1067, '导航新增', 7, 'tool:navigation:add' union all
    select 1068, '导航修改', 8, 'tool:navigation:edit' union all
    select 1069, '导航删除', 9, 'tool:navigation:remove'
) ids
where @open_skill_menu_id is not null
  and not exists (select 1 from sys_menu where menu_id = ids.menu_id or perms = ids.perms);

-- 把已存在的导航接口权限项挂到导航管理页面下。
update sys_menu set parent_id = @navigation_admin_menu_id
where perms in ('tool:navigation:list', 'tool:navigation:query', 'tool:navigation:add', 'tool:navigation:edit', 'tool:navigation:remove')
  and @navigation_admin_menu_id is not null;

insert into sys_role_menu (role_id, menu_id)
select 1, m.menu_id from sys_menu m
where (m.menu_id = @open_skill_menu_id or m.parent_id = @open_skill_menu_id)
  and @open_skill_menu_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = 1 and rm.menu_id = m.menu_id);

insert into sys_role_menu (role_id, menu_id)
select 1, m.menu_id from sys_menu m
where (m.menu_id = @navigation_admin_menu_id or m.parent_id = @navigation_admin_menu_id)
  and @navigation_admin_menu_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = 1 and rm.menu_id = m.menu_id);
