# Snackbar 统一优化与灵感页撤销提示

**日期**：2026-07-14
**类型**：UI 优化 + 交互改进
**前置任务**：
- [Snackbar 格式重设计](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar格式重设计-design.md)
- [Snackbar 体验优化](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar体验优化-design.md)

---

## 1. 背景与目标

### 1.1 已完成（前两轮迭代）

| 需求 | 状态 | 实现位置 |
|------|------|---------|
| 全项目 Snackbar 统一为单段式品牌风格 | ✅ 已完成 | [AppSnackbarHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt) |
| 左侧柯基图直接引用（无 Box 背景包裹） | ✅ 已完成 | 同上 |
| 容器宽度自适应，最大 560dp | ✅ 已完成 | `widthIn(max = SnackbarMaxWidth)` |
| 无按钮 Snackbar 底部居中 | ✅ 已完成 | 外层 Box `contentAlignment = Center` |
| 带按钮 Snackbar 文字靠左、按钮靠右 | ✅ 已完成 | 文字 `weight(1f, fill = false)` |
| 待办删除合并为 1 阶段（取消立即"已删除"Snackbar） | ✅ 已完成 | [HomeScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) |
| 图标缩小到 28dp，整体更紧凑 | ✅ 已完成 | `Modifier.size(28.dp)` |

### 1.2 本次待完成

| # | 需求 | 现状 | 期望 |
|---|------|------|------|
| 4 | 灵感页删除灵感时无 Snackbar 提示 | [InspirationViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/InspirationViewModel.kt) 中 `deleteInspiration` / `batchDeleteInspirations` 直接软删除，无撤销状态 | 仿 HomeViewModel，添加 `pendingDeletedInspiration` / `pendingBatchDeletedInspirations` 状态 + 5s 倒计时 + undo 方法 |
| 5 | Snackbar 底部紧贴 APP 底部导航栏，无缝隙 | Material 3 Scaffold `snackbarHost` 槽位默认紧贴 bottomBar | 在 [AppSnackbarHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt) 内统一加 16dp bottom padding |
| 6 | Snackbar 整体偏高，垂直内边距过大 | 当前 vertical padding 8dp / 6dp，图标 28dp → 整体高度 44dp | 缩到 4dp / 2dp → 整体高度 36dp（接近安卓原生 48dp） |

---

## 2. 设计目标

| 目标 | 描述 |
|------|------|
| **架构一致性** | InspirationViewModel 仿 HomeViewModel 模式，避免架构分裂 |
| **统一性** | 8 处 snackbarHost 槽位（MainScreen、TodoEditScreen、RecycleBinScreen、SpecialDateQuickCreateScreen、SpecialDateDetailScreen、SpecialDateCardStyleScreen、InspirationEditScreen、InspirationViewScreen）共享同一 AppSnackbarHost，新增改动一处生效 |
| **安卓规范** | Snackbar 位置与高度符合 Material Design 3 标准（底部 + 16dp 间距 + 48dp 高度） |
| **代码精简** | 保持 1 行调用方式 `snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }` |

---

## 3. 视觉规范

### 3.1 AppSnackbarHost 容器

| 维度 | 当前值 | 新值 | 说明 |
|------|--------|------|------|
| **底部间距** | 紧贴 bottomBar | `padding(bottom = 16.dp)` | 安卓标准间距 |
| **垂直内边距（无按钮）** | 8dp | 4dp | 高度降低 4dp |
| **垂直内边距（带按钮）** | 6dp | 2dp | 高度降低 4dp |
| **整体高度** | 44dp | 36dp | 图标 28dp + 上下各 4dp |
| **容器宽度** | `widthIn(max = 560.dp)` | 保持 | 已有 |
| **容器对齐** | `Alignment.Center` | 保持 | 已有 |
| **圆角** | `RoundedCornerShape(20.dp)` | 保持 | 已有 |
| **阴影** | `shadowElevation = 4.dp` | 保持 | 已有 |
| **水平内边距** | 16dp | 保持 | 已有 |

### 3.2 特殊场景：InspirationImageGallery

该组件 Snackbar 离底 80dp（避开下载按钮）：

```kotlin
AppSnackbarHost(
    hostState = snackbarHostState,
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 80.dp)  // 原有 modifier
)
```

**影响分析**：在 AppSnackbarHost 内再加 16dp bottom padding 后，实际离底 80 + 16 = 96dp。可接受（离底更远）。**无需特殊处理**。

### 3.3 视觉对比

**修改前**（Snackbar 紧贴导航栏 + 高度 44dp）：
```
        ┌─────────────────────────┐
        │ 🐕 已删除 1 个待办 撤销  │  ← 44dp 高
        └─────────────────────────┘
═══════════════════════════════════════════  ← 紧贴
   首页  待办  日历  我的  [+]            ← 底部导航栏
```

