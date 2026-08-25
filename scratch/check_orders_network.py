import sys
from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding='utf-8')
STUDENT_PORTAL = "http://192.168.100.136:5173"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    
    page.on("request", lambda req: print(f"[REQ] {req.method} {req.url}"))
    page.on("response", lambda resp: print(f"[RESP] {resp.status} {resp.url}"))
    
    # Login
    page.goto(f"{STUDENT_PORTAL}/login", wait_until="networkidle")
    page.locator("input[placeholder*='用户名'], input[placeholder*='账号'], input[name='username'], input[type='text']").first.fill("fe_demo_10")
    page.locator("input[type='password']").first.fill("FeDemo@2026")
    page.locator("button[type='submit'], button:has-text('登录')").first.click()
    page.wait_for_timeout(2000)
    
    # Orders
    print("\n--- Navigating to /orders ---")
    page.goto(f"{STUDENT_PORTAL}/orders", wait_until="networkidle")
    page.wait_for_timeout(3000)
    
    print("\nPage text:")
    print(page.locator("main, body").first.inner_text()[:600])
    
    browser.close()
