# Snackbar 格式重设计

**日期**：2026-07-14
**类型**：UI 重构 + Bug 修复
**关联提交**：`498a0952`、`4d27d83a`（v1 + v2，引入崩溃）

---

## 1. 背景与问题

### 1.1 旧实现回顾（v1 + v2）

| 阶段 | 内容 | 提交 |
|------|------|------|
| v1 | 删除全屏 `CelebrationOverlay`，改为 Material 3 默认 Snackbar 提示 | `498a0952` |
| v2 | 新建 `CorgiSnackbar.kt` 品牌组件，全项目统一三段式 | `4d27d83a` |

v2 设计意图：左侧圆形 APP 图标 + 中间浅色文字区 + 右侧方块按钮。

### 1.2 触发的问题

v2 引入后，**所有点击 Snackbar 提示的功能**都闪退。崩溃堆栈：

```
java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported
  at androidx.compose.ui.res.PainterResources_androidKt.loadVectorResource
  at CorgiSnackbar.kt:77
```

### 1.3 根因

`app/src/main/res/drawable/ic_launcher.xml` 是 `<bitmap>` 包装的 XML：

```xml
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/corgi_tilt_2frames_01" />
```

Compose 的 `painterResource(id = R.drawable.ic_launcher)` 内部对资源类型做判断：
- 真 VectorDrawable → `loadVectorResource()` ✓
- 真 PNG / JPG / WEBP → `loadImageBitmapResource()` ✓
- **`<bitmap>` 包装的资源** → 走 `loadVectorResource()` 路径，解析失败抛 `IllegalArgumentException` ✗

### 1.4 用户的设计反馈

参考图（用户提供的截图）显示理想的 Snackbar 形态：
- **单段式**：白色圆角矩形，左侧柯基图直接显示（**无背景色块包裹**）
- **一行布局**：文字 + 右侧按钮一行内显示，不换行
- **极简风格**：去除三段式、去除右侧方块装饰

---

## 2. 设计目标

| 目标 | 描述 |
|------|------|
| **修复崩溃** | 解决 `<bitmap>` 包装资源在 Compose 中的渲染失败 |
| **视觉重设** | 符合参考图风格：单段式、左侧图标、Material 3 原生按钮动效 |
| **代码统一** | 全项目 9 处 `snackbarHost` 槽位统一为新组件 |
| **代码精简** | 抽公共 `AppSnackbarHost` composable，调用方代码 ≤ 1 行 |
| **清理调试代码** | 删除 19 个调试日志 + 3 个临时 try-catch |
| **删除废弃组件** | 删除 `CorgiSnackbar.kt`（被新组件替代） |

---

## 3. 视觉规范

| 维度 | 规范 |
|------|------|
| **容器形状** | `RoundedCornerShape(20.dp)` |
| **容器背景** | `MaterialTheme.colorScheme.surface` |
| **阴影** | `shadowElevation = 4.dp` |
| **外边距** | `Modifier.padding(12.dp)` |
| **内边距** | `padding(horizontal = 16.dp, vertical = 10.dp)` |
| **左侧图标** | `R.drawable.corgi_tilt_2frames_01`（柯基歪头 PNG），36dp |
| **图标渲染** | **直接 `Image`，无 `Box` 背景色包裹** |
| **图标-文字间距** | `Spacer(width = 12.dp)` |
| **文字** | `MaterialTheme.colorScheme.onSurface`，14sp |
| **文字布局** | `Modifier.weight(1f, fill = false)` + `maxLines = 1` + `TextOverflow.Ellipsis` |
| **文字-按钮间距** | `Spacer(width = 8.dp)`（仅当 `actionLabel != null`） |
| **按钮组件** | `TextButton` |
| **按钮文字色** | `UiColors.Primary` |
| **按钮文字样式** | `FontWeight.Bold` + `maxLines = 1` |
| **整体布局** | `Row` + `verticalAlignment = Alignment.CenterVertically` |

**关键约束**：
- **提示语 + 按钮必须一行显示完毕**，文字过长时省略号截断（`TextOverflow.Ellipsis`）
- Snackbar 位置保持 **Material 3 默认底部居中**

---

## 4. 组件 API

### 4.1 新组件 `AppSnackbarHost`

**位置**：`app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt`

```kotlin
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
)
```

### 4.2 调用方代码

**之前**（6-7 行 lambda）：
```kotlin
snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
```

**之后**（1 行）：
```kotlin
snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }
```

**带 modifier 的位置**（InspirationImageGallery 离底 80dp）：
```kotlin
snackbarHost = {
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(bottom = 80.dp)
    )
}
```

### 4.3 关键实现要点

```kotlin
SnackbarHost(hostState = hostState, modifier = modifier) { data ->
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.corgi_tilt_2frames_01),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f, fill = false),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { data.performAction() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = UiColors.Primary
                    )
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
```

---

## 5. 改造清单

### 5.1 新建文件

| 文件 | 说明 |
|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt` | 新 Snackbar Host 公共组件 |

### 5.2 删除文件

| 文件 | 说明 |
|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/CorgiSnackbar.kt` | 废弃组件，被 `AppSnackbarHost` 替代 |

