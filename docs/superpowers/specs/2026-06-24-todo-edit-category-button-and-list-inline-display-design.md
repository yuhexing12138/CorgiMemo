# 待办编辑页"分类"按钮 + 列表内联展示分类

**日期**：2026-06-24
**类型**：UI 增强 + 数据模型扩展（加 1 个枚举值）
**状态**：已批准，待实施

## 一、背景与目标

### 1.1 现状

- 数据模型 `Category.kt` 已有 5 个枚举值：`STUDY(0)` / `WORK(1)` / `LIFE(2)` / `SPORT(3)` / `CUSTOM(4)`，对应默认分类"学习/工作/生活/运动"+ 自定义
- `CategoryRepository.initDefaultCategories()` 写入 4 个默认分类
- `CategoryPickerSheet`（`ui/components/CategoryPickerSheet.kt`）是**底部弹窗**，被灵感编辑页用于"移动到分组"功能
- 待办编辑页 `TodoEditScreen` 当前**没有"分类"按钮**：分类仅在保存时由智能推荐隐式设置，用户在编辑过程中无法主动选择
- 待办卡片 `TodoListItem` 在提醒行**下方**独立渲染一行"分类行"（图标 + 名称 + 背景块），但用户反馈该行"在待办卡片上并不可见"
- `CheckboxEditText.kt` 当前底部工具栏单行 Row：`[设置提醒] ... [中优先级] ... [完成]`

### 1.2 目标

1. **数据层扩展**：新增 `ENTERTAINMENT` 默认分类类型
2. **编辑页新增"分类"按钮**：与"优先级"按钮同行（优先级 + 分类），位于"设置提醒"行**上一行**
3. **分类选择 AlertDialog 弹窗**：5 个默认分类标签 + 自定义输入
4. **待办卡片内联展示分类**：分类名称出现在提醒行**左侧**（含阴影），删除原独立分类行
5. **与灵感页隔离**：所有改动不得影响灵感编辑页（`InspirationEditViewModel` / `InspirationEditScreen`）行为

### 1.3 设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| "娱乐"类型实现 | 新增 `CategoryType.ENTERTAINMENT = 5` | 不破坏现有枚举语义；用户确认 |
| 弹窗形式 | 新建 `CategorySelectorDialog`（AlertDialog） | 用户明确要求"按钮下方居中模态弹窗"，复用 `CategoryPickerSheet`（底部弹窗）位置/语义不符 |
| 选中即确认 | 选中标签自动保存+关闭 | 用户确认："选中后自动保存+关闭弹窗" |
| 分类按钮位置 | 优先级按钮右侧，8dp 间距 | 用户要求 |
| 优先级按钮位置 | 移到"设置提醒"上一行 | 用户要求 |
| 列表分类位置 | 内联到提醒行左侧 | 用户确认："原独立分类行不可见，可完整删除" |
| 阴影实现 | 双层 Box：外层 `Box.offset(2.dp, 2.dp)` + `Modifier.blur(4.dp)` 模拟阴影偏移；内层 `Box` 渲染实际内容 | `Modifier.shadow(elevation)` 无法精确控制偏移量为 2px；`drawBehind` 与 `background` 修饰符顺序有歧义；双层 Box 方案最清晰可读 |
| 数据层共享策略 | 新增枚举 + 默认分类写入 复用现有 `CategoryRepository` 流程 | 灵感页自动同步显示新增"娱乐"分类（数据层加法，UI 端无侵入） |

## 二、范围

### 2.1 包含

- `data/model/Category.kt`：新增 `ENTERTAINMENT = 5` + `DefaultCategoryName.ENTERTAINMENT = "娱乐"`
- `data/repository/CategoryRepository.kt`：`initDefaultCategories()` 追加"娱乐"记录
- `ui/components/CategorySelectorDialog.kt`：**新建** AlertDialog 弹窗
- `ui/components/CheckboxEditText.kt`：底部工具栏从单行 Row 重构为两行 Column；新增 category 参数与渲染
- `ui/screens/todo/TodoEditScreen.kt`：新增 `showCategoryDialog` 状态 + 接线到 `viewModel.setCategoryId`
- `ui/components/TodoListItem.kt`：删除独立分类行；分类内联到提醒行左侧
- **不动** `InspirationEditScreen.kt` / `InspirationEditViewModel.kt`：灵感页维持原样

### 2.2 不包含

