"""API 层安全修复验证 v2（修正管理端接口路径/方法）。"""
import json
import urllib.request
import urllib.error

BASE = "http://192.168.100.136:8080"


def call(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
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
        return data["data"]["accessToken"]
    return None


results = []
student_token = login("fe_demo_10", "FeDemo@2026", "STUDENT")
admin_token = login("demo_admin", "EduCloud@2026", "ADMIN")
results.append(("登录学生/管理员", bool(student_token and admin_token)))

# ---- BUG-034：管理员锁定 → 登录拒绝 → 解锁恢复 ----
st, data = call("GET", "/api/v1/users?page=1&pageSize=100", token=admin_token)
target = None
items = (data.get("data") or {}).get("items", []) if st == 200 else []
for u in items:
    if "fe_demo_10" in json.dumps(u):
        target = u
        break
if target:
    uid, ver = target.get("id"), target.get("version", 0)
    st, d = call("PATCH", f"/api/v1/users/{uid}/status", token=admin_token,
                 body={"status": "LOCKED", "version": ver, "reason": "e2e-lock-test"})
    results.append(("BUG-034 管理员锁定操作 (HTTP %s)" % st, st == 200))
    locked_token = login("fe_demo_10", "FeDemo@2026", "STUDENT")
    results.append(("BUG-034 锁定后登录被拒绝", locked_token is None))
    st, d = call("GET", f"/api/v1/users/{uid}", token=admin_token)
    ver2 = (d.get("data") or {}).get("version", ver + 1) if st == 200 else ver + 1
    st, d = call("PATCH", f"/api/v1/users/{uid}/status", token=admin_token,
                 body={"status": "ACTIVE", "version": ver2, "reason": "e2e-unlock"})
    unlock_token = login("fe_demo_10", "FeDemo@2026", "STUDENT")
    results.append(("BUG-034 解锁后登录恢复", unlock_token is not None))
else:
    results.append(("BUG-034 跳过（用户列表无 fe_demo_10，HTTP %s）" % st, None))

# ---- BUG-002：付费课件下载鉴权（课件 ID 由 VM 查询后传入命令行参数） ----
import sys
cw_paid = sys.argv[1] if len(sys.argv) > 1 else None
cw_free = sys.argv[2] if len(sys.argv) > 2 else None
if cw_paid:
    st1, d1 = call("GET", f"/api/v1/coursewares/{cw_paid}/download-url")
    results.append(("BUG-002 匿名下载付费课件 (HTTP %s)" % st1, st1 == 403))
    st2, d2 = call("GET", f"/api/v1/coursewares/{cw_paid}/download-url", token=student_token)
    results.append(("BUG-002 未报名用户下载付费课件 (HTTP %s)" % st2, st2 == 403))
if cw_free:
    st3, d3 = call("GET", f"/api/v1/coursewares/{cw_free}/download-url")
    ok3 = st3 == 200 and (d3.get("data") or {}).get("downloadUrl")
    results.append(("BUG-002 匿名下载免费预览课件放行 (HTTP %s)" % st3, bool(ok3)))

print(json.dumps(results, ensure_ascii=False, indent=1))
