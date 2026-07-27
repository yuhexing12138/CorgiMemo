# 图片拖拽迁移至 Reorderable 库与自定义上限实施计划

> **任务来源**：用户希望把待办编辑页图片拖拽迁移到 Reorderable 库（项目内已有子模块），并把「每行最多 3 张图片」的硬编码限制改为「用户在设置中可自定义」。
>
> **用户已确认的关键决策**：
> 1. ✅ 同意迁移到 Reorderable 库
> 2. ✅ 解除 3 张限制
> 3. ✅ 上限在设置中由用户自定义
>
> **预期收益**：删除约 1140 行自研代码，引入 50 行 Reorderable API；解除 3 张后 LazyRow 性能优势巨大；与项目内 todo 卡片拖拽技术栈统一。

---

## 一、设计决策一览

| 设计问题 | 最终方案 | 关键理由 |
|---|---|---|
| **Q1**：图片容器用 LazyRow 还是 ReorderableRow？ | **LazyRow + rememberReorderableLazyListState** | 解除 3 张后性能优势巨大；自带边缘自动滚动；3.1.0 版 `rememberReorderableLazyListState` 通过 `layoutInfo.orientation` 自动识别方向 |
| **Q2**：嵌套 Reorderable 的 zIndex / 手势冲突？ | 内层 `key = imagePath`；`Modifier.draggableHandle(DragGestureDetector.LongPress)`；`Modifier.zIndex` 写在 `ReorderableItem` 块内（LazyItemScope 内） | Reorderable 库内部 `awaitFirstDown(requireUnconsumed = true)` 会先消费长按，外层不响应 |
| **Q3**：上限设置 UI 用 Slider / OptionSelectDialog？ | **OptionSelectDialog 扩展版，选项 5 / 10 / 20 / 无限** | 与 `AUTO_BACKUP_KEEP_COUNT` 风格一致；无限存 `-1`；预定义选项比 Slider 步进更友好 |
| **Q4**：maxImagesPerLine 状态如何在 ViewModel 间共享？ | `CorgiPreferences.Flow` + `TodoEditViewModel` 内部 `MutableStateFlow` 同步 | 与项目 `soundEnabled` 模式一致；用户在编辑页改设置可立即生效 |
| **Q5**：删除/废弃哪些代码？ | 删除 `CrossLineDragManager.kt`（384 行）+ `DraggableImageAttachment` 拖拽部分（~400 行）；瘦身 `TodoEditScreen` 桥接（~80 行）；`TodoEditViewModel` 删 5 个旧 API | 总计删除 ~1100 行 |
| **Q6**：UI 层拖拽回调怎么改？ | 单一 `onImageReorder(lineIndex, newOrder)` 回调（替代 5 个旧回调） | 简化 API；由 `onMove` 内部构造完整新顺序 |
| **Q7**：图片删除按钮在拖拽时？ | `if (!isDragging)` 隐藏（与现状一致） | Reorderable 提供 `isDragging: Boolean` |
| **Q8**：Reorderable 库导入？ | `sh.calvin.reorderable.*`；`libs.reorderable = 3.1.0` 已在 `app/build.gradle.kts:169` | 无需新增依赖 |

---

## 二、文件改动清单（按依赖顺序）

### 1. 数据层

| 文件 | 关键改动 |
|---|---|
| `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt` | (1) `Keys` 对象新增 `MAX_IMAGES_PER_LINE = "max_images_per_line"`；(2) `val maxImagesPerLine: Flow<Int> = intFlow(Keys.MAX_IMAGES_PER_LINE, 10)`；(3) `suspend fun saveMaxImagesPerLine(count: Int)`；(4) `suspend fun getMaxImagesPerLine(): Int = esp.getInt(Keys.MAX_IMAGES_PER_LINE, 10)`（参考 `AUTO_BACKUP_KEEP_COUNT` 在 L600, L618-620, L658 的实现） |

### 2. ViewModel 层

| 文件 | 关键改动 |
|---|---|
| `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt` | (1) **删除** `companion object { const val MAX_IMAGES_PER_LINE = 3 }`；(2) 新增 `private val _maxImagesPerLine = MutableStateFlow(10)` + `val maxImagesPerLine: StateFlow<Int>`；(3) `init` 块新增 `corgiPreferences.maxImagesPerLine.collect { _maxImagesPerLine.value = it }`（`viewModelScope.launch`）；(4) `addImageToFocusedLine` 的 `>= MAX_IMAGES_PER_LINE` 改为 `>= _maxImagesPerLine.value`（**注意 `-1` 表示无限，校验逻辑要跳过**）；(5) 新增 `fun applyImageReorder(lineIndex: Int, newOrder: List<String>)`（内部 `setTodoLines`）；(6) **删除** `addImagePath / removeImagePath / reorderImagePaths / setImagePaths / clearImagePaths`（项目 memory 标记「无调用方」） |
| `app/src/main/java/com/corgimemo/app/viewmodel/SettingsViewModel.kt` | (1) `loadSettings` 中新增 `maxImagesPerLine = preferences.getMaxImagesPerLine()`；(2) 新增 `fun saveMaxImagesPerLine(count: Int)`（`viewModelScope.launch { preferences.saveMaxImagesPerLine(count) }`）；(3) 暴露 `var maxImagesPerLine: Int by mutableStateOf(10)` |