- 不改 `TodoItem` / `SubTask` / 数据库 schema
- 不改 `CategoryPickerSheet`（保留给灵感页使用）
- 不改 `HomeViewModel`：现有 `categoryName` / `categoryIcon` 参数已能满足
- 不改 `ReminderPickerBottomSheet`、`PriorityBadge` 等其他组件
- 不实现"删除分类"功能

## 三、详细设计

### 3.1 数据层变更

#### 3.1.1 [Category.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/Category.kt)

```kotlin
object CategoryType {
    const val STUDY = 0
    const val WORK = 1
    const val LIFE = 2
    const val SPORT = 3
    const val CUSTOM = 4
    const val ENTERTAINMENT = 5  // 新增
}

object DefaultCategoryName {
    const val STUDY = "学习"
    const val WORK = "工作"
    const val LIFE = "生活"
    const val SPORT = "运动"
    const val ENTERTAINMENT = "娱乐"  // 新增
}
```

**为什么是 5 而不是插在 3 前面？** 现有数据库记录使用 0-4，避免改动既有数据的 type 值。仅在写入新分类时使用 5。

#### 3.1.2 [CategoryRepository.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt)

`initDefaultCategories()` 函数：在现有写入 STUDY/WORK/LIFE/SPORT 逻辑后追加：

```kotlin
if (getCategoryByType(CategoryType.ENTERTAINMENT) == null) {
    insertCategory(Category(
        name = DefaultCategoryName.ENTERTAINMENT,
        type = CategoryType.ENTERTAINMENT,
        isDefault = true
    ))
}
```

**幂等性保证**：与现有 4 个分类写入逻辑一致，先查询再插入。

**对灵感页的影响**：灵感页使用 `CategoryPickerSheet` 展示所有分类，新增"娱乐"分类后灵感页弹窗也会多一项（**符合预期**：数据层加法，UI 端动态展示，不影响灵感页业务逻辑）。

### 3.2 新增组件：CategorySelectorDialog

**新建** [ui/components/CategorySelectorDialog.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CategorySelectorDialog.kt)

```kotlin
/**
 * 分类选择弹窗（AlertDialog）
 *
 * - 5 个默认分类以 Tag 标签形式展示
 * - 选中标签自动保存 + 关闭弹窗
 * - "自定义"按钮点击展开输入框
 * - 点击外部或"取消"按钮关闭
 */
@Composable
fun CategorySelectorDialog(
    categories: List<Category>,
    currentCategoryId: Long?,
    onDismiss: () -> Unit,
    onCategorySelected: (Long, String) -> Unit  // (id, name)
)
```

**核心实现要点**：
- 使用 `AlertDialog`，标题"选择分类"
- 主体 `Column { FlowRow { ... } }` 容纳标签 + 自定义按钮
- 默认分类 `Tag`：`Surface(shape = CircleShape, color = categoryColor.copy(alpha=0.15f))` 包裹 emoji + 文字；选中时边框 `border = BorderStroke(2.dp, categoryColor)`
- "自定义"按钮点击：展开 `OutlinedTextField` + "确定"按钮；输入为空时禁用；确定后调用 `onCategorySelected(0L, customName)` 由调用方写入数据库
- 点击外部触发 `onDismissRequest = onDismiss`
- 自定义分类的临时状态在 dialog 内部管理（`var customInput by remember { mutableStateOf("") }`）

**颜色映射**（与 `CategoryPickerSheet` 保持一致）：
- STUDY → 薄荷绿 `Color(0xFF7EC8A0)`
- WORK → 天空蓝 `Color(0xFF90CAF9)`
- LIFE → 暖橙色 `Color(0xFFFFB74D)`
- SPORT → 运动蓝 `Color(0xFF7EB8DA)`
- ENTERTAINMENT → 紫粉 `Color(0xFFE1BEE7)`（新增）
- CUSTOM → 默认紫色 `Color(0xFFB8A0D4)`

### 3.3 CheckboxEditText 底部工具栏重构

