# 灵感图片全屏预览增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增强 `InspirationImageGallery`，实现沉浸式全屏、多图左右滑动、右下角图片下载到相册功能。

**Architecture:** 在现有 `InspirationImageGallery` Composable 内部扩展：(1) `DisposableEffect` 控制 `WindowInsetsController` 实现粘性沉浸式；(2) 复用现有 `HorizontalPager`（已支持多图滑动）；(3) 右下角 `IconButton` + `coroutineScope` 调起 Coil 加载 + `InspirationScreenshot.saveToGallery` 保存，Snackbar 反馈结果。调用方零改动。

**Tech Stack:** Kotlin / Jetpack Compose / Coil 3.5.0 / MediaStore / WindowInsetsController

---

## File Structure

| 文件 | 改动 | 职责 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt` | 修改 | 新增沉浸式、下载按钮、Snackbar、协程下载逻辑 |
| `app/src/main/java/com/corgimemo/app/util/InspirationScreenshot.kt` | 不变 | 复用 `saveToGallery(context, bitmap)` |

---

## Task 1: 添加沉浸式全屏支持

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt:1-96`

- [ ] **Step 1: 添加必要 import**

在文件顶部 import 区域（现有 imports 后）添加：

```kotlin
import android.app.Activity
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.outlined.Download
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.corgimemo.app.util.InspirationScreenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: 在 `InspirationImageGallery` 函数顶部添加 `DisposableEffect`**

修改 `InspirationImageGallery` 函数（`InspirationImageGallery.kt:45-96`），在 `pagerState` 声明后、`Box` 之前插入：

```kotlin
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 粘性沉浸式：隐藏状态栏与导航栏
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（无新增错误，可能有未使用 import 警告，下载按钮 task 解决）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
git commit -m "feat(inspiration): add immersive fullscreen to image gallery"
```

---

## Task 2: 添加下载按钮 UI

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt:71-95`

- [ ] **Step 1: 在 `Box` 内部关闭按钮之后添加下载按钮**

修改 `InspirationImageGallery` 的 `Box`（`InspirationImageGallery.kt:58-95`）。在 `Text`（页码）之后、`Box { ... }` 闭合之前添加下载按钮：

```kotlin
        // 下载按钮（右下角）
        IconButton(
            onClick = { downloadCurrentImage(...) },  // 占位，Task 3 实现
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

- [ ] **Step 2: 验证编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: 编译错误（`downloadCurrentImage` 未定义）— 正常，下个 task 解决

- [ ] **Step 3: 临时将 onClick 改为空实现以便编译**

将下载按钮的 `onClick` 改为 `{}` 临时占位：

```kotlin
        IconButton(
            onClick = { /* TODO: Task 3 */ },
            ...
        )
```

- [ ] **Step 4: 验证编译通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
git commit -m "feat(inspiration): add download button placeholder to image gallery"
```

---

## Task 3: 实现图片下载逻辑

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt`

- [ ] **Step 1: 在 `DisposableEffect` 之前添加 `downloadCurrentImage` 闭包**

修改 `InspirationImageGallery` 函数，在 `DisposableEffect(Unit) { ... }` 之前插入：

```kotlin
    // 下载当前显示的图片到相册
    fun downloadCurrentImage() {
        val path = imagePaths.getOrNull(pagerState.currentPage) ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    // 用 BitmapFactory.decodeFile 直接读取本地图片为 Bitmap
                    // 替代 Coil 的 image.toBitmap()，避免 KMP expect/actual 扩展的解析问题
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap == null) {
                        Log.w("InspirationImageGallery", "图片不存在或无法解码: $path")
                        return@withContext false
                    }
                    InspirationScreenshot.saveToGallery(context, bitmap) != null
                } catch (e: Exception) {
                    Log.e("InspirationImageGallery", "下载失败: $path", e)
                    false
                }
            }
            snackbarHostState.showSnackbar(
                if (saved) "已保存到相册" else "保存失败"
            )
        }
    }
