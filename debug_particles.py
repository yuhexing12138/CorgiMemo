from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)  # 使用非无头模式
    page = browser.new_page(viewport={"width": 1440, "height": 900})

    # 收集控制台日志
    logs = []
    page.on("console", lambda msg: logs.append(f"[{msg.type.upper()}] {msg.text}"))

    # 用 HTTP 测试
    page.goto("http://localhost:8769/corgimemo-showcase.html")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(5000)

    # 输出日志
    print("=" * 60)
    print("控制台日志:")
    print("=" * 60)
    corgi_logs = [l for l in logs if "[柯基粒子]" in l]
    if corgi_logs:
        for log in corgi_logs:
            print(log)
    else:
        print("未找到柯基粒子相关日志")
        print("所有日志:")
        for log in logs[:30]:
            print(log)

    browser.close()