**修改后**（Snackbar 离导航栏 16dp + 高度 36dp）：
```
        ┌───────────────────────┐
        │ 🐕 已删除 1 个待办 撤销│  ← 36dp 高
        └───────────────────────┘
                                    ← 16dp 间隙
═══════════════════════════════════════════
   首页  待办  日历  我的  [+]            ← 底部导航栏
```

---

## 4. 行为规范

### 4.1 灵感页删除流程（新增）

**单个删除**：
1. 用户长按 → 菜单 → 删除 → 二次确认 → `viewModel.deleteInspiration(id)`
2. ViewModel 软删除（移入回收站）+ 设置 `_pendingDeletedInspiration`
3. 启动 5s 倒计时 Job
4. 屏幕显示 "已删除" + 撤销 按钮（Snackbar）
5. 用户点撤销 → 调用 `undoDeleteInspiration()` → 从回收站移回灵感表 + 取消倒计时
6. 5s 倒计时结束 → `_pendingDeletedInspiration = null` + 从回收站永久删除

**批量删除**：
1. 批量模式 → 选中 N 条 → 删除 → 二次确认 → `viewModel.batchDeleteInspirations()`
2. ViewModel 批量软删除 + 设置 `_pendingBatchDeletedInspirations`
3. 启动 5s 倒计时 Job
4. 屏幕显示 "已删除 N 条灵感" + 全部撤销 按钮
5. 用户点撤销 → `undoBatchDeleteInspiration()` → 批量恢复
6. 5s 倒计时结束 → 永久删除

### 4.2 灵感页 Snackbar 触发位置

在 [InspirationScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) 中添加 `LaunchedEffect` 监听 ViewModel 状态：

```kotlin
/** 监听单个灵感删除事件 */
LaunchedEffect(pendingDeletedInspiration) {
    pendingDeletedInspiration?.let { inspiration ->
        val result = snackbarHostState?.showSnackbar(
            message = "已删除 1 条灵感",
            actionLabel = "撤销",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDeleteInspiration()
        } else {
            viewModel.clearPendingDeletedInspiration()
        }
    }
}

/** 监听批量删除事件 */
LaunchedEffect(pendingBatchDeletedInspirations) {
    pendingBatchDeletedInspirations?.let { list ->
        if (list.isNotEmpty()) {
            val result = snackbarHostState?.showSnackbar(
                message = "已删除 ${list.size} 条灵感",
                actionLabel = "全部撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoBatchDeleteInspiration()
            } else {
                viewModel.clearPendingBatchDeletedInspiration()
            }
        }
    }
}
```

---

## 5. 组件 API

### 5.1 AppSnackbarHost 改动

**位置**：[app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt)

**改动**：
```kotlin
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 需求 5：与底部导航栏留 16dp 间隙（安卓标准）
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.widthIn(max = SnackbarMaxWidth)
        ) { data ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                val hasAction = data.visuals.actionLabel != null
                Row(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        // 需求 6：减小垂直内边距，降低 Snackbar 整体高度
                        vertical = if (hasAction) 2.dp else 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.corgi_tilt_2frames_01),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val textModifier = if (hasAction) {
                        Modifier.weight(1f, fill = false)
                    } else {
                        Modifier
                    }
                    Text(
                        text = data.visuals.message,
                        modifier = textModifier,
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
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 0.dp
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
    }
}

private val SnackbarMaxWidth = 560.dp
```

### 5.2 InspirationViewModel 新增 API

