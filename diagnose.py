from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})

    # 收集所有日志
    logs = []
    page.on("console", lambda msg: logs.append(f"[{msg.type}] {msg.text}"))
    page.on("pageerror", lambda err: logs.append(f"[ERROR] {err}"))

    # 测试 HTTP 协议
    page.goto("http://localhost:8770/corgimemo-showcase.html", timeout=120000)
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(8000)

    # 检查 canvas 和图片状态
    result = page.evaluate("""() => {
        // 检查粒子 canvas
        var container = document.getElementById('brutal-corgi-canvas');
        var canvas = container ? container.querySelector('canvas') : null;
        
        // 检查页面中普通 img 标签是否能加载柯基图片
        var testImg = document.querySelector('img[src*="corgi_tilt"]');
        var testImgLoaded = testImg ? testImg.complete && testImg.naturalHeight > 0 : false;
        
        return {
            canvasExists: !!canvas,
            canvasWidth: canvas ? canvas.width : 0,
            testImgLoaded: testImgLoaded,
            testImgSrc: testImg ? testImg.src : null
        };
    }""")
    
    print("=" * 60)
    print("诊断结果:")
    print("=" * 60)
    print(f"粒子 Canvas: {result['canvasExists']}, 尺寸: {result['canvasWidth']}")
    print(f"测试图片加载: {result['testImgLoaded']}")
    print(f"测试图片路径: {result['testImgSrc']}")
    
    # 输出错误日志
    errors = [l for l in logs if "error" in l.lower() or "ERROR" in l]
    if errors:
        print("\n错误日志:")
        for e in errors[:10]:
            print(f"  {e}")
    
    # 截图
    page.screenshot(path="c:/Users/Lenovo/Desktop/CorgiMemo/diagnose_result.png")
    print("\n截图已保存: diagnose_result.png")

    browser.close()
