# V2.0 优化继续实施计划 (Sprint 1 详细版)

> **版本**: v2.1 (继续执行版)
> **日期**: 2026-01-15
> **状态**: 🚀 执行中
> **当前阶段**: Sprint 1 - P1 高优先级

---

## 📊 当前进度总览

### ✅ 已完成任务 (V1.0 + V2.0 部分完成)

| 任务 | 文件 | 状态 | 行数 |
|------|------|------|------|
| 排序弹窗 | SortBottomSheet.kt | ✅ 完成 | ~200 |
| 操作弹窗 | ActionBottomSheet.kt | ✅ 完成 | ~150 |
| 待办操作弹窗 | TodoActionSheet.kt | ✅ 完成 | ~180 |
| 分组选择器 | CategoryPickerSheet.kt | ✅ 完成 | ~200 |
| 删除确认对话框 | DeleteConfirmDialog.kt | ✅ 完成 | ~120 |
| 角落装饰组件 | CornerDecoration.kt | ✅ 完成 | ~100 |
| 装饰内容盒子 | DecoratedContentBox.kt | ✅ 完成 | ~80 |
| 编辑工具栏 | EditToolbar.kt | ✅ 完成 | ~250 |
| 富文本编辑器核心 | RichTextEditor.kt | ✅ 完成 | 384 |
| 格式化工具栏 | TextFormatToolbar.kt | ✅ 完成 | 328 |

**已完成总计**: 10 个文件, ~1,990 行代码

---

## 🎯 下一步实施计划 (Sprint 1 剩余任务)

### 🔴 任务 1: A.1.3 - 创建 MarkdownParser.kt (预计 2-3 小时)

**目标**: 为富文本编辑器提供 Markdown 导入导出功能

**技术方案**:
```
utils/MarkdownParser.kt           ← 新建 (~150 行)
├── parseMarkdownToAnnotatedString()
│   ├── 解析 **bold** → SpanStyle.Bold
│   ├── 解析 *italic* → SpanStyle.Italic
│   ├── 解析 ~~strikethrough~~ → LineThrough
│   ├── 解析 - [ ] / - [x] → 待办列表
│   └── 解析 # 标题 → ParagraphStyle.Heading
└── exportAnnotatedStringToMarkdown()
    ├── 导出 Bold → **text**
    ├── 导出 Italic → *text*
    └── 导出 Strikethrough → ~~text~~
```

**接口设计**:
```kotlin
object MarkdownParser {
    fun parse(markdown: String): AnnotatedString
    fun export(annotatedString: AnnotatedString): String
}
```

**关键实现细节**:
- 使用正则表达式匹配 Markdown 语法
- 支持嵌套格式（如 **粗体_斜体_**）
- 处理特殊字符转义
- 保持光标位置映射关系（用于编辑时双向同步）

**依赖关系**: 无（独立模块）

---

### 🔴 任务 2: A.2 - 图片附件功能完善 (预计 2 小时)

#### 2.1 创建 InlineImagePreview.kt (~100 行)

**目标**: 在富文本编辑器内显示内联图片预览

**技术方案**:
```kotlin
@Composable
fun InlineImagePreview(
    imageUri: Uri,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    maxWidth: Dp = 300.dp
)
```

**功能特性**:
- 显示图片缩略图（最大宽度 300dp，高度自适应）
- 右上角删除按钮（× 图标）
- 点击放大查看（可选）
- 加载状态指示器（Placeholder）
- 圆角边框（符合 UI 设计规范 16dp）

**UI 布局**:
```
┌─────────────────────────────┐
│  ┌─────────────────────┐ × │  ← 删除按钮
│  │                     │    │
│  │     图片预览区域      │    │
│  │   (maxWidth=300dp)  │    │
│  │                     │    │
│  └─────────────────────┘    │
└─────────────────────────────┘
```

#### 2.2 集成 ImagePickerDialog 到 TodoEditScreen

**修改文件**: `TodoEditScreen.kt`

