# 灵感图片全屏预览增强设计文档

> 日期：2026-07-12
> 状态：已批准

## 1. 背景

`InspirationImageGallery` 当前已实现点击图片进入全屏预览（参见最近的修复：图片点击事件路径转义 / `combinedClickable` 与 `clickable` 冲突 / Gallery 布局层级问题）。

但用户反馈当前全屏预览仍存在三点不足：

1. 顶部状态栏（17:01 时间、信号）和底部导航栏（待办/灵感/日期/我的）依然可见，未实现沉浸式体验
2. 多图时虽然已有 `HorizontalPager` 支持左右滑动，但缺少下载入口
3. 用户希望预览时可一键下载图片到本地相册

## 2. 需求定义

### 2.1 沉浸式全屏

- 进入 `InspirationImageGallery` 后立即隐藏状态栏与导航栏
- 用户从屏幕边缘滑动时短暂显示系统栏（粘性行为），松手后自动重新隐藏
- 关闭 Gallery 时恢复系统栏显示

### 2.2 多图左右滑动（已实现，保持不变）

- 通过 `HorizontalPager` 实现多图浏览
- 显示当前页码（如 `1/3`）
- 翻页动画流畅
- 单图手指拖动平移 + 双指捏合缩放 + 双击放大还原（保持现有 ZoomableImage 行为）

### 2.3 图片下载到相册

- 位置：固定在预览页右下角
- 样式：透明背景 + 白色 `Icons.Outlined.Download` 图标，与右上角关闭按钮风格一致
- 行为：点击下载当前显示的图片（`pagerState.currentPage`）到手机系统相册
- 反馈：底部 Snackbar 提示"已保存到相册"或"保存失败"
- 文件名：`Inspiration_${System.currentTimeMillis()}.png`（复用现有命名规范）
- 保存路径：API 29+ 通过 MediaStore 写入公共 `Pictures/InspirationMemo` 目录；API 26-28 回退到应用私有目录

## 3. 技术设计

### 3.1 文件改动清单

| 文件 | 改动 |
|------|------|
| `ui/screens/inspiration/components/InspirationImageGallery.kt` | 新增沉浸式、下载按钮、Snackbar；调用方零改动 |
| `util/InspirationScreenshot.kt` | 复用：现成 `saveToGallery(context, bitmap)` 函数，无需改动 |

### 3.2 沉浸式实现

使用 `WindowInsetsController`（API 30+）控制：

```kotlin
val context = LocalContext.current
DisposableEffect(Unit) {
    val window = (context as? Activity)?.window
    val controller = window?.insetsController
    controller?.hide(
        WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
    )
    controller?.systemBarsBehavior =
        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    onDispose {
        controller?.show(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )
    }
}
```

- 旧版本兼容：API < 30 时 `window.insetsController` 为 null，`?.` 跳过沉浸式，状态栏仍可见（不影响功能）
- 恢复时机：组件 `onDispose` 中恢复，确保用户从预览退出后立即看到状态栏

### 3.3 下载按钮 UI

```kotlin
IconButton(
    onClick = { onDownloadClick(imagePaths[pagerState.currentPage]) },
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 24.dp)
) {
    Icon(
        imageVector = Icons.Outlined.Download,
        contentDescription = "保存到相册",
        tint = Color.White,
        modifier = Modifier.size(28.dp)
    )
}
```

### 3.4 下载流程

```kotlin
val scope = rememberCoroutineScope()
val context = LocalContext.current
val snackbarHostState = remember { SnackbarHostState() }

fun downloadCurrentImage() {
    val path = imagePaths[pagerState.currentPage]
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            try {
                // 1. 用 Coil 加载图片为 Bitmap
                val request = ImageRequest.Builder(context).data(path).build()
                val result = context.imageLoader.execute(request)
                if (result !is SuccessResult) return@withContext false
                val bitmap = result.image.toBitmap()

                // 2. 复用现有 saveToGallery
                InspirationScreenshot.saveToGallery(context, bitmap)
                true
            } catch (e: Exception) {
                Log.e("InspirationImageGallery", "下载失败", e)
                false
            }
        }
        snackbarHostState.showSnackbar(
            if (result) "已保存到相册" else "保存失败"
        )
    }
}
```

- 单图保存：针对 `pagerState.currentPage` 当前显示的图片
- 复用现有 `saveToGallery(context, bitmap)`，自动适配 API 版本
- 协程在 `Dispatchers.IO` 执行，UI 线程不阻塞
- Snackbar 在主线程由 `coroutineScope` 触发

### 3.5 Snackbar 实现

```kotlin
Box(Modifier.fillMaxSize().background(Color.Black)) {
    HorizontalPager(...) { ... }  // 现有

    IconButton(...) { ... }  // 右上角 X（现有）

    Text(...)  // 顶部页码（现有）

    // 新增：右下角下载按钮
    IconButton(
        onClick = ::downloadCurrentImage,
        modifier = Modifier.align(Alignment.BottomEnd).padding(...)
    ) { Icon(Icons.Outlined.Download, ...) }

    // 新增：底部居中 Snackbar
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp)  // 避开下载按钮
    )
}
```

- 文字："已保存到相册" / "保存失败"
- 位置：底部居中，离底 80dp（避免与右下角下载按钮重叠）
- 自动消失：使用 Snackbar 默认 duration（~4s）

### 3.6 错误处理

| 场景 | Snackbar 提示 |
|------|--------------|
| 图片加载失败（Coil 抛异常） | "图片加载失败" |
| 图片不存在 | "图片不存在" |
| `saveToGallery` 返回 null | "保存失败" |
| 协程异常 | "保存失败" |

## 4. 与现有约束的兼容性

| 约束 | 状态 |
|------|------|
| 调用方零改动（`InspirationScreen.kt`、`InspirationViewScreen.kt`） | ✓ 仅修改 Gallery 内部 |
| 保存到相册遵循 `MediaStore`/`RELATIVE_PATH`/`IS_PENDING` 规范 | ✓ 复用 `saveToGallery` |
| 图片保持原图比例 | ✓ 不缩放，原样保存 |
| 风格统一 | ✓ 与右上角关闭按钮样式一致 |
| 项目硬约束：野兽派风格 | ✗ 透明 + 纯图标非野兽派，但与用户选择的"透明背景+纯图标"一致 |

## 5. 测试清单

- [ ] 启动 Gallery 后状态栏与导航栏不可见
- [ ] 从屏幕边缘滑动时短暂显示系统栏，松手后自动隐藏
- [ ] 关闭 Gallery 后状态栏与导航栏恢复显示
- [ ] 单图时关闭按钮、页码、下载按钮位置正确
- [ ] 多图时可左右滑动切换，页码同步更新
- [ ] 点击下载按钮后，图片出现在系统相册
- [ ] Snackbar 显示"已保存到相册"
- [ ] API 29+ 设备图片保存到 `Pictures/InspirationMemo/` 目录
- [ ] API 26-28 设备图片保存到应用私有目录
- [ ] 不影响 ZoomableImage 缩放/平移/双击交互
- [ ] 关闭 Gallery 后再次进入仍能正常显示
