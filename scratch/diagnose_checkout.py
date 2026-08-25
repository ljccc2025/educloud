import os
import sys
from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding='utf-8')
STUDENT_PORTAL = "http://192.168.100.136:5173"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 900})
    page = context.new_page()
    
    # Login
    page.goto(f"{STUDENT_PORTAL}/login", wait_until="networkidle")
    page.locator("input[name='loginName'], input[placeholder*='用户名'], input[placeholder*='账号'], input[name='username'], input[type='text']").first.fill("fe_demo_10")
    page.locator("input[type='password']").first.fill("FeDemo@2026")
    page.locator("button[type='submit'], button:has-text('登录')").first.click()
    page.wait_for_timeout(2000)
    
    # Go to Checkout for course 9000000000000000114 SQL
    print("\n--- Navigating to Checkout ---")
    page.goto(f"{STUDENT_PORTAL}/checkout?courseId=9000000000000000114", wait_until="networkidle")
    page.wait_for_timeout(3000)
    
    # Print buttons
    buttons = page.locator("button").all()
    print(f"Total buttons found: {len(buttons)}")
    for i, b in enumerate(buttons):
        print(f"Button {i}: text='{b.text_content().strip()}', class='{b.get_attribute('class')}'")
        
    print("\nMain text content:")
    print(page.locator("main").inner_text()[:600])
    
    browser.close()