**当前**（[CheckboxEditText.kt:480-606](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt#L480-L606)）：

```
Row(单行) {
    设置提醒 ... 中优先级 ... Spacer.weight(1f) ... 完成
}
```

**目标**：

```
Column {
    Row1 { 中优先级  Spacer(8dp)  分类  Spacer.weight(1f) }
    Row2 { 设置提醒 ... Spacer.weight(1f) ... 完成 }
}
```

#### 函数签名新增参数

```kotlin
@Composable
fun CheckboxEditText(
    // ... 既有参数 ...
    /** 当前分组的分类 ID（null 表示未设置） */
    categoryId: Long? = null,
    /** 当前分组的分类名称（用于按钮显示） */
    categoryName: String? = null,
    /** 分类按钮点击回调 */
    onCategoryClick: (() -> Unit)? = null,
    // ...
)
```

#### 内部单容器函数 [CheckboxEditContainer](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt#L420) 同样新增上述 3 个参数。

#### Row1 渲染

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // 复用现有"优先级按钮"Box 块（line 562-591）
    Box(
        modifier = Modifier
            .clickable(enabled = onPriorityClick != null) { onPriorityClick?.invoke() }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) { /* ... 颜色圆点 + priorityLabel ... */ }

    Spacer(modifier = Modifier.width(8.dp))

    // 新增"分类"按钮
    if (onCategoryClick != null) {
        Box(
            modifier = Modifier
                .clickable { onCategoryClick?.invoke() }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
                .widthIn(max = 120.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📋",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = categoryName ?: "分类",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (categoryName != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}
```

#### Row2 渲染

保持不变（`Row` 内含提醒按钮 + Spacer.weight(1f) + 完成按钮），整体作为 `Column` 的第二个子项。

#### 行为契约

- `CheckboxEditText` 的外层调用方（`TodoEditScreen`）需将 `onCategoryClick` 接到 `showCategoryDialog = true`
- 已完成态的视觉降权逻辑不在此层处理（`CheckboxEditText` 不感知 `isCompleted`，由 `TodoEditScreen` 决定禁用还是允许编辑）

### 3.4 TodoEditScreen 接线

**新增状态**（文件顶部）：

```kotlin
var showCategoryDialog by remember { mutableStateOf(false) }
```

**收集 viewModel 状态**：

```kotlin
val categories by viewModel.categories.collectAsState()
val categoryId by viewModel.categoryId.collectAsState()
val currentCategory = categories.find { it.id == categoryId }
```

**CheckboxEditText 调用处**（[TodoEditScreen.kt:927-1012](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt#L927-L1012)）：

```kotlin
CheckboxEditText(
    // ... 既有参数 ...
    categoryId = categoryId,
    categoryName = currentCategory?.name,
    onCategoryClick = { showCategoryDialog = true },
    // ... 其他参数 ...
)
```

**弹窗渲染**（紧跟优先级弹窗的 `if (showPriorityDialog?.let { ... }` 之后）：

```kotlin
if (showCategoryDialog) {
    CategorySelectorDialog(
        categories = categories,
        currentCategoryId = categoryId,
        onDismiss = { showCategoryDialog = false },
        onCategorySelected = { id, name ->
            if (id > 0L) {
                // 默认分类：直接设置 ID
                viewModel.setCategoryId(id)
            } else {
                // 自定义分类：需写入数据库后获取 ID（异步）
                viewModel.createCustomCategory(name) { newId ->
                    viewModel.setCategoryId(newId)
                }
            }
            showCategoryDialog = false
        }
    )
}
```

**TodoEditViewModel 新增方法**：

```kotlin
/**
 * 创建自定义分类
 *
 * @param name 分类名称
 * @param onCreated 创建成功回调，返回新分类 ID
 */
fun createCustomCategory(name: String, onCreated: (Long) -> Unit) {
    viewModelScope.launch {
        val newCategory = Category(
            name = name,
            type = CategoryType.CUSTOM,
            isDefault = false
        )
        val newId = categoryRepository.insertCategory(newCategory)
        // 刷新分类列表
        _categories.value = categoryRepository.getAllCategoriesList()
        onCreated(newId)
    }
}
```

### 3.5 TodoListItem 列表内联展示

#### 3.5.1 删除原独立分类行

**删除** [TodoListItem.kt:395-427](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L395-L427) 整段：

```kotlin
// 分类行
if (categoryName != null) {
    Row(...) {
        Text(categoryIcon ?: "📋", ...)
        Text(categoryName, ...)
    }
}
```

#### 3.5.2 提醒行内联分类

**修改** [TodoListItem.kt:344-393](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L344-L393)：在 `if (todo.reminderTime != null)` 之前插入分类渲染。

```kotlin
// 提醒时间 + 附件数量（聚合：父 + 所有子任务）
val aggregateCounts = aggregateAttachmentCounts(todo, subTasks)
val hasReminder = todo.reminderTime != null
val hasAttachments = aggregateCounts.first > 0 || aggregateCounts.second > 0
val hasCategory = categoryName != null

if (hasCategory || hasReminder || hasAttachments) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        // ===== 分类（内联，阴影效果）=====
        if (hasCategory) {
            CategoryTagWithShadow(
                categoryName = categoryName!!,
                categoryIcon = categoryIcon,
                isCompleted = todo.status == 1
            )
            Spacer(modifier = Modifier.width(8.dp))   // 分类与提醒/附件间 1 空格间距
        }

        if (hasReminder) {
            // ... 现有提醒渲染 ...
        }

        if (hasAttachments) {
            // ... 现有附件渲染 ...
        }
    }
}
```

#### 3.5.3 新增私有组件 CategoryTagWithShadow

在 `TodoListItem.kt` 文件内部新增：

```kotlin
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size  // 已有

/**
 * 带阴影效果的分类标签
 *
 * 阴影参数：水平偏移 2px，垂直偏移 2px，模糊半径 4px，颜色 rgba(0,0,0,0.1)
 * 字号 12sp；已完成态使用 CompletedColors.Text 降权
 *
 * 实现方式：双层 Box
 * - 外层 Box：offset(2.dp, 2.dp) 模拟阴影偏移 + 半透明黑背景 + blur(4.dp) 模拟模糊
 * - 内层 Box：渲染实际内容（背景色 + padding + Row）
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CategoryTagWithShadow(
    categoryName: String,
    categoryIcon: String?,
    isCompleted: Boolean
) {
    val textColor = if (isCompleted) CompletedColors.Text
                    else MaterialTheme.colorScheme.primary
    val bgColor = if (isCompleted) CompletedColors.Text.copy(alpha = 0.12f)
                  else MaterialTheme.colorScheme.primaryContainer

    Box(contentAlignment = Alignment.Center) {
        // 外层：阴影（偏移 2dp 半透明黑 + 4dp 模糊）
        // 用 Modifier.matchParentSize() 让外层 Box 与内层 Row 同尺寸
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(
                    color = Color(0x1A000000),  // rgba(0, 0, 0, 0.1)
                    shape = RoundedCornerShape(4.dp)
                )
                .blur(radius = 4.dp)  // 模糊半径 4px
        )
        // 内层：实际内容
        Row(
            modifier = Modifier
                .background(color = bgColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryIcon ?: "📋",
                fontSize = 12.sp,
                color = textColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = categoryName,
                fontSize = 12.sp,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

**关键实现说明**：
- 外层阴影 Box 用 `offset(2.dp, 2.dp)` 偏移
- `Color(0x1A000000)` = `rgba(0, 0, 0, 0.1)`，与需求一致
- `Modifier.blur(radius = 4.dp)`（需 import `androidx.compose.ui.draw.blur`）实现模糊
- 内外两层独立 Box；外层无内容仅作阴影层；内层有 padding 和文本
- 内外两层尺寸由 Compose 的 `Box` 默认测量规则对齐

**回退方案**：若 `Modifier.blur` 在项目中编译报错（实验性 API），回退为：
```kotlin
// 外层改为：仅黑色半透明矩形 + offset（无 blur，视觉上仍有阴影感）
Box(
    modifier = Modifier
        .offset(x = 2.dp, y = 2.dp)
        .background(Color(0x1A000000), RoundedCornerShape(4.dp))
)
```

## 四、UI 流程图

### 编辑页分类选择流程

```
┌────────────────────────────┐
│  TodoEditScreen           │
│  ┌────────────────────┐    │
│  │ CheckboxEditText  │    │
│  │ Column {           │    │
│  │   Row1:            │    │
│  │     [⬤中] [📋分类] │ <──┐
│  │   Row2:            │    │ 点"分类"
│  │     [🔔提醒] ...   │    │
│  │   ...              │    │
│  └────────────────────┘    │
│                            │
│  showCategoryDialog=true ──┘
│  ↓
│  ┌────────────────────────┐
│  │ AlertDialog            │
│  │ "选择分类"             │
│  │ [📚学习] [💼工作] ...  │
│  │ [🆕 自定义]            │
│  └────────────────────────┘
│  ↓ 点"📚学习"
│  onCategorySelected(id=studyId, name="学习")
│  showCategoryDialog=false
│  viewModel.setCategoryId(studyId)
│  ↓
│  Row1 的"分类"按钮文字 = "📋 学习"
```

### 卡片渲染流程

```
HomeViewModel 加载 todos + categories
  ↓ 注入 categoryName, categoryIcon 给 TodoListItem
TodoListItem
  ↓ if (categoryName != null) 渲染 CategoryTagWithShadow（带阴影）
  ↓ 删除原独立分类行
  ↓ 提醒行内联展示
卡片显示：📚 学习  [闹钟] 明天 9:00   🎤×1 🖼×2
```

## 五、验收标准

### 5.1 编辑页

- [ ] 默认分类列表包含 5 项：学习/工作/生活/娱乐/运动
- [ ] 优先级按钮在提醒按钮**上一行**
- [ ] 优先级按钮和分类按钮在同一行，间距 8dp
- [ ] 分类按钮宽度自适应文本，超过 120dp 时显示省略号
- [ ] 点击分类按钮弹出 AlertDialog，标题"选择分类"
- [ ] AlertDialog 包含 5 个默认分类标签 + 1 个"自定义"按钮
- [ ] 点击标签自动保存分类并关闭弹窗
- [ ] 点击"自定义"展开输入框；输入非空时确定按钮可用
- [ ] 点击弹窗外部或取消按钮关闭弹窗
- [ ] 选中后按钮文字立即更新为分类名

### 5.2 列表卡片

- [ ] 提醒行左侧显示分类名（带阴影）
- [ ] 分类与提醒时间之间有 1 空格间距
- [ ] 分类字号 12sp
- [ ] 阴影偏移 2px/2px，模糊 4px，颜色 rgba(0,0,0,0.1)
- [ ] 原独立分类行已删除
- [ ] 已完成态分类使用 CompletedColors.Text 灰色
- [ ] 灵感页不受影响（验证灵感编辑页"选择分类"流程仍正常）
- [ ] 灵感页"分类"选择弹窗也显示新增的"娱乐"分类（数据层共享验证）

### 5.3 数据层

- [ ] `Category.kt` 新增 `ENTERTAINMENT = 5`
- [ ] `CategoryRepository.initDefaultCategories()` 写入"娱乐"分类
- [ ] 数据库无 schema 变更（type 字段已为 INTEGER，5 是合法值）
- [ ] 既有数据不受影响

## 六、风险与兼容性

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 灵感页 `CategoryPickerSheet` 自动展示新增"娱乐" | 灵感页弹窗多一项 | 用户预期：分类库扩展应全局生效；如灵感页不希望显示，可在 `CategoryPickerSheet` 调用处过滤 |
| `CheckboxEditText` 改动可能影响其他调用方 | 误改 | 全项目只有 `TodoEditScreen` 一处调用（已验证），增加参数使用默认值，不破坏既有调用 |
| 阴影 `drawBehind` 实现复杂 | 视觉效果偏差 | 若实现效果不佳，回退方案：使用 `Box` 叠层 + `Modifier.offset(2.dp, 2.dp).background(Color.Black.copy(alpha=0.1f), RoundedCornerShape(4.dp)).blur(4.dp)` 模拟 |
| 自定义分类异步写入时序 | 点击"确定"后 ID 还未返回 | 使用回调 `onCreated(newId)` 异步更新 `_categoryId.value`；弹窗立即关闭不影响体验 |

## 七、文件变更清单

| # | 文件路径 | 类型 | 主要变更 |
|---|----------|------|----------|
| 1 | [Category.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/Category.kt) | 修改 | 新增 `ENTERTAINMENT=5` + `DefaultCategoryName.ENTERTAINMENT="娱乐"` |
| 2 | [CategoryRepository.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt) | 修改 | `initDefaultCategories()` 追加"娱乐"写入 |
| 3 | [CategorySelectorDialog.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CategorySelectorDialog.kt) | **新建** | AlertDialog 弹窗 |
| 4 | [CheckboxEditText.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt) | 修改 | 底部工具栏两行布局；新增 category 参数 |
| 5 | [TodoEditScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | 修改 | 状态+接线 |
| 6 | [TodoEditViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt) | 修改 | 新增 `createCustomCategory()` |
| 7 | [TodoListItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) | 修改 | 删除独立分类行；内联展示 |

**未变更文件**（已验证与本次需求无关）：
- `InspirationEditScreen.kt` / `InspirationEditViewModel.kt`
- `Inspiration.kt` 数据模型
- `CategoryPickerSheet.kt`（保留给灵感页使用）
- `HomeViewModel.kt`
- 任何 DB migration（无 schema 变更）