### 5.3 修改文件（9 处 `snackbarHost` 替换 + 1 处继承）

| # | 文件 | 行号 | 改造 |
|---|------|------|------|
| 1 | `MainScreen.kt` | 829 | `SnackbarHost` 块 → `AppSnackbarHost(hostState = ...)` |
| 2 | `HomeScreen.kt` | — | **继承 MainScreen，无本地 snackbarHost** |
| 3 | `TodoEditScreen.kt` | 943 | `SnackbarHost(...)` → `AppSnackbarHost(...)` |
| 4 | `RecycleBinScreen.kt` | 145 | 同上 |
| 5 | `SpecialDateQuickCreateScreen.kt` | 225 | 同上 |
| 6 | `SpecialDateDetailScreen.kt` | 175 | 同上 |
| 7 | `SpecialDateCardStyleScreen.kt` | 249 | 同上 |
| 8 | `InspirationEditScreen.kt` | 729 | 同上 |
| 9 | `InspirationViewScreen.kt` | 158 | 同上 |
| 10 | `InspirationImageGallery.kt` | 203 | 同上 + 保留 `padding(bottom = 80.dp)` modifier |

### 5.4 清理调试代码

| 文件 | 清理内容 |
|------|---------|
| `CorgiSnackbar.kt` | 整个文件删除（含 4 个 `Log.d` + 1 个 try-catch） |
| `MainScreen.kt` | 删除 3 个 `Log.d` + 1 个 try-catch（保留 `data.performAction()` try-catch） |
| `HomeScreen.kt` | 删除 10 个 `Log.d` + 2 个 try-catch（保留 suspend `showSnackbar` try-catch） |
| `HomeViewModel.kt` | 删除 2 个 `Log.d`（保留原有 `Log.e`） |

---

## 6. 不在范围

| 项 | 说明 |
|-----|------|
| **61 处 `showSnackbar(...)` 调用** | 完全不动，只需替换 `snackbarHost` 槽位 |
| **emoji 强制追加** | 不强制，调用方控制文案 |
| **持续时间调整** | 保持默认 `SnackbarDuration.Short`（4s） |
| **位置调整** | 保持 Material 3 默认底部居中 |
| **dark mode 适配** | 使用 `MaterialTheme.colorScheme.surface`，自动跟随主题 |
| **`BackupRecordCard.kt`** | v2 提交时已同步改为 `corgi_tilt_2frames_01`，本次不动 |

---

## 7. 测试要点

| 场景 | 验证点 |
|------|--------|
| **待办完成** | 单条 → 显示 "✅ {待办标题} 已完成" + 撤销按钮，撤销后恢复 |
| **批量完成** | 多条 → Snackbar 依次显示，文本不换行 |
| **删除操作** | "已删除" + 撤销按钮可点 |
| **错误提示** | "权限不足" → 无按钮，文本一行显示 |
| **长文本** | 文本超长 → 末尾省略号截断，不换行 |
| **底部导航** | Snackbar 显示时与底部 Tab 不重叠（12dp padding） |
| **恢复出厂** | v2 引入的崩溃场景（点击完成/删除）不再闪退 |

---

## 8. 后续可优化点

| 优化项 | 说明 |
|--------|------|
| **Snackbar 入场动画** | 现状用 Material 3 默认；可考虑从下方滑入 + 淡入 |
| **多 Snackbar 队列** | 现状 `SnackbarHostState` 单队列，并发时会替换；可改用多 `SnackbarHostState` |
| **设计 token 化** | 20dp 圆角、4dp 阴影可抽到 `UiTokens` 集中管理 |
| **可访问性** | `Image` 的 `contentDescription = null` 可改为 "应用提示" 供 TalkBack |
| **i18n** | emoji 和"已完成"等中文文案可抽到 `strings.xml` |

---

## 9. 附录

### 9.1 变更对比

| 维度 | 旧（v2 CorgiSnackbar） | 新（AppSnackbarHost） |
|------|------------------------|----------------------|
| 结构 | 三段式 | 单段式 |
| 圆角 | 28dp | 20dp |
| 阴影 | 6dp | 4dp |
| 左侧图标 | 圆形背景（40dp）+ 柯基图（28dp） | 直接柯基图（36dp） |
| 中间 | 浅色背景圆角矩形 | 无背景（共享 surface） |
| 右侧 | 方块（撤销按钮 / FAB 装饰） | `TextButton`（Material 原生文字按钮） |
| 文字换行 | `maxLines = 2` | `maxLines = 1`（强制一行） |
| 资源 | `R.drawable.ic_launcher`（**崩溃**） | `R.drawable.corgi_tilt_2frames_01` |

### 9.2 资源引用约定

| 资源 | 类型 | 用途 |
|------|------|------|
| `R.drawable.corgi_tilt_2frames_01` | PNG | **AppSnackbarHost 左侧图标**（推荐） |
| `R.drawable.corgi_tilt_2frames_02` | PNG | 备用 |
| `R.drawable.ic_launcher` | `<bitmap>` 包装 | AndroidManifest / Widget previewImage 用，**不要在 Compose 中直接 `painterResource` 引用** |
