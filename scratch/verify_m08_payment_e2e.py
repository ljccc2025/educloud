"""M08 支付中心全链路 API 验证：
1. 收银台创建支付单（MOCK）→ mock-confirm → 支付单 SUCCESS
2. Outbox → order PAID → OrderPaidEvent → course 自动开课
3. 安全验证：学生调管理端退款/对账端点 → 403（@PreAuthorize 修复）
4. 管理员调退款列表 → 200（权限码生效）
5. 回调金额篡改 → 拒绝
"""
import json
import time
import urllib.request
import urllib.error

BASE = "http://192.168.100.136:8080"
RESULTS = []


def call(method, path, token=None, body=None, raw_body=None):
    url = BASE + path
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    elif raw_body is not None:
        data = raw_body.encode()
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:
        return -1, {"error": str(e)}


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + ("  " + str(detail)[:160] if detail else ""))


def login(name, pwd, portal):
    st, d = call("POST", "/api/v1/auth/login", body={"loginName": name, "password": pwd, "portal": portal})
    if st == 200 and d.get("code") == "SUCCESS":
        return d["data"]["accessToken"]
    return None


student = login("fe_demo_10", "FeDemo@2026", "STUDENT")
admin = login("demo_admin", "EduCloud@2026", "ADMIN")
check("学生/管理员登录", bool(student) and bool(admin))

# ---- 1. 找一门在售付费课程并创建订单 ----
st, d = call("GET", "/api/v1/courses?page=1&pageSize=50", token=student)
courses = d.get("data", {}).get("items", []) if isinstance(d.get("data"), dict) else []
target = None
for c in courses:
    price = c.get("price") or c.get("priceValue")
    try:
        on_sale = bool(c.get("onSale", c.get("published", True)))
    except Exception:
        on_sale = True
    if c.get("enrolled") is True:
        continue  # 已报名课程下单会被拒（COURSE_NOT_ON_SALE），跳过
    if price and float(price) > 0 and on_sale:
        target = c
        break
if target is None:
    check("发现在售付费课程", False, "课程列表为空或无付费课程")
    raise SystemExit(1)
course_id = target.get("id") or target.get("courseId")
check("发现在售付费课程", True, f"courseId={course_id} price={target.get('price')}")

st, d = call("GET", "/api/v1/orders/idempotency-token", token=student)
idem = d.get("data", {}).get("token") if isinstance(d.get("data"), dict) else d.get("data")
# 载荷修正：OrderCreateRequest 只有 courseId + idempotencyToken 两个字段，
# 传 items 数组会被忽略并误入购物车结算分支（历史误报 COURSE_NOT_ON_SALE 的根因）
st2, d2 = call("POST", "/api/v1/orders", token=student, body={
    "idempotencyToken": idem,
    "courseId": int(course_id),
})
order_id = d2.get("data", {}).get("id") if isinstance(d2.get("data"), dict) else None
check("创建待支付订单", st2 == 200 and order_id is not None, f"http={st2} code={d2.get('code')} orderId={order_id}")
if order_id is None:
    raise SystemExit(1)

# ---- 2. 支付中心：收银台 + 模拟确认 ----
st, d = call("POST", "/api/v1/payments/cashier", token=student,
             body={"orderId": order_id, "channelCode": "MOCK", "tradeType": "NATIVE"})
pay = d.get("data") if isinstance(d.get("data"), dict) else {}
payment_order_id = pay.get("paymentOrderId")
check("收银台创建支付单", st == 200 and payment_order_id, f"http={st} code={d.get('code')} amount={pay.get('amountCents')}")

st, d = call("POST", f"/api/v1/payments/{payment_order_id}/mock-confirm", token=student)
check("模拟支付确认", st == 200 and d.get("data", {}).get("status") == "SUCCESS",
      f"http={st} code={d.get('code')} status={d.get('data', {}).get('status')}")

# ---- 3. Outbox 异步履约：订单置 PAID + 自动开课 ----
order_paid, enrolled = False, False
for _ in range(12):
    time.sleep(3)
    st, d = call("GET", f"/api/v1/orders/{order_id}", token=student)
    od = d.get("data") if isinstance(d.get("data"), dict) else {}
    if od.get("status") == "PAID":
        order_paid = True
        break
check("订单异步置为 PAID", order_paid, f"最终状态={od.get('status') if isinstance(od, dict) else '?'}")

for _ in range(10):
    st, d = call("GET", f"/api/v1/courses/{course_id}", token=student)
    cd = d.get("data") if isinstance(d.get("data"), dict) else {}
    if cd.get("enrolled") is True:
        enrolled = True
        break
    time.sleep(3)
check("支付成功自动开课", enrolled, f"enrolled={cd.get('enrolled') if isinstance(cd, dict) else '?'}")

# ---- 4. 安全：学生调管理端点应 403 ----
st, d = call("GET", "/api/v1/admin/payments/refunds?page=1&size=5", token=student)
check("学生调退款管理端点被拒(403)", st == 403, f"http={st} code={d.get('code')}")

st, d = call("POST", "/api/v1/admin/payments/reconciliation/trigger", token=student,
             body={"reconcileDate": "2026-08-25", "channelCode": "MOCK"})
check("学生触发对账被拒(403)", st == 403, f"http={st} code={d.get('code')}")

# ---- 5. 管理员权限生效 ----
st, d = call("GET", "/api/v1/admin/payments/refunds?page=1&size=5", token=admin)
check("管理员查退款列表(200)", st == 200, f"http={st} code={d.get('code')}")

st, d = call("POST", "/api/v1/admin/payments/reconciliation/trigger", token=admin,
             body={"reconcileDate": "2026-08-25", "channelCode": "MOCK"})
check("管理员触发对账(200)", st == 200, f"http={st} code={d.get('code')} batch={d.get('data', {}).get('status')}")

# ---- 6. 回调金额篡改拒绝 ----
forged = json.dumps({"paymentOrderId": payment_order_id, "amountCents": 1, "notifyId": "FORGE_001"})
st, d = call("POST", "/api/v1/payment-callbacks/MOCK", raw_body=forged)
check("回调金额篡改被拒", st in (400, 403, 500) and d.get("code") in ("AMOUNT_MISMATCH", "SIGN_VERIFY_FAILED"),
      f"http={st} code={d.get('code')}")

# ---- 7. 支付单详情归属校验（IDOR）----
st, d = call("GET", f"/api/v1/payments/{payment_order_id}", token=admin)
check("他人支付单详情被拒(403)", st == 403, f"http={st} code={d.get('code')}")

fails = [r for r in RESULTS if not r[1]]
print("\n===== 结果汇总 =====")
print(f"{len(RESULTS) - len(fails)}/{len(RESULTS)} 通过")
for name, ok, detail in fails:
    print("FAIL:", name, detail)
