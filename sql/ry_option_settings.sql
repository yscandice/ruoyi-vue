-- ----------------------------
-- 数据权限步骤9：设置（技术文档 4.2、7.3）
--   阅读进度 /api/member/updates 改为按用户记录，从「今日」移到「设置」（本人数据 SELF）；
--   新增 /api/member/settings、/api/member/notifications/reset、/api/member/updates/seen；
--   「设置」排在「更新」之前匹配（/api/member/updates/** 也能匹配到 /api/member/updates 与 /updates/seen）。
--   可重复执行。
-- ----------------------------
update option_access_policy
   set path_pattern = '/api/member/feed,/api/member/today/**,/api/member/premarket,/api/member/seller-guide,/api/member/entitlement,/api/member/decisions,/api/member/management-choices,/api/member/support',
       update_by = 'admin', update_time = date_add(utc_timestamp(), interval 8 hour), update_time_utc = utc_timestamp()
 where resource_code = 'today';

update option_access_policy
   set path_pattern = '/api/member/settings,/api/member/settings/**,/api/member/profile,/api/member/notifications,/api/member/notifications/**,/api/member/updates,/api/member/updates/seen,/api/member/password,/api/member/avatar,/api/member/referrals,/api/member/push,/api/member/api-keys,/api/member/advisory-contact',
       order_num = 8,
       update_by = 'admin', update_time = date_add(utc_timestamp(), interval 8 hour), update_time_utc = utc_timestamp()
 where resource_code = 'settings';

update option_access_policy
   set order_num = 9,
       update_by = 'admin', update_time = date_add(utc_timestamp(), interval 8 hour), update_time_utc = utc_timestamp()
 where resource_code = 'update';
