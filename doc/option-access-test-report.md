# 数据权限功能 测试报告（步骤 11）

> 对应技术文档《‘设置’功能—数据权限功能-后端的技术文档》13.2 步骤 11：按第 11 节和 9.7 节的测试点逐项回归。

## 1. 结论

| 项目 | 结果 |
| --- | --- |
| 接口回归（`doc/option-access-regression.sh`） | **33 / 33 通过**，0 失败 |
| 单元测试（`OptionMarketCalendarTest`：节假日、提前收盘、夏令时、3.2 的 4 个示例、lag=0） | **10 / 10 通过** |
| 编译打包 | 通过 |
| 总开关 `option.access.enabled` | 测试环境已打开（`true`） |

## 2. 测试环境

| 项目 | 内容 |
| --- | --- |
| 分支 | `dataPermissions`（步骤 0–10 共 11 次提交） |
| 时间 | 2026-10-20（周二）美东 14:00–14:30，盘中 |
| 数据源 | osmtrade.com，同步账号已切换为新账号，全部菜单强制同步成功 |
| 测试账号 | admin（STAFF）、ry（普通后台用户，无 `option:*` 权限）、m_trial（盘后版）、m_expired（体验期中）、m_paid（付费）、测试时现场注册 1 个新会员（测试后删除） |
| 时间相关场景 | 放出时间用 `/option/access/preview?at=` 指定时间点验证；体验期边界用 SQL 改 `register_time / trial_end_time` 后真实等待到期验证 |

## 3. 测试明细

### 3.1 第 11 节 测试要点

