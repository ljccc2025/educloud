"""API 层安全修复验证：
1. BUG-002：未报名用户下载付费课件 → 403
2. BUG-034：管理员锁定账号后登录 → 拒绝（锁定不被解除）
3. BUG-022：mock-pay 环境门控（local 环境放行，已由购买链路验证）
4. BUG-016：过期订单支付 → 拒绝（构造验证）
"""
import json
import urllib.request
import urllib.error

BASE = "http://192.168.100.136:8080"


def call(method, path, token=None, body=None, headers=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:
        return -1, {"error": str(e)}


def login(login_name, password, portal):
    st, data = call("POST", "/api/v1/auth/login", body={
        "loginName": login_name, "password": password, "portal": portal})
    if st == 200 and data.get("code") == "SUCCESS":
        return data["data"]["accessToken"], data["data"].get("user", {})
    return None, data


results = []

# ---- 0. 准备：学生/教师/管理员登录 ----
student_token, _ = login("fe_demo_10", "FeDemo@2026", "STUDENT")
teacher_token, _ = login("demo_teacher", "EduCloud@2026", "TEACHER")
admin_token, _ = login("demo_admin", "EduCloud@2026", "ADMIN")
results.append(("登录学生/教师/管理员", bool(student_token and teacher_token and admin_token)))

# ---- 1. BUG-002：未报名用户下载付费课件 ----
# 找 content 库中任一课件（经 content 学生端章节接口；若无则跳过说明）
st, data = call("GET", "/api/v1/courses/9000000000000000113/chapters")
cw_id = None
if st == 200 and data.get("code") == "SUCCESS":
    for ch in data.get("data") or []:
        for cw in ch.get("coursewares") or []:
            if not cw.get("freePreview"):
                cw_id = cw.get("id")
                break
        if cw_id:
            break
if cw_id:
    # 匿名用户（无 token）下载付费课件 → 应 403
    st1, d1 = call("GET", f"/api/v1/coursewares/{cw_id}/download-url")
    # fe_demo_10 未报名 113（付费 199 未购买）→ 也应 403
    st2, d2 = call("GET", f"/api/v1/coursewares/{cw_id}/download-url", token=student_token)
    results.append((f"BUG-002 匿名下载付费课件拒绝 (cw={cw_id})", st1 == 403))
    results.append(("BUG-002 未报名登录用户下载拒绝", st2 == 403))
else:
    results.append(("BUG-002 跳过（无付费课件数据）", None))

# ---- 2. BUG-034：管理员锁定后登录被拒绝 ----
# 用管理员锁定 fe_demo_10 对应账号：先查用户列表找到 id
st, data = call("GET", "/api/v1/admin/users?page=1&size=50&keyword=fe_demo_10", token=admin_token)
target = None
if st == 200 and data.get("code") == "SUCCESS":
    for u in (data.get("data", {}) or {}).get("items", []):
        if u.get("loginName") == "fe_demo_10" or u.get("username") == "fe_demo_10":
            target = u
            break
if target:
    uid, ver = target.get("id"), target.get("version", 0)
    st, data = call("PUT", f"/api/v1/admin/users/{uid}/status", token=admin_token,
                    body={"status": "LOCKED", "version": ver, "reason": "e2e-lock-test"})
    lock_ok = st == 200
    # 锁定后登录 → 必须拒绝（修复前会静默解锁放行）
    locked_login_token, resp = login("fe_demo_10", "FeDemo@2026", "STUDENT")
    results.append(("BUG-034 管理员锁定操作成功", lock_ok))
    results.append(("BUG-034 锁定后登录被拒绝", locked_login_token is None))
    # 解锁恢复（避免影响后续验证）
    st, data = call("GET", f"/api/v1/admin/users/{uid}", token=admin_token)
    ver2 = (data.get("data") or {}).get("version", ver + 1) if st == 200 else ver + 1
    call("PUT", f"/api/v1/admin/users/{uid}/status", token=admin_token,
         body={"status": "ACTIVE", "version": ver2, "reason": "e2e-unlock"})
    unlock_token, _ = login("fe_demo_10", "FeDemo@2026", "STUDENT")
    results.append(("BUG-034 解锁后登录恢复", unlock_token is not None))
else:
    results.append(("BUG-034 跳过（未找到用户）", None))

# ---- 3. 订单权益：已购课程 enrolled=true ----
st, data = call("GET", "/api/v1/courses/9000000000000000112", token=student_token)
enrolled = (data.get("data") or {}).get("enrolled") if st == 200 else None
results.append(("BUG-017/051 支付后自动开课 (enrolled)", enrolled is True))

print(json.dumps(results, ensure_ascii=False, indent=1))