```kotlin
/** 单个待撤销删除的灵感（用于显示 Snackbar） */
private val _pendingDeletedInspiration = MutableStateFlow<Inspiration?>(null)
val pendingDeletedInspiration: StateFlow<Inspiration?> = _pendingDeletedInspiration.asStateFlow()

/** 批量待撤销删除的灵感列表（用于显示 Snackbar） */
private val _pendingBatchDeletedInspirations = MutableStateFlow<List<Inspiration>?>(null)
val pendingBatchDeletedInspirations: StateFlow<List<Inspiration>?> = _pendingBatchDeletedInspirations.asStateFlow()

/** 删除倒计时任务（可取消） */
private var deleteInspirationTimerJob: Job? = null

/** 删除倒计时时长（5秒） */
private val UNDO_DELETE_INSPIRATION_DELAY_MS = 5000L

/**
 * 删除灵感（软删除 + 设置撤销状态）
 * 改造点：相比原版，额外设置 _pendingDeletedInspiration 并启动 5s 倒计时
 */
fun deleteInspiration(id: Long) {
    viewModelScope.launch {
        runCatching {
            val inspiration = inspirationRepository.getInspirationById(id)
            if (inspiration != null) {
                deletedInspirationRepository.insertDeletedInspiration(inspiration)
                inspirationRepository.deleteById(id)
                
                // ★ 新增：设置撤销状态 + 启动倒计时
                _pendingDeletedInspiration.value = inspiration
                deleteInspirationTimerJob?.cancel()
                deleteInspirationTimerJob = launch {
                    delay(UNDO_DELETE_INSPIRATION_DELAY_MS)
                    _pendingDeletedInspiration.value = null
                }
            }
        }
    }
}

/** 批量删除选中的灵感（软删除 + 设置撤销状态） */
fun batchDeleteInspirations() {
    val selectedIds = _selectedInspirationIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        val deletedList = mutableListOf<Inspiration>()
        selectedIds.forEach { id ->
            val inspiration = inspirationRepository.getInspirationById(id)
            if (inspiration != null) {
                deletedInspirationRepository.insertDeletedInspiration(inspiration)
                inspirationRepository.deleteById(id)
                deletedList.add(inspiration)
            }
        }
        exitBatchMode()
        
        // ★ 新增：设置批量撤销状态 + 启动倒计时
        _pendingBatchDeletedInspirations.value = deletedList
        deleteInspirationTimerJob?.cancel()
        deleteInspirationTimerJob = launch {
            delay(UNDO_DELETE_INSPIRATION_DELAY_MS)
            _pendingBatchDeletedInspirations.value = null
        }
    }
}

/** 撤销单个灵感删除（从回收站移回灵感表） */
fun undoDeleteInspiration() {
    viewModelScope.launch {
        val inspiration = _pendingDeletedInspiration.value ?: return@launch
        deleteInspirationTimerJob?.cancel()
        deleteInspirationTimerJob = null
        // 1. 从回收站永久删除（注意：与 HomeViewModel 不同，灵感是软删除到回收站的）
        deletedInspirationRepository.permanentlyDelete(inspiration.id)
        // 2. 重新插入灵感表
        inspirationRepository.insert(inspiration)
        _pendingDeletedInspiration.value = null
    }
}

/** 撤销批量灵感删除 */
fun undoBatchDeleteInspiration() {
    viewModelScope.launch {
        val list = _pendingBatchDeletedInspirations.value ?: return@launch
        deleteInspirationTimerJob?.cancel()
        deleteInspirationTimerJob = null
        list.forEach { inspiration ->
            // 1. 从回收站永久删除
            deletedInspirationRepository.permanentlyDelete(inspiration.id)
            // 2. 重新插入灵感表
            inspirationRepository.insert(inspiration)
        }
        _pendingBatchDeletedInspirations.value = null
    }
}

/** 清除单个待撤销状态（Snackbar 自动消失时调用） */
fun clearPendingDeletedInspiration() {
    deleteInspirationTimerJob?.cancel()
    deleteInspirationTimerJob = null
    _pendingDeletedInspiration.value = null
}

/** 清除批量待撤销状态 */
fun clearPendingBatchDeletedInspiration() {
    deleteInspirationTimerJob?.cancel()
    deleteInspirationTimerJob = null
    _pendingBatchDeletedInspirations.value = null
}
```

