-- ----------------------------
-- 数据权限功能上线：打开读取路由总开关（技术文档 13.2 步骤 11）
-- 前提：已执行 ry_option_access.sql、ry_option_register.sql、ry_option_settings.sql、ry_option_member.sql，
--       冻结任务「OSM盘后视图冻结」已正常运行
-- 执行后在后台「系统管理 → 参数设置」点「刷新缓存」（或重启服务）生效
-- 回滚：把 config_value 改回 'false' 并刷新缓存
-- ----------------------------
update sys_config set config_value = 'true', update_by = 'admin', update_time = sysdate()
where config_key = 'option.access.enabled';
