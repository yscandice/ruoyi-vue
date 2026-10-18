-- ----------------------------------------------------------------------------
-- 期权 today 分支表：时间列统一为「东八区 + UTC」双列（已建表的库执行本脚本）
--   原列（sync_time / create_time / update_time / first_sync_time / last_sync_time）= 东八区
--   新增 *_utc 列 = UTC；之后由程序写入时同时写两列（utc_timestamp() 计算，不依赖数据库时区）
-- 可重复执行：列已存在则跳过；回填只处理 *_utc 为空的历史行
-- 回填假设：原 sysdate() 写入的列按「当前数据库会话时区」，option_feed_sync.sync_time 按东八区（JDBC serverTimezone=GMT+8）
-- ----------------------------------------------------------------------------

set @db_offset = timestampdiff(second, utc_timestamp(), now());

-- 1. 新增列
set @s = (select if(sum(table_name = 'option_feed_sync') > 0 and sum(table_name = 'option_feed_sync' and column_name = 'sync_time_utc') = 0,
  'alter table option_feed_sync add column sync_time_utc datetime default null comment ''同步时间（UTC）'' after sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_sync');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_sync') > 0 and sum(table_name = 'option_feed_sync' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_sync add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_sync');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_block') > 0 and sum(table_name = 'option_feed_block' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_block add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_block');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_history') > 0 and sum(table_name = 'option_feed_history' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_history add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_history');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_history') > 0 and sum(table_name = 'option_feed_history' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_history add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_history');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_management') > 0 and sum(table_name = 'option_feed_management' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_management add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_management');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_management') > 0 and sum(table_name = 'option_feed_management' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_management add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_management');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_protection') > 0 and sum(table_name = 'option_feed_protection' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_protection add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_protection');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_protection') > 0 and sum(table_name = 'option_feed_protection' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_protection add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_protection');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_settlement') > 0 and sum(table_name = 'option_feed_settlement' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_settlement add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_settlement');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_settlement') > 0 and sum(table_name = 'option_feed_settlement' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_settlement add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_settlement');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_order_intent') > 0 and sum(table_name = 'option_feed_order_intent' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_order_intent add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_order_intent');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_order_intent') > 0 and sum(table_name = 'option_feed_order_intent' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_order_intent add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_order_intent');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_midterm') > 0 and sum(table_name = 'option_feed_midterm' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_midterm add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_midterm');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_midterm') > 0 and sum(table_name = 'option_feed_midterm' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_midterm add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_midterm');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_signal') > 0 and sum(table_name = 'option_feed_signal' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_signal add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_signal');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_signal') > 0 and sum(table_name = 'option_feed_signal' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_signal add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_signal');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_event') > 0 and sum(table_name = 'option_feed_event' and column_name = 'create_time_utc') = 0,
  'alter table option_feed_event add column create_time_utc datetime default null comment ''创建时间（UTC）'' after create_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_event');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_feed_event') > 0 and sum(table_name = 'option_feed_event' and column_name = 'update_time_utc') = 0,
  'alter table option_feed_event add column update_time_utc datetime default null comment ''更新时间（UTC）'' after update_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_feed_event');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_resource_sync') > 0 and sum(table_name = 'option_resource_sync' and column_name = 'sync_time_utc') = 0,
  'alter table option_resource_sync add column sync_time_utc datetime default null comment ''同步时间（UTC）'' after sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_resource_sync');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_seller_guide') > 0 and sum(table_name = 'option_res_seller_guide' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_seller_guide add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_seller_guide');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_seller_guide') > 0 and sum(table_name = 'option_res_seller_guide' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_seller_guide add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_seller_guide');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_entitlement') > 0 and sum(table_name = 'option_res_entitlement' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_entitlement add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_entitlement');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_entitlement') > 0 and sum(table_name = 'option_res_entitlement' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_entitlement add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_entitlement');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_decision') > 0 and sum(table_name = 'option_res_decision' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_decision add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_decision');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_decision') > 0 and sum(table_name = 'option_res_decision' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_decision add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_decision');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_management_choice') > 0 and sum(table_name = 'option_res_management_choice' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_management_choice add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_management_choice');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_management_choice') > 0 and sum(table_name = 'option_res_management_choice' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_management_choice add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_management_choice');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_premarket') > 0 and sum(table_name = 'option_res_premarket' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_premarket add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_premarket');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_premarket') > 0 and sum(table_name = 'option_res_premarket' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_premarket add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_premarket');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_update') > 0 and sum(table_name = 'option_res_update' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_update add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_update');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_update') > 0 and sum(table_name = 'option_res_update' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_update add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_update');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_support') > 0 and sum(table_name = 'option_res_support' and column_name = 'first_sync_time_utc') = 0,
  'alter table option_res_support add column first_sync_time_utc datetime default null comment ''首次同步时间（UTC）'' after first_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_support');
prepare st from @s; execute st; deallocate prepare st;
set @s = (select if(sum(table_name = 'option_res_support') > 0 and sum(table_name = 'option_res_support' and column_name = 'last_sync_time_utc') = 0,
  'alter table option_res_support add column last_sync_time_utc datetime default null comment ''最近命中时间（UTC）'' after last_sync_time', 'select 1')
  from information_schema.columns where table_schema = database() and table_name = 'option_res_support');
prepare st from @s; execute st; deallocate prepare st;

-- 2. 回填历史数据（原列改为东八区，*_utc 为 UTC）
update option_feed_sync set sync_time_utc = date_sub(sync_time, interval 8 hour) where sync_time_utc is null and sync_time is not null;
update option_feed_sync set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_block set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_history set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_history set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_management set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_management set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_protection set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_protection set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_settlement set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_settlement set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_order_intent set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_order_intent set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_midterm set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_midterm set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_signal set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_signal set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_feed_event set create_time_utc = date_sub(create_time, interval @db_offset second), create_time = date_add(date_sub(create_time, interval @db_offset second), interval 8 hour) where create_time_utc is null and create_time is not null;
update option_feed_event set update_time_utc = date_sub(update_time, interval @db_offset second), update_time = date_add(date_sub(update_time, interval @db_offset second), interval 8 hour) where update_time_utc is null and update_time is not null;
update option_resource_sync set sync_time_utc = date_sub(sync_time, interval @db_offset second), sync_time = date_add(date_sub(sync_time, interval @db_offset second), interval 8 hour) where sync_time_utc is null and sync_time is not null;
update option_res_seller_guide set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_seller_guide set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
update option_res_entitlement set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_entitlement set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
update option_res_decision set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_decision set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
update option_res_management_choice set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_management_choice set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
update option_res_premarket set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_premarket set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
update option_res_update set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_update set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
update option_res_support set first_sync_time_utc = date_sub(first_sync_time, interval @db_offset second), first_sync_time = date_add(date_sub(first_sync_time, interval @db_offset second), interval 8 hour) where first_sync_time_utc is null and first_sync_time is not null;
update option_res_support set last_sync_time_utc = date_sub(last_sync_time, interval @db_offset second), last_sync_time = date_add(date_sub(last_sync_time, interval @db_offset second), interval 8 hour) where last_sync_time_utc is null and last_sync_time is not null;
