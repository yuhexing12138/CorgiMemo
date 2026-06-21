from playwright.sync_api import sync_playwright
import time

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1280, "height": 900})

    file_path = r"c:\Users\Lenovo\Desktop\CorgiMemo\【刻记+】APP\corgimemo-showcase.html"
    file_url = "file:///" + file_path.replace("\\", "/")

    page.goto(file_url, timeout=120000)
    page.wait_for_load_state("networkidle")
    time.sleep(5)

    page.evaluate("window.scrollTo(0, 5000)")
    time.sleep(2)

    page.evaluate("""() => {
        const card = document.querySelector('.combo-card');
        if (card) card.click();
    }""")
    time.sleep(3)

    # 检查canvas像素
    pixel_info = page.evaluate("""() => {
        const canvas = document.querySelector('#sceneP5Container canvas');
        if (!canvas) return { error: 'no canvas' };

        const ctx = canvas.getContext('2d');
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const data = imageData.data;

        let nonWhitePixels = 0;
        let nonTransparentPixels = 0;
        let sampleColors = [];

        for (let i = 0; i < data.length; i += 4) {
            const r = data[i], g = data[i+1], b = data[i+2], a = data[i+3];
            if (a > 0) nonTransparentPixels++;
            if (a > 10 && (r < 250 || g < 250 || b < 250)) {
                nonWhitePixels++;
                if (sampleColors.length < 8) {
                    sampleColors.push([r, g, b, a]);
                }
            }
        }

        return {
            totalPixels: canvas.width * canvas.height,
            nonTransparentPixels,
            nonWhitePixels,
            sampleColors
        };
    }""")
    print(f"Pixel analysis: {pixel_info}")

    # 截图
    page.screenshot(path="modal-particles-visible.png")
    print("Screenshot saved")

    browser.close()
