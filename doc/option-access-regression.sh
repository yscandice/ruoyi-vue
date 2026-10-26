#!/bin/bash
# ==========================================================================================
# 数据权限功能回归测试（技术文档《‘设置’功能—数据权限功能-后端》第 11 节、9.6 节、9.7 节）
#
# 仅限测试环境：会临时修改测试会员（m_trial / m_expired / m_paid）、手动冻结与放出、注册 1 个测试会员，
# 结束时自动还原（测试会员原值、冻结记录、注册账号、开关参数）。
#
# 前提：
#   1. 服务已启动；已执行 sql/ry_option_*.sql；存在测试会员 m_trial(9001) / m_expired(9002) / m_paid(9003)
#      与普通后台用户 ry（无 option:* 权限），密码均为 PWD0
#   2. 库里没有冻结记录（option_close_snapshot 为空），避免影响真实盘后视图
#   3. REG_EMAIL：真实可收信的邮箱，用于发送注册验证码（邮件会真实发出）；验证码通过改写 Redis 中的哈希模拟填写
#
# 用法：REG_EMAIL=xxx@qq.com bash doc/option-access-regression.sh
# 输出：每项 PASS / FAIL，汇总表写入 $OUT（TSV）
# ==========================================================================================
B=${B:-http://localhost:8090}
MY=${MY:-"mysql --default-character-set=utf8mb4 -uroot -ppassword ruoyi"}
RC=${RC:-redis6-cli}
PWD0=${PWD0:-admin123}
REG_EMAIL=${REG_EMAIL:?请设置 REG_EMAIL}
REG_IP=${REG_IP:-10.11.0.9}
OUT=${OUT:-/tmp/option-regression.tsv}
JOB_FREEZE=${JOB_FREEZE:-129}
: > "$OUT"
NPASS=0; NFAIL=0

sql(){ $MY -N -B -e "$1" 2>/dev/null; }
J=/tmp/_regress_j.py
cat > $J <<'PY'
import json,sys
try: d=json.load(open(sys.argv[1]))
except Exception: d={}
try: r=eval(sys.argv[2])
except Exception as e: r='ERR:'+str(e)
print(json.dumps(r,ensure_ascii=False) if isinstance(r,(dict,list)) else r)
PY
j(){ python3 $J "$1" "$2"; }
# t 编号 场景 "说明" 条件命令...
t(){ local id=$1 name=$2 detail=$3; shift 3
  if eval "$@"; then NPASS=$((NPASS+1)); printf '%s\t%s\tPASS\t%s\n' "$id" "$name" "$detail" >> "$OUT"; echo "PASS [$id] $name — $detail"
  else NFAIL=$((NFAIL+1)); printf '%s\t%s\tFAIL\t%s\n' "$id" "$name" "$detail" >> "$OUT"; echo "FAIL [$id] $name — $detail"; fi; }
# req TOKEN METHOD PATH [JSON] [curl 参数...]：响应头 /tmp/rh，响应体 /tmp/rb，输出 HTTP 状态
req(){ local tk=$1 m=$2 p=$3 d=${4:-}; shift; shift; shift; [ $# -gt 0 ] && shift
  local a=(-s -X "$m" -D /tmp/rh -o /tmp/rb -w '%{http_code}')
  [ -n "$tk" ] && a+=(-H "Authorization: Bearer $tk")
  [ -n "$d" ] && a+=(-H 'Content-Type: application/json' -d "$d")
  curl "${a[@]}" "$@" "$B$p"; }
hdr(){ grep -i "^$1:" /tmp/rh | tr -d '\r' | cut -d' ' -f2-; }
rsha(){ sha256sum /tmp/rb | cut -c1-16; }
code(){ j /tmp/rb 'd.get("code")'; }
msg(){ j /tmp/rb 'd.get("msg")'; }
tok(){ curl -s -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"${2:-$PWD0}\"}" "${@:3}" $B/login > /tmp/lb; j /tmp/lb 'd.get("token") or ""'; }
setcfg(){ sql "update sys_config set config_value='$2' where config_key='$1'"; req "$AD" DELETE /system/config/refreshCache >/dev/null; }
clearacc(){ req "$AD" DELETE "/option/access/cache/$1" >/dev/null; }
acc(){ req "$1" GET /api/member/access >/dev/null; }
view(){ req "$1" GET "$2" >/dev/null; hdr X-Option-Data-View; }
now8(){ sql "select date_format(utc_timestamp()+interval 8 hour,'%Y-%m-%d %H:%i:%s')"; }
pv(){ req "$AD" GET "/option/access/preview?at=$1" >/dev/null; j /tmp/rb "$2"; }
# 通过 SQL 直接设置体验期（东八区 + UTC 两套时间），参数：userId 注册时间偏移(秒) 到期偏移(秒)，相对当前
settrial(){ sql "update option_member_access set register_time=utc_timestamp()+interval 8 hour+interval $2 second, register_time_utc=utc_timestamp()+interval $2 second,
  trial_end_time=utc_timestamp()+interval 8 hour+interval $3 second, trial_end_time_utc=utc_timestamp()+interval $3 second where user_id=$1"; }
CORE="/api/member/feed /api/member/info-feed /api/member/conduct /api/member/follow /api/member/settlement /api/member/record"

echo "===== 准备 ====="
AD=$(tok admin); RY=$(tok ry)
[ -n "$AD" ] || { echo "admin 登录失败"; exit 1; }
[ "$(sql 'select count(*) from option_close_snapshot')" = "0" ] || { echo "库里已有冻结记录，请在干净的测试库运行"; exit 1; }
START=$(now8); OPER0=$(sql "select ifnull(max(oper_id),0) from sys_oper_log")
ORIG_ENABLED=$(sql "select config_value from sys_config where config_key='option.access.enabled'")
ORIG_LAG=$(sql "select config_value from sys_config where config_key='option.access.delayedLagSessions'")
RESTORE_SQL=$(sql "select concat('update option_member_access set level=',quote(level),',register_time=',quote(register_time),',register_time_utc=',quote(register_time_utc),
 ',trial_end_time=',quote(trial_end_time),',trial_end_time_utc=',quote(trial_end_time_utc),',trial_extend_hours=',quote(trial_extend_hours),
 ',paid_until=',quote(paid_until),',paid_until_utc=',quote(paid_until_utc),',status=',quote(status),',remark=',quote(remark),',update_by=',quote(update_by),
 ',update_time=',quote(update_time),',update_time_utc=',quote(update_time_utc),',last_access_time=',quote(last_access_time),' where user_id=',user_id,';')
 from option_member_access where user_id in (9001,9002,9003)")
cleanup(){
  echo "===== 还原 ====="
  [ -n "$NEW_ID" ] && { req "$AD" DELETE "/option/member/$NEW_ID/online" >/dev/null
    sql "delete from sys_user_role where user_id=$NEW_ID; delete from option_member_profile where user_id=$NEW_ID; delete from option_member_notification where user_id=$NEW_ID;
      delete from option_member_update_seen where user_id=$NEW_ID; delete from option_member_access where user_id=$NEW_ID; delete from sys_user where user_id=$NEW_ID"
    $RC del "option:member:access:$NEW_ID" "option:member:settings:$NEW_ID" "option:member:lastAccess:$NEW_ID" >/dev/null; }
  sql "delete from option_member_register_log where ip='$REG_IP' or email='$REG_EMAIL' or email like 'regress-%@example.com'"
  $RC del "option:member:code:$REG_EMAIL" "option:member:code:limit:$REG_EMAIL" >/dev/null
  sql "$RESTORE_SQL update sys_user set status='0' where user_id in (9001,9002,9003)"
  [ -n "$SID" ] && sql "delete from option_close_snapshot_data where snapshot_id>=$SID; delete from option_close_snapshot where snapshot_id>=$SID"
  $RC --scan --pattern 'option:delayed:*' | xargs -r -n 500 $RC del >/dev/null
  $RC del option:access:release:current option:access:freeze:status >/dev/null
  clearacc 9001,9002,9003
  setcfg option.access.delayedLagSessions "$ORIG_LAG"
  setcfg option.access.enabled "${KEEP_ENABLED:-$ORIG_ENABLED}"
  echo "通过 $NPASS 项，失败 $NFAIL 项；明细 $OUT"; }
trap cleanup EXIT
setcfg option.access.enabled false
clearacc 9001,9002,9003
TR=$(tok m_trial); TE=$(tok m_expired); TP=$(tok m_paid)
# m_trial 体验期已过（盘后版）、m_expired 体验期中、m_paid 付费
settrial 9001 -259300 -100; settrial 9002 -3600 255600; clearacc 9001,9002

echo "===== 开关关闭时行为不变（步骤 6） ====="
req "$TR" GET /api/member/conduct >/dev/null; V1=$(hdr X-Option-Data-View); S1=$(rsha)
req "$AD" GET /api/member/conduct >/dev/null; S2=$(rsha)
t C01 "开关关闭：盘后版会员与原来一致" "无 X-Option-Data-View 头=${V1:-无}，与管理员响应 sha 相同=$([ "$S1" = "$S2" ] && echo 是 || echo 否)" '[ -z "$V1" ] && [ "$S1" = "$S2" ]'
setcfg option.access.enabled true

echo "===== 冻结 / 放出（第 11 节） ====="
for p in $CORE; do req "$TR" GET $p >/dev/null; E="$E$(hdr X-Option-Data-Empty)"; VV="$VV$(hdr X-Option-Data-View) "; done
t A06a "上线第一天（尚无放出）盘后版访问 6 个核心菜单" "delayedFallback=EMPTY，视图=[$VV]，6 个都返回空结构" '[ "$E" = "truetruetruetruetruetrue" ] && ! echo "$VV" | grep -q REALTIME'
req "$AD" POST /option/access/snapshot/freeze '{"force":true}' >/dev/null; SID=$(j /tmp/rb 'd["data"]["snapshotId"]'); TD=$(j /tmp/rb 'd["data"]["tradeDate"]'); STALE=$(j /tmp/rb '",".join(d["data"]["staleResources"])')
req "$TR" GET /api/member/conduct >/dev/null
t A06b "冻结后未放出时盘后版仍按 delayedFallback 处理" "冻结记录 $SID（$TD，FROZEN），盘后版 X-Option-Data-Empty=$(hdr X-Option-Data-Empty)" '[ "$(hdr X-Option-Data-Empty)" = "true" ] && [ "$(sql "select status from option_close_snapshot where snapshot_id=$SID")" = "FROZEN" ]'
req "$AD" POST /option/access/snapshot/freeze '{"force":false}' >/dev/null; M1=$(msg)
req "$AD" PUT /monitor/job/run "{\"jobId\":$JOB_FREEZE,\"jobGroup\":\"DEFAULT\"}" >/dev/null; sleep 3
req "$AD" PUT /monitor/job/run "{\"jobId\":$JOB_FREEZE,\"jobGroup\":\"DEFAULT\"}" >/dev/null; sleep 3
CNT=$(sql "select count(*) from option_close_snapshot where trade_date='$TD'")
t A05a "同一天重复冻结 / 重复调度" "手动重复冻结被拒绝（$M1）；冻结任务连跑 2 次后 $TD 记录数=$CNT" '[ "$CNT" = "1" ] && echo "$M1" | grep -q "已冻结"'
req "$AD" POST "/option/access/snapshot/$SID/release" >/dev/null; RK=$(j /tmp/rb 'd["data"]["keyCount"]')
OKV=1; for p in $CORE; do req "$TR" GET $p >/dev/null; [ "$(hdr X-Option-Data-View)" = "DELAYED" ] && [ "$(hdr X-Option-Data-Trade-Date)" = "$TD" ] && [ -z "$(hdr X-Option-Data-Empty)" ] || OKV=0; done
DK=$($RC --scan --pattern 'option:delayed:*' | wc -l)
t A05b "放出后盘后版看到冻结数据" "放出 $RK 个键，option:delayed:* 键数=$DK；6 个核心菜单 View=DELAYED、Trade-Date=$TD、非空" '[ "$OKV" = 1 ] && [ "$DK" = "$RK" ]'
t A05c "周二收盘 +10 分钟（预览 16:30）" "数据日=$(pv 2026-10-20T16:30:00-04:00 'd["data"]["dataTradeDate"]')，放出=$(j /tmp/rb 'd["data"]["releasedAt"]')，下次=$(j /tmp/rb 'd["data"]["nextReleaseAt"]')" \
  '[ "$(j /tmp/rb "d[\"data\"][\"dataTradeDate\"]+d[\"data\"][\"releasedAt\"]+d[\"data\"][\"nextReleaseAt\"]")" = "2026-10-192026-10-20T16:10:00-04:002026-10-21T16:10:00-04:00" ]'
req "$TR" GET /api/member/conduct >/dev/null
t A11 "最后一次同步早于收盘 / 失败时冻结" "冻结仍执行，stale_resources=[$STALE]，盘后版响应 X-Option-Data-Stale=$(hdr X-Option-Data-Stale)" '[ -n "$STALE" ] && [ "$(hdr X-Option-Data-Stale)" = "true" ]'
req "$TR" GET /api/member/feed >/dev/null; F1=$(rsha); req "$TR" GET /api/member/conduct >/dev/null; C1=$(rsha)
$RC --scan --pattern 'option:delayed:*' | xargs -r -n 500 $RC del >/dev/null; $RC del option:access:release:current >/dev/null
req "$TR" GET /api/member/feed >/dev/null; F2=$(rsha); FV=$(hdr X-Option-Data-View); req "$TR" GET /api/member/conduct >/dev/null; C2=$(rsha)
t A10 "清空 Redis 盘后视图后访问" "删除 $DK 个键后：视图=$FV，feed/conduct 与删除前一致=$([ "$F1$C1" = "$F2$C2" ] && echo 是 || echo 否)，回填后键数=$($RC --scan --pattern 'option:delayed:*' | wc -l)" '[ "$FV" = "DELAYED" ] && [ "$F1$C1" = "$F2$C2" ]'

echo "===== 盘后版访问所有路径（第 11 节） ====="
req "$AD" GET /option/access/policy/check >/dev/null; cp /tmp/rb /tmp/pc.json
UNREG=$(j /tmp/pc.json 'len(d["data"]["unregistered"])')
t C02 "全部 /api/member/** 路径都已登记策略" "共 $(j /tmp/pc.json 'd["data"]["total"]') 个，登记 $(j /tmp/pc.json 'd["data"]["registered"]')，排除 $(j /tmp/pc.json 'd["data"]["excluded"]')（access/register），未登记 $UNREG" '[ "$UNREG" = 0 ]'
BAD=""; N=0
for row in $(j /tmp/pc.json '" ".join(i["path"]+"|"+i["views"]["DELAYED"] for i in d["data"]["items"] if i.get("views") and "GET" in str(i.get("methods") or i.get("method")) and "{" not in i["path"] and i["views"]["DELAYED"] in ("DELAYED","REALTIME"))'); do
  p=${row%|*}; exp=${row#*|}; N=$((N+1)); got=$(view "$TR" "$p"); [ "$got" = "$exp" ] || BAD="$BAD $p=$got/$exp"; done
t A12a "盘后版直接调用各实时接口" "$N 个 GET 路径的 X-Option-Data-View 都与策略一致（盘后菜单=DELAYED，学堂/更新=REALTIME）${BAD:+；不一致:$BAD}" '[ -z "$BAD" ] && [ $N -gt 10 ]'
req "$AD" GET /option/access/policy/6 >/dev/null; P6=$(j /tmp/rb 'd["data"]'); P6OFF=$(echo "$P6" | python3 -c 'import json,sys;d=json.load(sys.stdin);d["status"]="1";print(json.dumps(d))')
req "$AD" PUT /option/access/policy/6 "$P6OFF" >/dev/null; VR=$(view "$TR" /api/member/record); VA=$(view "$AD" /api/member/record)
req "$AD" PUT /option/access/policy/6 "$P6" >/dev/null
t A12b "策略里没登记的路径默认 DELAYED" "临时停用「战绩」策略：盘后版=$VR，管理员=$VA；已恢复" '[ "$VR" = "DELAYED" ] && [ "$VA" = "REALTIME" ]'

echo "===== 放出时间预览（3.2 示例、节假日、夏令时） ====="
pv 2026-10-20T10:00:00-04:00 'd' >/dev/null; R=$(j /tmp/rb 'd["data"]["dataTradeDate"]+" "+d["data"]["releasedAt"]+" "+d["data"]["nextReleaseAt"]')
t A04 "周二盘中（美东 10:00）" "$R（看到上周五数据）" '[ "$R" = "2026-10-16 2026-10-19T16:10:00-04:00 2026-10-20T16:10:00-04:00" ]'
pv 2026-10-24T12:00:00-04:00 'd' >/dev/null; R1=$(j /tmp/rb 'd["data"]["dataTradeDate"]+" "+d["data"]["releasedAt"]+" "+d["data"]["nextReleaseAt"]')
pv 2026-11-27T14:00:00-05:00 'd' >/dev/null; R2=$(j /tmp/rb 'd["data"]["dataTradeDate"]+" "+d["data"]["releasedAt"]+" "+d["data"]["nextReleaseAt"]')
pv 2026-11-26T12:00:00-05:00 'd' >/dev/null; R3=$(j /tmp/rb 'd["data"]["dataTradeDate"]+" "+str(d["data"]["tradingDay"])')
t A08 "周末 / 节假日 / 提前收盘日" "周六 12:00→$R1；感恩节次日 14:00→$R2；感恩节当天→$R3" \
  '[ "$R1" = "2026-10-22 2026-10-23T16:10:00-04:00 2026-10-26T16:10:00-04:00" ] && [ "$R2" = "2026-11-25 2026-11-27T13:10:00-05:00 2026-11-30T16:10:00-05:00" ] && [ "$R3" = "2026-11-24 False" ]'
pv 2026-10-30T17:00:00-04:00 'd' >/dev/null; D1=$(j /tmp/rb 'd["data"]["releasedAtBeijing"]')
pv 2026-10-31T12:00:00-04:00 'd' >/dev/null; D2=$(j /tmp/rb 'd["data"]["nextReleaseAtBeijing"]')
pv 2026-11-02T17:00:00-05:00 'd' >/dev/null; D3=$(j /tmp/rb 'd["data"]["releasedAtBeijing"]')
t A09 "夏令时切换周（2026-11-01 结束夏令时）" "周五 10-30 放出=北京 $D1；周六看下次放出=北京 $D2；周一 11-02 放出=北京 $D3" \
  '[ "$D1" = "2026-10-31 04:10:00" ] && [ "$D2" = "2026-11-03 05:10:00" ] && [ "$D3" = "2026-11-03 05:10:00" ]'
setcfg option.access.delayedLagSessions 0
R=$(pv 2026-10-20T16:30:00-04:00 'd["data"]["dataTradeDate"]+" "+d["data"]["releasedAt"]')
setcfg option.access.delayedLagSessions "$ORIG_LAG"
t A07 "delayedLagSessions 改为 0" "周二 16:30 看到 $R（当天收盘数据），已改回 $ORIG_LAG" '[ "$R" = "2026-10-20 2026-10-20T16:10:00-04:00" ]'

echo "===== 身份（第 11 节） ====="
settrial 9002 -258540 60; clearacc 9002
acc "$TE"; L1=$(j /tmp/rb 'd["data"]["level"]+"/"+d["data"]["view"]'); RS=$(j /tmp/rb 'd["data"]["trialRemainingSeconds"]'); TTL=$($RC ttl option:member:access:9002)
t A01a "注册后 71 小时 59 分访问" "身份=$L1，剩余 ${RS}s，身份缓存 TTL=${TTL}s（不超过剩余时长）" '[ "$L1" = "TRIAL/REALTIME" ] && [ "$TTL" -le 61 ]'
sleep $((TTL+2)); acc "$TE"; L2=$(j /tmp/rb 'd["data"]["level"]+"/"+d["data"]["view"]'); V2=$(view "$TE" /api/member/conduct)
t A01b "72 小时后访问（体验期自然到期，未清缓存）" "身份=$L2，进行菜单视图=$V2" '[ "$L2" = "DELAYED/DELAYED" ] && [ "$V2" = "DELAYED" ]'
settrial 9002 -3600 255600; clearacc 9002
DIFF=""; for p in $CORE; do for k in 1 2 3; do req "$TE" GET $p >/dev/null; a=$(rsha); v=$(hdr X-Option-Data-View); req "$AD" GET $p >/dev/null; [ "$a" = "$(rsha)" ] && break; done; [ "$a" = "$(rsha)" ] && [ "$v" = "REALTIME" ] || DIFF="$DIFF $p"; done
t A02 "注册后立即访问 6 个核心菜单" "体验期会员 view=REALTIME，6 个菜单响应与管理员逐字节一致${DIFF:+；不一致:$DIFF}" '[ -z "$DIFF" ]'
sql "update option_member_access set trial_end_time=utc_timestamp()+interval 8 hour-interval 1 minute, trial_end_time_utc=utc_timestamp()-interval 1 minute where user_id=9002"; clearacc 9002
V3=$(view "$TE" /api/member/conduct)
t A03 "把 trial_end_time 改到过去" "改库并清身份缓存后，下一次请求视图=$V3（后台接口修改会自动清缓存，见 B03）" '[ "$V3" = "DELAYED" ]'
settrial 9002 -3600 255600; clearacc 9002

echo "===== 付费（9.6） ====="
req "$AD" PUT /option/member/9001/paid "{\"paidUntil\":\"$(sql "select date_format(utc_timestamp()+interval 8 hour+interval 1 day,'%Y-%m-%d %H:%i:%s')")\"}" >/dev/null
acc "$TR"; P1=$(j /tmp/rb 'd["data"]["level"]'); PV1=$(view "$TR" /api/member/conduct)
req "$AD" PUT /option/member/9001/paid '{"paidUntil":null}' >/dev/null; acc "$TR"; P2=$(j /tmp/rb 'd["data"]["level"]'); PV2=$(view "$TR" /api/member/conduct)
t C03 "设置 / 取消付费" "设置后 $P1/$PV1，取消后 $P2/$PV2" '[ "$P1/$PV1/$P2/$PV2" = "PAID/REALTIME/DELAYED/DELAYED" ]'

echo "===== 隐私与安全（第 11 节、步骤 2、步骤 9） ====="
req "$TP" POST /option/session/login '{"email":"x@example.com","password":"x"}' >/dev/null
t A15 "会员调用 POST /option/session/login（同步账号登录，原 /api/member/login 已删除）" "返回 code=$(code)（$(msg)）" '[ "$(code)" = "403" ]'
SYNC_NAME=$(sql "select json_unquote(json_extract(payload,'$.sharer.name')) from option_info_referral order by version_id desc limit 1")
SYNC_CODE=$(sql "select json_unquote(json_extract(payload,'$.referral.code')) from option_info_referral order by version_id desc limit 1")
req "$TE" GET /api/member/referrals >/dev/null; REF=$(j /tmp/rb 'd.get("data")')
LEAK=""; for u in "$TR" "$TE"; do for p in $(j /tmp/pc.json '" ".join(i["path"] for i in d["data"]["items"] if "GET" in str(i.get("methods") or i.get("method")) and "{" not in i["path"])'); do
  req "$u" GET "$p" >/dev/null; grep -qF -e "$SYNC_CODE" -e "$SYNC_NAME" /tmp/rb && LEAK="$LEAK $p"; done; done
t A14 "GET /api/member/referrals 不含同步账号推荐码" "referrals 返回 $REF；体验期 / 盘后版会员遍历全部 GET 接口均不含同步账号推荐码与用户名${LEAK:+；泄露:$LEAK}" '[ -n "$SYNC_CODE" ] && [ -z "$LEAK" ]'
req "$TP" GET /system/user/list >/dev/null
t C04 "会员调用后台接口" "/system/user/list 返回 code=$(code)" '[ "$(code)" = "403" ]'

echo "===== 注册与后台管理（9.7） ====="
req "" POST /api/member/register/code "{\"email\":\"$REG_EMAIL\"}" -H "X-Forwarded-For: $REG_IP" >/dev/null; SC=$(code)
python3 - "$REG_EMAIL" <<'PY'
import sys,subprocess,hashlib,re
e=sys.argv[1]; k="option:member:code:"+e
v=subprocess.run(["redis6-cli","get",k],capture_output=True,text=True).stdout.strip()
h=hashlib.sha256((e+":246810").encode()).hexdigest()
subprocess.run(["redis6-cli","set",k,re.sub(r"[0-9a-f]{64}",h,v),"KEEPTTL"],capture_output=True)
PY
req "" POST /api/member/register "{\"email\":\"$REG_EMAIL\",\"password\":\"Regress123\",\"emailCode\":\"246810\"}" -H "X-Forwarded-For: $REG_IP" >/dev/null
NT=$(j /tmp/rb 'd.get("token") or (d.get("data") or {}).get("token") or ""')
NEW_ID=$(sql "select user_id from option_member_access where email='$REG_EMAIL'")
req "$AD" GET "/option/member/list?email=$REG_EMAIL" >/dev/null; LV=$(j /tmp/rb 'd["rows"][0]["level"]'); RSEC=$(j /tmp/rb 'd["rows"][0]["trialRemainingSeconds"]'); TE0=$(j /tmp/rb 'd["rows"][0]["trialEndTime"]')
req "$AD" GET "/option/member/registerLog/list?email=$REG_EMAIL" >/dev/null; RL=$(j /tmp/rb '",".join(r["action"]+":"+r["result"] for r in d["rows"])')
t B01 "网站注册一个新会员" "发码 code=$SC（邮件真实发送）；注册拿到 token；后台列表立即出现 userId=$NEW_ID，身份=$LV，剩余 $(( RSEC/3600 )) 小时；注册日志=[$RL]" \
  '[ -n "$NT" ] && [ "$LV" = "TRIAL" ] && [ "$RSEC" -gt 259000 ] && [ "$(echo $RL | tr , "\n" | sort | tr "\n" " ")" = "REGISTER:SUCCESS SEND_CODE:SUCCESS " ]'
req "$NT" PUT /api/member/profile '{"strongAlerts":true,"updatedAt":null}' >/dev/null; U1=$(j /tmp/rb 'd["data"]["profile"]["updatedAt"]'); sleep 1.2
req "$NT" PUT /api/member/profile "{\"strongAlerts\":false,\"updatedAt\":\"$U1\"}" >/dev/null; C1=$(code)
req "$NT" PUT /api/member/profile "{\"strongAlerts\":true,\"updatedAt\":\"$U1\"}" >/dev/null; C2=$(code); M2=$(msg)
t A13 "设置并发保存" "两个窗口读到同一个 updatedAt=$U1：第一次保存 code=$C1，第二次带旧 updatedAt code=$C2（$M2）" '[ "$C1" = 200 ] && [ "$C2" = 409 ]'
req "$AD" PUT "/option/member/$NEW_ID/trial" '{"extendHours":24,"remark":"回归测试"}' >/dev/null
req "$AD" GET "/option/member/list?email=$REG_EMAIL" >/dev/null; TE1=$(j /tmp/rb 'd["rows"][0]["trialEndTime"]')
acc "$NT"; RS2=$(j /tmp/rb 'd["data"]["trialRemainingSeconds"]'); OL=$(sql "select count(*) from sys_oper_log where title='会员管理' and oper_url='/option/member/$NEW_ID/trial' and oper_id>$OPER0")
DH=$(python3 -c "from datetime import datetime as D;f='%Y-%m-%d %H:%M:%S';print(int((D.strptime('$TE1',f)-D.strptime('$TE0',f)).total_seconds()/3600))")
t B02 "后台延长 24 小时" "到期 $TE0 → $TE1（+${DH}h）；会员端剩余约 $(( RS2/3600 )) 小时；操作日志 $OL 条" '[ "$DH" = 24 ] && [ "$RS2" -gt 345000 ] && [ "$OL" -ge 1 ]'
V0=$(view "$NT" /api/member/conduct); req "$AD" PUT "/option/member/$NEW_ID/trial" '{"extendHours":-100}' >/dev/null; V1=$(view "$NT" /api/member/conduct)
t B03 "把体验到期时间缩短到过去" "会员端视图 $V0 → 下一次请求 $V1" '[ "$V0" = REALTIME ] && [ "$V1" = DELAYED ]'
tok "$REG_EMAIL" Regress123 -H 'X-Client-Type: admin' >/dev/null; M5=$(j /tmp/lb 'd.get("msg")')
LG=$(sql "select msg from sys_logininfor where user_name='$REG_EMAIL' order by info_id desc limit 1")
AT=$(tok admin "$PWD0" -H 'X-Client-Type: admin')
t B05 "会员账号登录 ruoyi-vue3（带 X-Client-Type: admin）" "提示「$M5」，登录日志「$LG」；admin 带同样请求头正常登录" '[ "$M5" = "该账号无后台权限" ] && [ -n "$AT" ]'
req "$AD" PUT "/option/member/$NEW_ID/status" '{"status":"1"}' >/dev/null; req "$NT" GET /api/member/access >/dev/null; C4=$(code)
NT2=$(tok "$REG_EMAIL" Regress123); M4=$(j /tmp/lb 'd.get("msg")')
t B04 "后台停用会员" "会员端下一次请求 code=$C4；再登录提示「$M4」（若依默认文案，文档写「账号已停用」）" '[ "$C4" = 401 ] && [ -z "$NT2" ] && echo "$M4" | grep -qE "封禁|停用"'
req "$AD" PUT "/option/member/$NEW_ID/status" '{"status":"0"}' >/dev/null
for i in 1 2; do sql "insert into option_member_register_log(email,action,result,ip,create_time,create_time_utc) values('regress-$i@example.com','REGISTER','SUCCESS','$REG_IP',utc_timestamp()+interval 8 hour,utc_timestamp())"; done
req "" POST /api/member/register '{"email":"regress-4@example.com","password":"Regress123","emailCode":"000000"}' -H "X-Forwarded-For: $REG_IP" >/dev/null; C16=$(code); M16=$(msg)
RJ=$(sql "select concat(result,':',reason) from option_member_register_log where email='regress-4@example.com' order by log_id desc limit 1")
t A16 "同一 IP 当天第 4 次注册" "已有 3 次成功注册（1 次真实 + 2 条造数）；第 4 次 code=$C16（$M16），风控日志=$RJ" '[ "$C16" = 429 ] && [ "$RJ" = "REJECTED:IP 当天注册超限" ]'
OK6=1; for x in "GET /option/member/list" "GET /option/member/stats" "PUT /option/member/$NEW_ID/trial" "PUT /option/member/$NEW_ID/status" "PUT /option/member/$NEW_ID/paid" "PUT /option/member/$NEW_ID/resetPwd" "DELETE /option/member/$NEW_ID/online"; do
  req "$RY" ${x% *} ${x#* } '{"extendHours":1,"status":"1","paidUntil":null,"password":"Abcd12345"}' >/dev/null; [ "$(code)" = 403 ] || OK6=0; done
t B06 "没有 option:member:* 权限的后台账号（ry）" "列表、统计、延长、停用、付费、重置密码、强制下线 7 个接口都返回 403" '[ "$OK6" = 1 ]'
req "$AD" GET "/option/member/list?level=DELAYED&pageSize=500" >/dev/null; LT=$(j /tmp/rb 'd["total"]'); LIDS=$(j /tmp/rb '",".join(sorted(str(r["userId"]) for r in d["rows"]))')
curl -s -X POST -H "Authorization: Bearer $AD" -d 'level=DELAYED' -o /tmp/regress.xlsx "$B/option/member/export"
EX=$(python3 -c "
import openpyxl,warnings;warnings.simplefilter('ignore');ws=openpyxl.load_workbook('/tmp/regress.xlsx').active;rows=list(ws.iter_rows(values_only=True))
print('|'.join(rows[0]));print(','.join(sorted(str(int(r[0])) for r in rows[1:])))")
EH=$(echo "$EX" | head -1); EIDS=$(echo "$EX" | tail -1)
t B07 "导出" "筛选 level=DELAYED：列表 $LT 条 [$LIDS]，Excel [$EIDS]；表头：$EH" '[ "$LIDS" = "$EIDS" ] && [ "$EH" = "用户ID|邮箱|昵称|身份|状态|注册时间|体验到期|体验剩余秒数|累计调整小时|付费到期|最近访问|注册IP|在线" ]'
req "$AD" GET "/system/user/list?pageSize=500" >/dev/null; U1=$(j /tmp/rb 'sum(1 for r in d["rows"] if r["userName"].startswith("m_") or "@" in r["userName"])')
req "$AD" GET "/system/user/list?pageSize=500&params%5BshowMember%5D=true" >/dev/null; U2=$(j /tmp/rb 'sum(1 for r in d["rows"] if r["userName"].startswith("m_") or "@" in r["userName"])')
t B08 "「用户管理」默认列表" "默认显示会员 $U1 个；params[showMember]=true 后显示 $U2 个" '[ "$U1" = 0 ] && [ "$U2" -ge 5 ]'