| 编号 | 场景 | 结果 | 实测 |
| --- | --- | --- | --- |
| A06a | 上线第一天（尚无放出）盘后版访问 6 个核心菜单 | ✅ PASS | delayedFallback=EMPTY，视图=[DELAYED DELAYED DELAYED DELAYED DELAYED DELAYED ]，6 个都返回空结构 |
| A06b | 冻结后未放出时盘后版仍按 delayedFallback 处理 | ✅ PASS | 冻结记录 7（2026-10-20，FROZEN），盘后版 X-Option-Data-Empty=true |
| A05a | 同一天重复冻结 / 重复调度 | ✅ PASS | 手动重复冻结被拒绝（2026-10-20 已冻结，如需重新冻结请选择强制冻结）；冻结任务连跑 2 次后 2026-10-20 记录数=1 |
| A05b | 放出后盘后版看到冻结数据 | ✅ PASS | 放出 70 个键，option:delayed:* 键数=70；6 个核心菜单 View=DELAYED、Trade-Date=2026-10-20、非空 |
| A05c | 周二收盘 +10 分钟（预览 16:30） | ✅ PASS | 数据日=2026-10-19，放出=2026-10-20T16:10:00-04:00，下次=2026-10-21T16:10:00-04:00 |
| A11 | 最后一次同步早于收盘 / 失败时冻结 | ✅ PASS | 冻结仍执行，stale_resources=[today,conduct,follow,settlement,record]，盘后版响应 X-Option-Data-Stale=true |
| A10 | 清空 Redis 盘后视图后访问 | ✅ PASS | 删除 70 个键后：视图=DELAYED，feed/conduct 与删除前一致=是，回填后键数=70 |
| A12a | 盘后版直接调用各实时接口 | ✅ PASS | 17 个 GET 路径的 X-Option-Data-View 都与策略一致（盘后菜单=DELAYED，学堂/更新=REALTIME） |
| A12b | 策略里没登记的路径默认 DELAYED | ✅ PASS | 临时停用「战绩」策略：盘后版=DELAYED，管理员=REALTIME；已恢复 |
| A04 | 周二盘中（美东 10:00） | ✅ PASS | 2026-10-16 2026-10-19T16:10:00-04:00 2026-10-20T16:10:00-04:00（看到上周五数据） |
| A08 | 周末 / 节假日 / 提前收盘日 | ✅ PASS | 周六 12:00→2026-10-22 2026-10-23T16:10:00-04:00 2026-10-26T16:10:00-04:00；感恩节次日 14:00→2026-11-25 2026-11-27T13:10:00-05:00 2026-11-30T16:10:00-05:00；感恩节当天→2026-11-24 False |
| A09 | 夏令时切换周（2026-11-01 结束夏令时） | ✅ PASS | 周五 10-30 放出=北京 2026-10-31 04:10:00；周六看下次放出=北京 2026-11-03 05:10:00；周一 11-02 放出=北京 2026-11-03 05:10:00 |
| A07 | delayedLagSessions 改为 0 | ✅ PASS | 周二 16:30 看到 2026-10-20 2026-10-20T16:10:00-04:00（当天收盘数据），已改回 1 |
| A01a | 注册后 71 小时 59 分访问 | ✅ PASS | 身份=TRIAL/REALTIME，剩余 59s，身份缓存 TTL=60s（不超过剩余时长） |
| A01b | 72 小时后访问（体验期自然到期，未清缓存） | ✅ PASS | 身份=DELAYED/DELAYED，进行菜单视图=DELAYED |
| A02 | 注册后立即访问 6 个核心菜单 | ✅ PASS | 体验期会员 view=REALTIME，6 个菜单响应与管理员逐字节一致 |
| A03 | 把 trial_end_time 改到过去 | ✅ PASS | 改库并清身份缓存后，下一次请求视图=DELAYED（后台接口修改会自动清缓存，见 B03） |
| A15 | 会员调用 POST /api/member/login | ✅ PASS | 返回 code=403（没有权限，请联系管理员授权） |
| A14 | GET /api/member/referrals 不含同步账号推荐码 | ✅ PASS | referrals 返回 {"enabled": false}；体验期 / 盘后版会员遍历全部 GET 接口均不含同步账号推荐码与用户名 |
| A13 | 设置并发保存 | ✅ PASS | 两个窗口读到同一个 updatedAt=2026-10-21T02:19:30+08:00：第一次保存 code=200，第二次带旧 updatedAt code=409（设置已在其他设备修改，请刷新） |
| A16 | 同一 IP 当天第 4 次注册 | ✅ PASS | 已有 3 次成功注册（1 次真实 + 2 条造数）；第 4 次 code=429（当前网络今天注册次数已达上限，请明天再试），风控日志=REJECTED:IP 当天注册超限 |

### 3.2 9.7 测试要点（会员后台）

| 编号 | 场景 | 结果 | 实测 |
| --- | --- | --- | --- |
| B01 | 网站注册一个新会员 | ✅ PASS | 发码 code=200（邮件真实发送）；注册拿到 token；后台列表立即出现 userId=9011，身份=TRIAL，剩余 71 小时；注册日志=[REGISTER:SUCCESS,SEND_CODE:SUCCESS] |
| B02 | 后台延长 24 小时 | ✅ PASS | 到期 2026-10-24 02:19:29 → 2026-10-25 02:19:29（+24h）；会员端剩余约 95 小时；操作日志 1 条 |
| B03 | 把体验到期时间缩短到过去 | ✅ PASS | 会员端视图 REALTIME → 下一次请求 DELAYED |
| B05 | 会员账号登录 ruoyi-vue3（带 X-Client-Type: admin） | ✅ PASS | 提示「该账号无后台权限」，登录日志「该账号无后台权限」；admin 带同样请求头正常登录 |
| B04 | 后台停用会员 | ✅ PASS | 会员端下一次请求 code=401；再登录提示「用户已封禁，请联系管理员」（若依默认文案，文档写「账号已停用」） |
| B06 | 没有 option:member:* 权限的后台账号（ry） | ✅ PASS | 列表、统计、延长、停用、付费、重置密码、强制下线 7 个接口都返回 403 |
| B07 | 导出 | ✅ PASS | 筛选 level=DELAYED：列表 2 条 [9001,9011]，Excel [9001,9011]；表头：用户ID/邮箱/昵称/身份/状态/注册时间/体验到期/体验剩余秒数/累计调整小时/付费到期/最近访问/注册IP/在线 |
| B08 | 「用户管理」默认列表 | ✅ PASS | 默认显示会员 0 个；params[showMember]=true 后显示 5 个 |

