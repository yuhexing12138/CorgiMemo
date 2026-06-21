from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})

    logs = []
    page.on("pageerror", lambda err: logs.append(f"PAGE_ERROR: {err}"))

    # 用 HTTP 服务器测试（从项目根目录启动，路径与 file:// 一致）
    page.goto("http://localhost:8767/%E3%80%90%E5%88%BB%E8%AE%B0+%E3%80%91APP/corgimemo-showcase.html", timeout=120000)
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(5000)

    # 检查 canvas
    canvas_count = page.locator("canvas").count()
    print(f"Canvas elements: {canvas_count}")

    # 检查图片加载状态
    result = page.evaluate("""() => {
        var container = document.getElementById('brutal-corgi-canvas');
        var canvas = container ? container.querySelector('canvas') : null;
        return {
            containerExists: !!container,
            canvasExists: !!canvas,
            canvasWidth: canvas ? canvas.width : 0,
            canvasHeight: canvas ? canvas.height : 0
        };
    }""")
    print(f"Container: {result['containerExists']}, Canvas: {result['canvasExists']}, Size: {result['canvasWidth']}x{result['canvasHeight']}")

    # 页面错误
    errors = [l for l in logs if "PAGE_ERROR" in l]
    print(f"Page errors ({len(errors)}):")
    for e in errors[:5]:
        print(f"  {e}")

    # 截图
    page.screenshot(path="c:/Users/Lenovo/Desktop/CorgiMemo/brutal-corgi-http.png", full_page=False)
    print("Screenshot saved")

    browser.close()