**集成点**:
```kotlin
// 在 TodoEditScreen 中添加状态
var showImagePicker by remember { mutableStateOf(false) }
var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

// 在 Scaffold 中添加 ImagePickerDialog
if (showImagePicker) {
    ImagePickerDialog(
        onCameraSelected = { /* 启动相机 */ },
        onGallerySelected = { /* 打开相册 */ },
        onDismiss = { showImagePicker = false }
    )
}

// 在 DecoratedContentBox 内显示图片预览
selectedImageUris.forEach { uri ->
    InlineImagePreview(
        imageUri = uri,
        onDelete = { /* 从列表移除 */ }
    )
}
```

**对接 EditToolbar 回调**:
```kotlin
EditToolbar(
    onPhotoClick = { showImagePicker = true }, // 触发图片选择
    // ... 其他回调
)
```

**依赖关系**: 
- ✅ ImagePickerDialog.kt 已存在（直接复用）
- ✅ InlineImagePreview.kt 需先创建

---

### 🔴 任务 3: A.3 - 背景色选择器 (预计 2-3 小时)

#### 3.1 创建 ColorPickerBottomSheet.kt (~180 行)

**目标**: 提供背景色选择面板（12 种预设色 + 自定义颜色入口）

**技术方案**:
```kotlin
@Composable
fun ColorPickerBottomSheet(
    sheetState: SheetState,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
)
```

**颜色方案** (12 种预设色):
```kotlin
val PRESET_COLORS = listOf(
    ColorItem(Color(0xFFFFFFFF), "白色", true),      // 默认选中
    ColorItem(Color(0xFFFFF5F0), "暖白"),
    ColorItem(Color(0xFFFFE0C0), "浅橙"),
    ColorItem(Color(0xFFE3F2FD), "浅蓝"),
    ColorItem(Color(0xFFE8F5E9), "浅绿"),
    ColorItem(Color(0xFFFFF3E0), "暖黄"),
    ColorItem(Color(0xFFFCE4EC), "浅粉"),
    ColorItem(Color(0xFFF3E5F5), "浅紫"),
    ColorItem(Color(0xFFE0F7FA), "浅青"),
    ColorItem(Color(0xFFFFF9C4), "浅黄绿"),
    ColorItem(Color(0xFF37474F), "深灰"),
    ColorItem(Color(0xFF263238), "近黑")
)
```

**UI 布局**:
```
┌─────────────────────────────────┐
│  ════════════════════════════   │  ← 拖拽手柄
│                                 │
│         选择背景颜色              │  ← 标题
│                                 │
│  ┌───┐ ┌───┐ ┌───┐ ┌───┐       │
│  │ ○ │ │ ● │ │ ○ │ │ ○ │       │  ← 4×3 网格布局
│  └───┘ └───┘ └───┘ └───┘       │
│  ┌───┐ ┌───┐ ┌───┐ ┌───┐       │
│  │ ○ │ │ ○ │ │ ○ │ │ ○ │       │
│  └───┘ └───┘ └───┘ └───┘       │
│  ┌───┐ ┌───┐ ┌───┐ ┌───┐       │
│  │ ○ │ │ ○ │ │ ○ │ │ ○ │       │
│  └───┘ └───┘ └───┘ └───┘       │
│                                 │
│  ┌─────────────────────────┐    │
│  │    🎨 自定义颜色...      │    │  ← 高级选项按钮
│  └─────────────────────────┘    │
│                                 │
└─────────────────────────────────┘
```

**交互逻辑**:
- 点击颜色圆圈 → 即时应用到 DecoratedContentBox 背景
- 当前选中色显示 ✓ 对勾标记
- 默认选中白色（第一项）
- 底部"自定义颜色"按钮（P3 阶段实现，暂时灰显或隐藏）

#### 3.2 集成到 TodoEditScreen

**修改文件**:
- `TodoEditScreen.kt` - 添加背景色状态和选择器调用
- `DecoratedContentBox.kt` - 接收 backgroundColor 参数（可能需要修改签名）