### 3.3 补充回归（9.6 / 步骤验收）

| 编号 | 场景 | 结果 | 实测 |
| --- | --- | --- | --- |
| C01 | 开关关闭：盘后版会员与原来一致 | ✅ PASS | 无 X-Option-Data-View 头=无，与管理员响应 sha 相同=是 |
| C02 | 全部 /api/member/** 路径都已登记策略 | ✅ PASS | 共 42 个，登记 39，排除 3（access/register），未登记 0 |
| C03 | 设置 / 取消付费 | ✅ PASS | 设置后 PAID/REALTIME，取消后 DELAYED/DELAYED |
| C04 | 会员调用后台接口 | ✅ PASS | /system/user/list 返回 code=403 |

## 4. 发现与说明

1. **停用提示文案**：再登录提示的是若依默认文案「用户已封禁，请联系管理员」（`messages.properties` 的 `user.blocked`），文档写的是「账号已停用」。改这个文案会影响所有后台用户，所以没有改，需要确认。
2. **会员登录后台**：后端靠 `X-Client-Type: admin` 请求头识别后台登录。ruoyi-vue3 登录请求需要加上这个请求头（9.4 前端工作），不加的话会员可以登录后台，但没有任何菜单和权限（接口都是 403）。
3. **Excel 时间**：服务器 JVM 时区为 UTC 时，导出的时间会早 8 小时（接口 JSON 正常）。生产 JVM 需要设置为 `-Duser.timezone=Asia/Shanghai`。
4. **手动冻结**：盘中强制冻结会把当天记成「已冻结」，当天收盘的定时冻结会因此跳过。生产环境盘中不要手动冻结；测试产生的冻结记录已全部清理。
5. **注册验证码**：测试时邮件真实发送，验证码通过改写 Redis 中的哈希模拟填写；「同一 IP 第 4 次注册」用 1 次真实注册 + 2 条注册日志造数。

## 5. 上线步骤

1. 合并 `dataPermissions` 到 `main`，打包部署。
2. 按顺序执行 SQL（都可以重复执行）：`ry_option_access.sql` → `ry_option_register.sql` → `ry_option_settings.sql` → `ry_option_member.sql`。
3. 配置环境变量 `MAIL_USERNAME` / `MAIL_PASSWORD`（SMTP），JVM 加 `-Duser.timezone=Asia/Shanghai`。
4. 确认定时任务「OSM盘后视图冻结」（`optionAccessTask.freezeClose()`，每 5 分钟）处于正常状态。
5. 执行 `sql/ry_option_release.sql` 打开总开关，然后在后台「参数设置」点「刷新缓存」（或重启）。

## 6. 上线后观察（1–2 个交易日）

| 时间（北京，夏令时） | 预期 | 检查方式 |
| --- | --- | --- |
| 第 1 个交易日次日 04:10 后 | 冻结当天数据（`status = FROZEN`），**不放出**；盘后版用户按 `delayedFallback=EMPTY` 返回空结构 | `select * from option_close_snapshot order by snapshot_id desc` |
| 第 2 个交易日次日 04:10 后 | 冻结第 2 天，放出第 1 天（第 1 天记录变 `RELEASED`）；盘后版 `X-Option-Data-Trade-Date` = 第 1 天 | 同上 + 盘后版账号访问任一菜单看响应头 |
| 每次 | 同一交易日只有 1 条记录；`stale_resources` 为空（有值说明收盘时同步失败或过期） | `GET /option/access/snapshot/status` |

> 2026-11-01 起美国冬令时，放出时间变为北京时间 05:10。
