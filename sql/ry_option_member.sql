-- ----------------------------
-- 数据权限步骤10：会员后台管理（技术文档 9.3.3）
--   菜单：2100 会员管理（目录）/ 2101 会员列表 / 2102 注册日志，按钮 2111–2115
--   字典：option_member_level / option_register_action / option_register_result
--   默认只有 admin 拥有这些权限；会员角色 option_member 不分配任何后台菜单。可重复执行。
-- ----------------------------

-- 1、一级目录：会员管理
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 2100, '会员管理', 0, 5, 'member', null, '', '', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', sysdate(), '', null, '网站会员管理目录'
from dual where not exists (select 1 from sys_menu where menu_id = 2100);

-- 2、页面：会员列表 / 注册日志
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 2101, '会员列表', 2100, 1, 'list', 'option/member/index', '', 'OptionMember', 1, 0, 'C', '0', '0', 'option:member:list', 'user', 'admin', sysdate(), '', null, '会员列表菜单'
from dual where not exists (select 1 from sys_menu where menu_id = 2101);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select 2102, '注册日志', 2100, 2, 'registerLog', 'option/registerLog/index', '', 'OptionRegisterLog', 1, 0, 'C', '0', '0', 'option:member:log', 'log', 'admin', sysdate(), '', null, '会员注册与风控日志菜单'
from dual where not exists (select 1 from sys_menu where menu_id = 2102);

-- 3、按钮（会员列表下）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select t.menu_id, t.menu_name, 2101, t.order_num, '#', '', '', '', 1, 0, 'F', '0', '0', t.perms, '#', 'admin', sysdate(), '', null, ''
from (
  select 2111 menu_id, '会员查询' menu_name, 1 order_num, 'option:member:query' perms
  union all select 2112, '会员修改', 2, 'option:member:edit'
  union all select 2113, '重置密码', 3, 'option:member:resetPwd'
  union all select 2114, '强制下线', 4, 'option:member:forceLogout'
  union all select 2115, '会员导出', 5, 'option:member:export'
) t
where not exists (select 1 from sys_menu m where m.menu_id = t.menu_id);

-- 4、字典类型
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select t.dict_name, t.dict_type, '0', 'admin', sysdate(), t.remark
from (
  select '会员身份' dict_name, 'option_member_level' dict_type, '会员身份（按体验期、付费、状态实时计算）' remark
  union all select '注册日志动作', 'option_register_action', '会员注册日志动作'
  union all select '注册日志结果', 'option_register_result', '会员注册日志结果'
) t
where not exists (select 1 from sys_dict_type d where d.dict_type = t.dict_type);

-- 5、字典数据
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select t.dict_sort, t.dict_label, t.dict_value, t.dict_type, '', t.list_class, 'N', '0', 'admin', sysdate(), t.remark
from (
  select 1 dict_sort, '体验期' dict_label, 'TRIAL' dict_value, 'option_member_level' dict_type, 'primary' list_class, '注册后 72 小时内，看实时数据' remark
  union all select 2, '盘后版', 'DELAYED', 'option_member_level', 'warning', '体验期结束，看上一交易日收盘后数据'
  union all select 3, '付费', 'PAID', 'option_member_level', 'success', '付费有效期内，看实时数据'
  union all select 4, '停用', 'DISABLED', 'option_member_level', 'danger', '账号停用'
  union all select 5, '内部人员', 'STAFF', 'option_member_level', 'info', '管理员 / option_staff'
  union all select 1, '发送验证码', 'SEND_CODE', 'option_register_action', 'primary', null
  union all select 2, '注册', 'REGISTER', 'option_register_action', 'success', null
  union all select 1, '成功', 'SUCCESS', 'option_register_result', 'success', null
  union all select 2, '拒绝', 'REJECTED', 'option_register_result', 'warning', '风控或校验未通过'
  union all select 3, '失败', 'FAILED', 'option_register_result', 'danger', '系统原因失败（如邮件发送失败）'
) t
where not exists (select 1 from sys_dict_data d where d.dict_type = t.dict_type and d.dict_value = t.dict_value);