```

- [ ] **Step 2: 将下载按钮 onClick 连接到 `downloadCurrentImage`**

修改下载按钮（Task 2 中添加的）：

```kotlin
        IconButton(
            onClick = ::downloadCurrentImage,
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

- [ ] **Step 3: 验证编译通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（无错误）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
git commit -m "feat(inspiration): implement download to gallery with Snackbar feedback"
```

---

## Task 4: 添加 SnackbarHost 到 Box 内部

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt`

- [ ] **Step 1: 在 `Box` 内部、`}` 闭合之前添加 SnackbarHost**

在 `IconButton`（下载按钮）之后、`Box { ... }` 闭合之前添加：

```kotlin
        // Snackbar 提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
```

- [ ] **Step 2: 验证编译通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
git commit -m "feat(inspiration): add Snackbar host to image gallery"
```

---

## Task 5: 手动验证

- [ ] **Step 1: 编译完整 APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查 import 无未使用项**

Run: 在 Android Studio 中查看 `InspirationImageGallery.kt` 的 import 区域，移除任何未使用的 import

- [ ] **Step 3: 手动测试清单**

| # | 测试项 | 预期 | 通过 |
|---|--------|------|------|
| 1 | 点击灵感列表中的图片 | 进入全屏预览，状态栏与导航栏隐藏 | ☐ |
| 2 | 进入预览后从顶部边缘下滑 | 短暂显示状态栏，松手后自动隐藏 | ☐ |
| 3 | 进入预览后从底部边缘上滑 | 短暂显示导航栏，松手后自动隐藏 | ☐ |
| 4 | 点击右上角 X | 退出预览，状态栏与导航栏恢复 | ☐ |
| 5 | 多图灵感：左右滑动 | 图片切换，页码同步更新 | ☐ |
| 6 | 单图：点击右下角下载按钮 | Snackbar 显示"已保存到相册" | ☐ |
| 7 | 多图：切换到第 2 张，点击下载 | Snackbar 显示"已保存到相册"，相册中是第 2 张图 | ☐ |
| 8 | 打开手机相册 | 看到下载的图片，文件名以 `Inspiration_` 开头 | ☐ |
| 9 | 双指捏合放大、双击放大、单指拖动平移 | 缩放交互正常工作 | ☐ |
| 10 | 关闭后再次进入 Gallery | 沉浸式、下载按钮、Snackbar 全部正常 | ☐ |

---

## Task 6: 提交最终版本

- [ ] **Step 1: 最终 commit（如有清理改动）**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
git commit -m "chore(inspiration): clean up unused imports in image gallery"
```

如果没有需要 commit 的改动，可跳过。

---

## Self-Review

### Spec Coverage

| Spec 需求 | Task |
|-----------|------|
| 沉浸式全屏（隐藏状态栏/导航栏） | Task 1 |
| 粘性行为（边缘滑动短暂显示） | Task 1 |
| 关闭 Gallery 恢复系统栏 | Task 1（onDispose） |
| 多图左右滑动 | 已实现，无需新 task |
| 页码同步 | 已实现，无需新 task |
| 右下角下载按钮 | Task 2 |
| 透明背景 + 纯图标样式 | Task 2 |
| 点击下载到相册 | Task 3 |
| Snackbar 成功/失败反馈 | Task 3 + Task 4 |
| 文件名规范 `Inspiration_${timestamp}.png` | Task 3（复用 saveToGallery） |
| API 29+ MediaStore / 26-28 回退 | Task 3（复用 saveToGallery） |

### Placeholder Scan

- 所有代码块均为完整可粘贴内容
- 无 "TODO"/"TBD"/"implement later"（Task 2 Step 3 的 `/* TODO: Task 3 */` 是临时占位，已在 Task 3 Step 2 替换）
- 无 "Add appropriate error handling" 模糊描述
- 测试项使用 checkbox 而非"写测试"

### Type Consistency

- `downloadCurrentImage` 在 Task 3 Step 1 定义，在 Task 3 Step 2 引用
- `snackbarHostState` 在 Task 1 Step 2 定义，Task 3 Step 1 写入，Task 4 Step 1 渲染
- `scope` 在 Task 1 Step 2 定义，Task 3 Step 1 使用
- `context` 在 Task 1 Step 2 定义，Task 3 Step 1 使用
