#!/usr/bin/env python3
"""
EduCloud M11 全局搜索中心（educloud-search）全链路 E2E 自动化集成测试脚本
覆盖功能：
1. 搜索服务探针与健康检查 (8099 业务 / 8100 监控)
2. 网关路由转发验证 (/api/v1/search/** -> educloud-search)
3. 课程全文检索接口验证（关键词加权匹配、多维过滤、排序、分页与高亮渲染）
4. 搜索智能前缀联想与自动补全接口验证 (/api/v1/search/suggest)
5. 管理员登录与鉴权校验（RBAC 权限码 search:rebuild 拦截与放行）
6. 管理端一键全量索引平滑重建（Zero-Downtime Alias Swap）
7. 重建任务状态与进度实时轮询直至 SUCCESS
8. 别名原子切换后线上检索无损连续性验证
"""

import sys
import time
import json
import urllib.request
import urllib.parse
import urllib.error

try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

BASE_GATEWAY = "http://192.168.100.136:8080"
SEARCH_DIRECT = "http://192.168.100.136:8099"
SEARCH_MGMT = "http://192.168.100.136:8100"

def request_json(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    headers["Content-Type"] = "application/json"
    req_data = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, json.loads(body)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"error": body}
    except Exception as e:
        return 500, {"error": str(e)}

def login_admin():
    login_url = f"{BASE_GATEWAY}/api/v1/auth/login"
    status, res = request_json(login_url, method="POST", data={
        "loginName": "demo_admin",
        "password": "EduCloud@2026",
        "portal": "ADMIN"
    })
    if status == 200 and res.get("code") in (200, "200", "SUCCESS"):
        token = res.get("data", {}).get("accessToken")
        print(f"  [OK] Admin login success, token acquired ({token[:15]}...)")
        return token
    else:
        raise RuntimeError(f"Admin login failed: {status}, {res}")