**集成代码示例**:
```kotlin
var backgroundColor by remember { mutableStateOf<Color>(Color.White) }
var showColorPicker by remember { mutableStateOf(false) }

// 在 Composable 中
if (showColorPicker) {
    ColorPickerBottomSheet(
        sheetState = rememberModalBottomSheetState(),
        selectedColor = backgroundColor,
        onColorSelected = { color ->
            backgroundColor = color
        },
        onDismiss = { showColorPicker = false }
    )
}

// 应用到内容区
DecoratedContentBox(
    backgroundColor = backgroundColor, // 新增参数
    content = { /* 编辑器内容 */ }
)

// EditToolbar 回调
EditToolbar(
    onBackgroundClick = { showColorPicker = true },
    // ...
)
```

**依赖关系**: 无（独立模块）

---

## 📋 Sprint 1 完成后的验收清单

### 功能验证
- [ ] **A.1.3**: Markdown 文本可正确解析为带样式的 AnnotatedString
- [ ] **A.1.3**: 带样式的文本可正确导出为 Markdown 格式
- [ ] **A.2.1**: 图片可在编辑器内以内联方式显示
- [ ] **A.2.1**: 图片支持删除操作
- [ ] **A.2.2**: 点击工具栏"📷照片"按钮可弹出 ImagePickerDialog
- [ ] **A.2.2**: 选择图片后可在编辑器内预览
- [ ] **A.3.1**: 背景色选择器显示 12 种预设颜色
- [ ] **A.3.1**: 选择颜色后实时应用到内容区背景
- [ ] **A.3.2**: 背景色设置在页面跳转后保持（需持久化）

### 代码质量检查
- [ ] 所有新文件有完整的 KDoc 注释
- [ ] 符合项目编码规范（函数级注释、中文注释）
- [ ] import 语句完整无缺失
- [ ] 无硬编码字符串（使用 stringResource 或提取为常量）
- [ ] UI 组件符合 Material Design 3 规范

### 编译验证
- [ ] Gradle Build 成功（无编译错误）
- [ ] Lint 检查通过
- [ ] 无警告信息

---

## 🔄 后续 Sprint 计划预览

### Sprint 2: P2 中优先级 (交互体验)
- **B.1**: 拖拽排序（ReorderableLazyColumn + DragHandle）
- **B.2**: 多级排序（MultiSortSheet + MultiSortConfig）
- **B.3**: 动画过渡（ListReorderAnimation + AnimatableItemCard）

### Sprint 3: P3 低优先级 (质量保障)
- **C.1**: 国际化支持（strings.xml 扩展 + en/ja 资源 + LocaleHelper）
- **C.2**: 无障碍优化（AccessibilityExtensions + Semantics API）
- **C.3**: 单元测试（HomeViewModel/Preferences/Repository 测试）

---

## ⚠️ 注意事项

### 技术风险
1. **Markdown 解析复杂度**: 嵌套格式解析需仔细处理正则表达式边界情况
2. **图片内存管理**: 大图可能导致 OOM，需限制图片尺寸（max 1024x1024）
3. **背景色持久化**: 需要在 Todo Entity 中新增 `background_color` 字段并处理数据库迁移

### 性能考虑
- 图片使用 Coil/Glide 异步加载（避免主线程阻塞）
- Markdown 解析在后台线程执行（使用 Dispatchers.Default）
- 背景色切换使用 animateColorAsState 平滑过渡

### 用户规则遵循
- ✅ 所有新代码添加中文注释
- ✅ 函数级 KDoc 注释
- ✅ 使用 Windows 系统命令
- ✅ 仅使用中文交流
- ✅ 编译前询问用户确认

---

## 📝 总结

本计划文档详细描述了 **Sprint 1 剩余的 3 个任务**：

1. **MarkdownParser.kt** - 富文本导入导出桥梁
2. **InlineImagePreview.kt + ImagePickerDialog 集成** - 图片附件功能闭环
3. **ColorPickerBottomSheet.kt** - 背景色个性化定制

预计新增代码量：~530 行（MarkdownParser 150 + InlineImagePreview 100 + ColorPickerBottomSheet 180 + 集成修改 100）

完成后 **Sprint 1 (P1 高优先级)** 将全部完成，可以进入 **Sprint 2 (P2 中优先级)** 的拖拽排序、多级排序和动画过渡功能开发。

---

**文档版本**: v2.1
**最后更新**: 2026-01-15
**作者**: AI Assistant (Trae)