### 5.3 InspirationScreen 新增参数

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: InspirationViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState? = null  // ★ 新增参数
) {
    val pendingDeletedInspiration by viewModel.pendingDeletedInspiration.collectAsState()
    val pendingBatchDeletedInspirations by viewModel.pendingBatchDeletedInspirations.collectAsState()
    
    // ... existing code ...
    
    // ★ 新增：监听单个删除 Snackbar
    LaunchedEffect(pendingDeletedInspiration) {
        pendingDeletedInspiration?.let { 
            val host = snackbarHostState ?: return@let
            val result = host.showSnackbar(
                message = "已删除 1 条灵感",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteInspiration()
            } else {
                viewModel.clearPendingDeletedInspiration()
            }
        }
    }
    
    // ★ 新增：监听批量删除 Snackbar
    LaunchedEffect(pendingBatchDeletedInspirations) {
        pendingBatchDeletedInspirations?.let { list ->
            if (list.isNotEmpty()) {
                val host = snackbarHostState ?: return@let
                val result = host.showSnackbar(
                    message = "已删除 ${list.size} 条灵感",
                    actionLabel = "全部撤销",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoBatchDeleteInspiration()
                } else {
                    viewModel.clearPendingBatchDeletedInspiration()
                }
            }
        }
    }
}
```

### 5.4 MainScreen 调用改造

```kotlin
TabItem.INSPIRE -> InspirationScreen(
    navController = navController,
    onFabClick = onFabClick,
    viewModel = inspirationViewModel,
    snackbarHostState = snackbarHostState  // ★ 新增参数传递
)
```

---

## 6. 改造清单

| # | 文件 | 改造内容 | 行数估算 |
|---|------|---------|---------|
| 1 | [AppSnackbarHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt) | Box 加 `padding(bottom = 16.dp)`；Row vertical padding 8/6 → 4/2 | 2 处 |
| 2 | [InspirationViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/InspirationViewModel.kt) | 添加 2 个 StateFlow + 4 个方法 + 改造 2 个现有方法 | ~80 行 |
| 3 | [InspirationScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) | 新增 snackbarHostState 参数 + 2 个 LaunchedEffect + import | ~30 行 |
| 4 | [MainScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) | InspirationScreen 调用加 `snackbarHostState = snackbarHostState` | 1 行 |

**总计**：4 个文件，约 113 行代码改动。

---

## 7. 不在范围

| 项 | 说明 |
|-----|------|
| **其他类型 Snackbar（日期删除、日期提醒）** | 保持现状，不在本任务 |
| **图片保存 Snackbar** | 保持现状 |
| **多 Snackbar 队列** | 单队列足够（Snackbar 替换而非堆叠） |
| **Snackbar 入场动画** | Material 3 默认 |
| **灵感回收站的二次确认** | 已存在（AlertDialog 二次确认） |
| **i18n** | emoji 和"已删除"等中文文案暂不抽到 strings.xml |
| **回收站永久删除逻辑** | 已存在，本任务只改删除流程的 Snackbar 提示 |

---

## 8. 测试要点

| 场景 | 验证点 |
|------|--------|
| 灵感单个删除 + 撤销 | 显示 "已删除 1 条灵感" + 撤销；点撤销后灵感恢复 |
| 灵感单个删除 + 不撤销 | 5s 后灵感从回收站永久删除，Snackbar 自动消失 |
| 灵感批量删除 + 全部撤销 | 显示 "已删除 N 条灵感" + 全部撤销；点撤销后全部恢复 |
| 灵感批量删除 + 不撤销 | 5s 后批量永久删除 |
| Snackbar 间隙 | 所有页面（首页、灵感、日期等）Snackbar 底部与导航栏有 16dp 间隙 |
| Snackbar 高度 | 整体高度约 36dp（图标 28dp + 上下各 4dp），更紧凑 |
| 长文本 | 文字过长时省略号截断，不换行，最大 560dp |
| 短文本无按钮 | 居中显示，宽度跟随内容 |
| 短文本带按钮 | 文字靠左，按钮靠右 |
| 长文本带按钮 | 文字省略号截断，按钮不挤压 |
| InspirationImageGallery 场景 | Snackbar 离底 80 + 16 = 96dp，避开下载按钮 |

---

## 9. 风险与注意事项

| 风险 | 缓解措施 |
|------|---------|
| **灵感页删除时立即显示 Snackbar 与 HomeViewModel 行为一致** | 仿 HomeViewModel 模式（`_pendingDeletedInspiration` StateFlow + LaunchedEffect） |
| **撤销逻辑可能误触发** | 用 `_pendingDeletedInspiration.value` 非空判断 + cancel timer job |
| **回收站恢复 API 是否存在** | 检查 `deletedInspirationRepository.deleteDeletedInspiration(inspiration)` 是否存在，若不存在需补充实现 |
| **灵感页与首页 Snackbar 共享状态** | 各自 ViewModel 独立，互不影响 |
| **配置变更（旋转屏幕）撤销状态丢失** | StateFlow 存于 ViewModel，旋转不丢失（除非 ViewModel 被销毁） |
| **多次快速删除** | cancel 上一个 timer job + 替换 _pendingDeletedInspiration（仅保留最新） |
| **批量删除时单个删除也被触发** | 互斥：批量删除时设置 `_pendingBatchDeletedInspirations`，单个状态保持 null |

---

## 10. 后续可优化点

| 优化项 | 说明 |
|--------|------|
| **Snackbar 队列** | 现状单队列，并发会替换；可改用多 `SnackbarHostState` |
| **入场动画** | 现状 Material 3 默认；可改为从下方滑入 + 淡入 |
| **设计 token 化** | 16dp 间隙、36dp 高度、560dp 最大宽度可抽到 `UiTokens` |
| **可访问性** | `Image` 的 `contentDescription = null` 可改为 "应用提示" 供 TalkBack |
| **i18n** | emoji 和中文文案抽到 `strings.xml` |
| **PendingDelete 抽象** | HomeViewModel 和 InspirationViewModel 的删除撤销模式可抽出公共类 |
