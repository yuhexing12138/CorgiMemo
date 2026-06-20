from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1280, "height": 800})

    # 打开页面
    page.goto('http://localhost:8080/corgimemo-showcase.html')
    page.wait_for_load_state('networkidle')

    # 滚动到三大功能关系图区域
    diagram = page.locator('#functionDiagram')
    if diagram.count() > 0:
        diagram.scroll_into_view_if_needed()
        page.wait_for_timeout(500)
        print("找到 functionDiagram，已滚动到视图")

        # 截图：点击前
        page.screenshot(path='/tmp/before_click.png')

        # 查找节点
        nodes = page.locator('.diagram-node')
        print(f"找到 {nodes.count()} 个 diagram-node 节点")

        for i in range(nodes.count()):
            node = nodes.nth(i)
            text = node.inner_text()
            print(f"  节点 {i}: {text.strip()}, visible={node.is_visible()}")

        # 点击待办管理节点
        todo_node = page.locator('.diagram-node[data-func="todo"]')
        if todo_node.count() > 0:
            print("\n点击 待办管理 节点...")
            todo_node.click()
            page.wait_for_timeout(500)

            # 检查弹窗是否出现
            modal = page.locator('#modal-todo')
            if modal.count() > 0:
                is_active = modal.evaluate('el => el.classList.contains("active")')
                print(f"弹窗 modal-todo 是否 active: {is_active}")
                page.screenshot(path='/tmp/after_click_todo.png')
            else:
                print("未找到 modal-todo 弹窗")

            # 检查控制台错误
            page.on("console", lambda msg: print(f"CONSOLE: {msg.type}: {msg.text}"))
        else:
            print("未找到 待办管理 节点")
    else:
        print("未找到 functionDiagram")

    browser.close()