### 3. UI 层

| 文件 | 关键改动 |
|---|---|
| `app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt` | 在「数据管理」分组或新建「编辑」分组下新增「单行图片上限」设置项：`SettingListCard` + `SettingItem`，点击弹 `OptionSelectDialog`（参考 `AutoBackupSettings.kt:268-284` 模式），选项 5/10/20/无限（内部存 `-1`） |
| `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt` | **两步走**：先按 `巨石组件拆分规范.md` 拆出 `CheckboxEditTextRow` 到 `checkboxedittext/sections/CheckboxEditRowImpl.kt`（薄壳保留 `CheckboxEditRow` 顶层 API）；再在 Impl 内把 `Row + horizontalScroll + forEachIndexed`（行 1329-1486）替换为 `LazyRow + rememberReorderableLazyListState + items(list, key = { it }) + ReorderableItem`；删 5 个旧拖拽参数，新增 `onImageReorder: (Int, List<String>) -> Unit` 一个参数 |
| `app/src/main/java/com/corgimemo/app/ui/components/DraggableImageAttachment.kt` | **保留 + 瘦身**：删 Popup 浮层、`pointerInput` 长按检测、`graphicsLayer.translation` 拖拽视觉、3 个 `onDragStart/Update/End` 回调；保留：缩略图 Box、`AsyncImage` 渲染、`onClick`、`onDelete`（含 `isDragging` 隐藏删除按钮）；新签名：`ImageAttachmentItem(imagePath, isDragging, onClick, onDelete)` |
| `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt` | (1) 删除 `val crossLineDragManager = remember { CrossLineDragManager() }`（行 388）；(2) 删除 `val rowBoundsMap`；(3) 删除 5 个拖拽回调桥接（行 1442-1529）；(4) `CheckboxEditText` 调用改传 `onImageReorder = { lineIndex, newOrder -> viewModel.applyImageReorder(lineIndex, newOrder) }`；(5) 添加 `viewModel.maxImagesPerLine.collectAsState()` 收集 |
| `app/src/main/java/com/corgimemo/app/ui/components/CrossLineDragManager.kt` | **整文件删除**（384 行） |

### 4. 拆分子目录（按巨石组件拆分规范）

由于 `CheckboxEditText.kt` 达 1666 行（远超 800 行阈值），按 `巨石组件拆分规范.md` 触发拆分（**注意：本任务是否拆分由你决定；如不分拆，文件会继续增长到 ~1700+ 行**）：

```
ui/components/CheckboxEditText.kt              # 薄壳 < 150 行（保留全部原 API）
ui/components/checkboxedittext/
    └── sections/
        ├── CheckboxEditTextImpl.kt            # CheckboxEditText 主入口实现
        ├── CheckboxEditRowImpl.kt             # 单行渲染实现
        └── ImageAttachmentRow.kt              # 内层 Reorderable LazyRow 实现
```

**薄壳样板**：所有原 `CheckboxEditText` / `CheckboxEditRow` 顶层 Composable 保持原签名，转发到 Impl。

---

## 三、关键风险点 + 缓解措施

| 风险 | 严重度 | 缓解措施 |
|---|---|---|
| **R1**：嵌套 Reorderable 手势冲突（内层长按 vs 外层 todo 卡片长按） | 高 | (a) key 隔离（`imagePath` vs `todoId`）；(b) Reorderable 库 `awaitFirstDown(requireUnconsumed = true)` 先消费长按；(c) Fallback：若仍冲突，把内层改为 `Press` 模式（体验略降） |
| **R2**：`Modifier.zIndex` 失效（项目踩坑：必须 `LazyItemScope` 内） | 中 | 把 `Modifier.zIndex(if (isDragging) 1f else 0f)` 写在 `ReorderableItem { isDragging -> ... }` 块内 |
| **R3**：`onMove` 内更新 list 导致 LazyRow 重组死循环 | 中 | Reorderable 库设计：内部用 `LazyListItemInfo` 索引 + `derivedStateOf` 检测差异停止推进；若死循环，在 `onMove` 加 `from.index == to.index` 早退 |
| **R4**：删除/添加图片动画与 `animateItem` 冲突 | 中 | 走 `setTodoLines` → copy 单项更新；Reorderable 默认 `animateItem()` 接管移动动画 |
| **R5**：用户在编辑页改设置不生效 | 低 | 用 `CorgiPreferences.Flow + MutableStateFlow` 同步（与 `soundEnabled` 一致） |
| **R6**："无限"选项边界 | 低 | 内部存 `-1`；UI 显示「无限」；ViewModel `if (maxImagesPerLine > 0 && size >= maxImagesPerLine) return false` |
| **R7**：`ShareCoordinator` 受图片顺序影响 | 中 | `applyImageReorder` 走 `setTodoLines` 完整更新 `_todoLines` + 自动同步 `_lineAttachmentsSnapshot`（已在 `setTodoLines` 内部处理） |
| **R8**：拆分薄壳时 `private` 跨文件 | 低 | 按 `巨石组件拆分规范.md §4`：`private fun` → `internal fun`，`private const val` → `internal const val`；外部 API 维持 `public` |

