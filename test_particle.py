from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})

    logs = []
    page.on("pageerror", lambda err: logs.append(f"PAGE_ERROR: {err}"))
    page.on("console", lambda msg: logs.append(f"CONSOLE({msg.type}): {msg.text}"))

    # 用 file:// 协议直接打开
    page.goto("file:///C:/Users/Lenovo/Desktop/CorgiMemo/%E3%80%90%E5%88%BB%E8%AE%B0+%E3%80%91APP/corgimemo-showcase.html", timeout=120000)
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(8000)

    # 检查 canvas
    canvas_count = page.locator("canvas").count()
    print(f"Canvas elements: {canvas_count}")

    # 检查 brutal-corgi-canvas
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

    # 检查图片加载状态
    img_status = page.evaluate("""() => {
        if (typeof window._corgiDebug !== 'undefined') {
            return window._corgiDebug;
        }
        return 'debug info not available';
    }""")
    print(f"Image status: {img_status}")

    # 页面错误
    errors = [l for l in logs if "error" in l.lower()]
    print(f"Errors ({len(errors)}):")
    for e in errors[:5]:
        print(f"  {e}")

    # 截图
    page.screenshot(path="c:/Users/Lenovo/Desktop/CorgiMemo/test_result.png", full_page=False)
    print("Screenshot saved")

    browser.close()