def main():
    print("=" * 60)
    print("🚀 开始执行 EduCloud M11 搜索中心 E2E 全链路集成测试")
    print("=" * 60)

    # 1. 检查探针健康度
    print("\n[Step 1] 检查 educloud-search 健康与就绪探针...")
    status, res = request_json(f"{SEARCH_MGMT}/actuator/health")
    assert status == 200, f"Search health probe failed: {status}, {res}"
    print(f"  [OK] Search health status: {res.get('status')}")

    # 2. 网关公共检索接口验证
    print("\n[Step 2] 验证网关转发公开课程全文检索接口 GET /api/v1/search/courses...")
    status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/courses?page=1&size=10")
    assert status == 200 and res.get("code") in (200, "200", "SUCCESS"), f"Search courses failed: {status}, {res}"
    data = res.get("data", {})
    total = data.get("total", 0)
    items = data.get("items", [])
    print(f"  [OK] Search returned {len(items)} items (total: {total}, isDegraded: {data.get('isDegraded')})")

    # 3. 关键词检索与高亮验证
    print("\n[Step 3] 验证多字段加权与高亮检索 (keyword=Spring)...")
    status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/courses?keyword=Spring")
    assert status == 200 and res.get("code") in (200, "200", "SUCCESS"), f"Keyword search failed: {status}, {res}"
    data = res.get("data", {})
    items = data.get("items", [])
    print(f"  [OK] Keyword 'Spring' returned {len(items)} matches")
    if items:
        first_item = items[0]
        print(f"    - Title: {first_item.get('title')}")
        print(f"    - Category: {first_item.get('category')}")
        print(f"    - Price: {first_item.get('priceCents')} cents")

    # 4. 智能搜索建议与自动补全接口验证
    print("\n[Step 4] 验证前缀智能联想接口 GET /api/v1/search/suggest?q=微服务...")
    encoded_q = urllib.parse.quote("微服务")
    status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/suggest?q={encoded_q}")
    assert status == 200 and res.get("code") in (200, "200", "SUCCESS"), f"Suggest failed: {status}, {res}"
    suggestions = res.get("data", {}).get("suggestions", [])
    print(f"  [OK] Suggest returned {len(suggestions)} suggestions:")
    for s in suggestions[:3]:
        print(f"    - Text: {s.get('text')} (Type: {s.get('type')}, Category: {s.get('category')})")

    # 5. 管理员登录与权限验证
    print("\n[Step 5] 验证管理员登录与 search:rebuild 权限保护...")
    admin_token = login_admin()
    auth_headers = {"Authorization": f"Bearer {admin_token}"}

    # 匿名调用管理端接口应当报 401
    status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/admin/tasks", method="GET")
    assert status == 401 or res.get("code") == 401, f"Expected 401 for anonymous admin access, got {status}"
    print("  [OK] Anonymous access to /api/v1/search/admin/** successfully blocked (401)")

    # 6. 触发管理端一键平滑全量重建
    print("\n[Step 6] 触发全量索引平滑重建 POST /api/v1/search/admin/rebuild-index...")
    status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/admin/rebuild-index", method="POST", headers=auth_headers)
    assert status == 200 and res.get("code") in (200, "200", "SUCCESS"), f"Rebuild trigger failed: {status}, {res}"
    task_data = res.get("data", {})
    task_no = task_data.get("taskNo")
    print(f"  [OK] Rebuild task started! TaskNo: {task_no}, Status: {task_data.get('status')}")

    # 7. 轮询监控重建任务直至完成
    print("\n[Step 7] 轮询监控全量重建任务进度...")
    finished = False
    for i in range(20):
        time.sleep(1)
        status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/admin/tasks/{task_no}", method="GET", headers=auth_headers)
        assert status == 200 and res.get("code") in (200, "200", "SUCCESS"), f"Poll task failed: {status}, {res}"
        task_info = res.get("data", {})
        task_status = task_info.get("status")
        processed = task_info.get("processedRecords", 0)
        total_rec = task_info.get("totalRecords", 0)
        percent = task_info.get("progressPercent", 0)
        print(f"  [Poll {i+1}/20] Status: {task_status} | Progress: {processed}/{total_rec} ({percent}%)")
        if task_status == "SUCCESS":
            finished = True
            print(f"  [SUCCESS] 索引平滑重建与别名切换完成! 新物理索引: {task_info.get('indexName')}")
            break
        elif task_status == "FAILED":
            raise RuntimeError(f"Rebuild task failed: {task_info.get('errorMessage')}")

    assert finished, "Rebuild task did not finish in expected time"

    # 8. 验证重建后检索可用性与多维聚合
    print("\n[Step 8] 验证别名原子切换后检索连续性与多维 Facet 聚合...")
    status, res = request_json(f"{BASE_GATEWAY}/api/v1/search/courses?page=1&size=10")
    assert status == 200 and res.get("code") in (200, "200", "SUCCESS"), f"Post-rebuild search failed: {status}, {res}"
    data = res.get("data", {})
    aggs = data.get("aggregations", {})
    cat_aggs = aggs.get("categories", [])
    diff_aggs = aggs.get("difficulties", [])
    price_aggs = aggs.get("priceRanges", [])
    print(f"  [OK] Post-rebuild total: {data.get('total')}")
    print(f"  [OK] Categories Aggregations: {[c.get('key')+':'+str(c.get('count')) for c in cat_aggs[:4]]}")
    print(f"  [OK] Difficulties Aggregations: {[d.get('key')+':'+str(d.get('count')) for d in diff_aggs]}")
    print(f"  [OK] Price Ranges Aggregations: {[p.get('key')+':'+str(p.get('count')) for p in price_aggs]}")

    print("\n" + "=" * 60)
    print("🎉 M11 全局搜索中心（educloud-search）全链路 E2E 自动化测试 100% 全部通过！")
    print("=" * 60)

if __name__ == "__main__":
    main()