---

## 四、关键复用工具（不重新发明轮子）

| 工具 | 路径 | 用途 |
|---|---|---|
| `OptionSelectDialog<T>` | `app/src/main/java/com/corgimemo/app/ui/screens/settings/AutoBackupSettings.kt:297` | 复用作为「单行图片上限」选择弹窗（参考 L268-284 用法） |
| `SettingListCard` + `SettingItem` | `app/src/main/java/com/corgimemo/app/ui/screens/profile/components/SettingListCard.kt:55` | 复用作为设置入口 |
| `intFlow` + ESP 样板 | `CorgiPreferences.kt:382` + `AUTO_BACKUP_KEEP_COUNT` 完整链路 | 复用作为新增 `maxImagesPerLine` 的样板 |
| `rememberReorderableLazyListState` + `ReorderableItem` | `Reorderable/reorderable/src/commonMain/.../ReorderableLazyList.kt` | 复用作为图片拖拽核心 |
| `setTodoLines` 单一入口 | `TodoEditViewModel.kt`（`todoLines` StateFlow 的 setter） | 复用作为 `applyImageReorder` 写入入口（自动推 undo/redo 快照） |

---

## 五、编译验证前的检查清单（按 `.trae/rules/编译验证.md`）

> **不擅自跑 gradle**。本节是自检清单，跑前必须用 AskUserQuestion 询问用户。

| # | 检查项 | 通过 |
|---|---|---|
| 1 | `CorgiPreferences.kt` 新增 key 拼写与 `Keys` 对象中常量名一致 | □ |
| 2 | `CorgiPreferences.kt` 默认值 10 与 `TodoEditViewModel.init` 中 `MutableStateFlow(10)` 一致 | □ |
| 3 | `CheckboxEditText.kt` 薄壳对外 API（参数列表 + Composable 名称）**未变** | □ |
| 4 | 拆分新文件 `checkboxedittext/sections/*.kt` 的 `private → internal` 调整完整 | □ |
| 5 | `DraggableImageAttachment.kt` 已删 `Popup / pointerInput / graphicsLayer.translation` 拖拽代码 | □ |
| 6 | `CrossLineDragManager.kt` 已删除（`git status` 确认） | □ |
| 7 | `TodoEditScreen.kt` 已删 5 个旧回调桥接 + `crossLineDragManager` 实例化 | □ |
| 8 | `TodoEditViewModel.MAX_IMAGES_PER_LINE` 静态常量已删，UI 层引用全部改为 `viewModel.maxImagesPerLine.collectAsState()` | □ |
| 9 | `applyImageReorder` 走 `setTodoLines`（自动推快照），不绕过 undo/redo | □ |
| 10 | 导入 `sh.calvin.reorderable.*` 完整（`ReorderableItem` / `rememberReorderableLazyListState` / `draggableHandle` / `DragGestureDetector`） | □ |
| 11 | 编译前用 AskUserQuestion 询问用户「是否进行编译验证」 | □ |
| 12 | `addImagePath / removeImagePath / reorderImagePaths / setImagePaths / clearImagePaths` 删除前用 grep 全仓验证无调用方 | □ |
| 13 | 检查 `DragZoneStateMachine.kt`（todo 卡片本身用）**未**被误删 | □ |
| 14 | 长按 todo 卡片面板仍然只有 4 个选项（项目 memory 红线） | □ |

---

## 六、手动测试场景清单

### 核心场景（必须通过）

