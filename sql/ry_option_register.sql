-- ----------------------------
-- 数据权限步骤8：会员用户名即注册邮箱，sys_user.user_name 放宽到 50
-- ----------------------------
alter table sys_user modify user_name varchar(50) not null comment '用户账号';
