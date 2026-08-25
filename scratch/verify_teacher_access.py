"""BUG-005 教师横向越权验证：教师只能操作自己归属课程的内容。"""
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


def login(name, pwd, portal):
    st, d = call("POST", "/api/v1/auth/login", body={"loginName": name, "password": pwd, "portal": portal})
    return d["data"]["accessToken"] if st == 200 and d.get("code") == "SUCCESS" else None


teacher = login("demo_teacher", "EduCloud@2026", "TEACHER")
student = login("fe_demo_10", "FeDemo@2026", "STUDENT")
print("teacher token:", bool(teacher), "| student token:", bool(student))

# 1. demo_teacher 读取自己归属课程的草稿（GET /content-draft）→ 应 200（课程 110 归属 9000000000000000001）
st1, d1 = call("GET", "/api/v1/teacher/courses/9000000000000000110/content-draft", token=teacher)
print("BUG-005 教师读本人课程草稿 (HTTP %s code=%s)" % (st1, d1.get("code")))

# 2. 学生 token 冒充教师读取教师草稿（归属校验）→ 应 401/403/404 而非 200/500/503
st2, d2 = call("GET", "/api/v1/teacher/courses/9000000000000000110/content-draft", token=student)
print("BUG-005 学生 token 调教师端点 (HTTP %s code=%s)" % (st2, d2.get("code")))

# 3. 无 token → 401
st3, d3 = call("GET", "/api/v1/teacher/courses/9000000000000000110/content-draft")
print("BUG-001 匿名调教师端点 (HTTP %s)" % st3)