| # | 场景 | 步骤 | 预期 |
|---|---|---|---|
| 1 | 基础拖拽排序 | 一行加 5 张图片，长按第 3 张拖到第 1 位 | 重新排序为 3/1/2/4/5；保存后重新打开顺序保持 |
| 2 | 跨行拖拽 | 2 行各 3 张图片，长按第 1 行第 2 张拖到第 2 行末尾 | 第 1 行变 2 张、第 2 行变 4 张；边界自动滚动触发 |
| 3 | 解除 3 张限制 | 设置改「无限」；给一行加 15 张图片 | 「+」号不被禁用；图片行可滚动；保存/读取无丢失 |
| 4 | 设置项生效 | 编辑页打开时，进设置改上限为 5，回编辑页尝试加第 6 张 | 添加失败，提示「单行已达上限 5」 |
| 5 | 删除按钮在拖拽时 | 拖拽某张图片到一半时 | 拖拽中的图片删除按钮不显示；松手后立即恢复 |
| 6 | 外层 todo 卡片拖拽不冲突 | 主页长按 todo 卡片 | 卡片正常被拖拽；不被误判为图片行内拖拽 |
| 7 | 多卡 + 嵌套多行 | 2 个子 todo 卡片各 3 行，每行 5 张图片，长按内层图片 | 内层拖拽正常；外层 todo 卡片不响应；行间边界自动滚动 |

### 回归场景

| # | 场景 | 预期 |
|---|---|---|
| R1 | 编辑页点图片看大图 | 缩略图点击仍打开全屏查看器 |
| R2 | 编辑页加图片 → 保存 → 退出 → 重新打开 | 图片顺序、URL 完整 |
| R3 | 编辑页 undo/redo | 拖拽后的新顺序可被撤销；重做恢复 |
| R4 | 分享带图片的 todo | 分享卡片图片顺序与编辑页一致 |
| R5 | 长按 todo 卡片弹出面板 | 仍然只有 4 个选项（无 imagePaths 调试项） |

---

## 七、实施阶段（推荐 6 阶段，每阶段独立可测）

| 阶段 | 内容 | 涉及文件 | 验证 |
|---|---|---|---|
| **P1** | 数据层：`CorgiPreferences` 新增 `maxImagesPerLine` Flow + getter/setter | CorgiPreferences.kt | 编译 + get/save 往返 |
| **P2** | ViewModel：`TodoEditViewModel` 接入 Flow、删静态常量；`SettingsViewModel` 加状态 | TodoEditViewModel.kt, SettingsViewModel.kt | 编译 |
| **P3** | 设置 UI：「单行图片上限」OptionSelectDialog | SettingsScreen.kt | 编译 + 真机能改 |
| **P4** | CheckboxEditText 拆分（薄壳 + sections 子包） | CheckboxEditText.kt + 新建 sections/ | 编译 + 现有功能不破坏 |
| **P5** | 内部实现替换为 Reorderable + 删除旧文件 | CheckboxEditText.kt 内 Impl + DraggableImageAttachment.kt 瘦身 + CrossLineDragManager.kt 删除 + TodoEditScreen.kt 改回调 | 编译 + 核心场景 1-5 通过 |
| **P6** | 清理遗留 API（addImagePath 等） | TodoEditViewModel.kt | 编译 + grep 验证无调用方 |

> **不擅自跑编译**，每个阶段完成后用 AskUserQuestion 询问「是否进行 git 提交 / 是否进行编译验证」。

---

## 八、关键文件路径速查

### Critical Files for Implementation

- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\data\local\datastore\CorgiPreferences.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\viewmodel\TodoEditViewModel.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\viewmodel\SettingsViewModel.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\components\CheckboxEditText.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\components\DraggableImageAttachment.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\screens\todo\TodoEditScreen.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\screens\settings\SettingsScreen.kt`
- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\screens\settings\AutoBackupSettings.kt`（参考 `OptionSelectDialog` 用法）

### Files to Delete

- `c:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\components\CrossLineDragManager.kt`（384 行）

---

## 九、后续优化建议

1. **`DraggableVoiceAttachment.kt` 同样适用迁移**：当前语音附件也用 3 个旧拖拽回调，本次因「解除 3 张限制」不涉及语音而保留。后续可单独任务统一。
2. **无限上限的存储优化**：当前 `-1` 表示无限，UI 显示「无限」是字符串硬编码。若未来要支持 i18n，需要在 `strings.xml` 抽出 `"unlimited"`。
3. **图片压缩**：解除 3 张限制后，若用户一次加 20 张原图，内存压力陡增。可考虑引入压缩阈值（如 1080p 长边），但属额外任务。
4. **Reorderable 库升级跟进**：`libs.reorderable = "3.1.0"` 已用最新；后续升级时关注 `rememberReorderableLazyRowState` deprecation 是否彻底删除。
5. **拆分后的子包文档**：`checkboxedittext/sections/` 内每个 Impl 文件的 KDoc 应包含「对应薄壳函数」交叉引用。
