# 待办编辑页"分类"按钮 + 列表内联展示分类 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在待办编辑页底部工具栏新增"分类"按钮（与"优先级"按钮同行），点击弹出 AlertDialog 选择分类（含 5 个默认分类 + 自定义）；同时把分类从 TodoListItem 的独立行迁移到提醒行**左侧**（带阴影），并删除原不可见的独立分类行。

**Architecture:** 5 文件协同改动 + 1 新建组件。数据层只在 `Category.kt` 加 1 个枚举值 + `CategoryRepository.initDefaultCategories()` 追加 1 条记录（无 DB schema 变更）。新弹窗 `CategorySelectorDialog` 用 `AlertDialog` 实现并支持回调注入。`CheckboxEditText` 底部栏从单行 Row 重构为两行 Column（不影响灵感页，因为灵感页用 `RichTextEditor`）。列表展示用"双层 Box + offset + blur"实现精确阴影。

**Tech Stack:** Jetpack Compose / Material 3 / Room / Kotlin

**Spec:** [2026-06-24-todo-edit-category-button-and-list-inline-display-design.md](../specs/2026-06-24-todo-edit-category-button-and-list-inline-display-design.md)

---

## 文件清单

| 类型 | 路径 | 职责 |
|------|------|------|
| 修改 | `app/src/main/java/com/corgimemo/app/data/model/Category.kt` | 新增 `ENTERTAINMENT=5` + `DefaultCategoryName.ENTERTAINMENT="娱乐"` |
| 修改 | `app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt` | `initDefaultCategories()` 追加"娱乐"记录 |
| 新建 | `app/src/main/java/com/corgimemo/app/ui/components/CategorySelectorDialog.kt` | AlertDialog 弹窗（5 默认 + 自定义） |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt` | 底部栏两行布局 + 新增 category 参数 |
| 修改 | `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt` | 新增 `createCustomCategory()` 方法 |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt` | 状态+接线 |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | 删除原独立分类行；新增 `CategoryTagWithShadow`；内联到提醒行左侧 |

**未变更文件**（已验证与本次需求无关）：
- `InspirationEditScreen.kt` / `InspirationEditViewModel.kt`：灵感页使用 `RichTextEditor` 而非 `CheckboxEditText`
- `CategoryPickerSheet.kt`：保留给灵感页使用
- `HomeViewModel.kt`：现有 `categoryName` / `categoryIcon` 参数已满足
- 任何 DB migration 文件（无 schema 变更）

---

## Task 1: Category 模型扩展 - 新增 ENTERTAINMENT

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/model/Category.kt`

- [ ] **Step 1: 在 CategoryType 中新增 ENTERTAINMENT**

打开 `Category.kt`，在 `object CategoryType` 的 `CUSTOM = 4` 之后追加：

```kotlin
object CategoryType {
    const val STUDY = 0
    const val WORK = 1
    const val LIFE = 2
    const val SPORT = 3
    const val CUSTOM = 4
    const val ENTERTAINMENT = 5
}
```

- [ ] **Step 2: 在 DefaultCategoryName 中新增 ENTERTAINMENT**

在同一文件 `object DefaultCategoryName` 的 `SPORT = "运动"` 之后追加：

```kotlin
object DefaultCategoryName {
    const val STUDY = "学习"
    const val WORK = "工作"
    const val LIFE = "生活"
    const val SPORT = "运动"
    const val ENTERTAINMENT = "娱乐"
}
```

- [ ] **Step 3: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。无编译错误。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/data/model/Category.kt
git commit -m "feat(category): add ENTERTAINMENT category type (id=5) and default name"
```

---

## Task 2: CategoryRepository 初始化"娱乐"分类

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt`

- [ ] **Step 1: 修改 initDefaultCategories() 的注释和逻辑**

打开 `CategoryRepository.kt`，找到 `initDefaultCategories()` 函数（约第 28-44 行），替换为：

```kotlin
/**
 * 初始化默认分类
 * 如果数据库中没有分类，则创建学习、工作、生活、娱乐、运动五个默认分类
 *
 * 幂等性：使用 "不存在则插入" 模式，多次调用安全
 */
suspend fun initDefaultCategories() = withContext(ioDispatcher) {
    val existingCategories = categoryDao.getAllCategoriesList()
    if (existingCategories.isEmpty()) {
        val defaultCategories = listOf(
            Category(name = DefaultCategoryName.STUDY, type = CategoryType.STUDY, isDefault = true),
            Category(name = DefaultCategoryName.WORK, type = CategoryType.WORK, isDefault = true),
            Category(name = DefaultCategoryName.LIFE, type = CategoryType.LIFE, isDefault = true),
            Category(name = DefaultCategoryName.SPORT, type = CategoryType.SPORT, isDefault = true),
            Category(name = DefaultCategoryName.ENTERTAINMENT, type = CategoryType.ENTERTAINMENT, isDefault = true)
        )
        categoryDao.insertAll(defaultCategories)
    } else {
        val hasSport = existingCategories.any { it.type == CategoryType.SPORT }
        if (!hasSport) {
            categoryDao.insert(Category(name = DefaultCategoryName.SPORT, type = CategoryType.SPORT, isDefault = true))
        }
        // 兼容老用户：补齐"娱乐"分类
        val hasEntertainment = existingCategories.any { it.type == CategoryType.ENTERTAINMENT }
        if (!hasEntertainment) {
            categoryDao.insert(Category(name = DefaultCategoryName.ENTERTAINMENT, type = CategoryType.ENTERTAINMENT, isDefault = true))
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt
git commit -m "feat(category): initialize ENTERTAINMENT default category in repository"
```

---

## Task 3: TodoEditViewModel 新增 createCustomCategory

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt`

- [ ] **Step 1: 在 loadCategories() 之后添加 createCustomCategory() 方法**

打开 `TodoEditViewModel.kt`，在 `loadCategories()` 函数（`}` 闭合大括号之后，约第 1287 行）的下一个函数前插入：

```kotlin
/**
 * 创建自定义分类
 *
 * 用于"分类"选择弹窗的"自定义"按钮：用户输入名称后异步写入数据库。
 * 写入成功后通过 [onCreated] 回调返回新分类的 ID，
 * 调用方应立即调用 [setCategoryId] 切换当前 todo 到新分类。
 *
 * @param name 自定义分类名称（已 trim 且非空）
 * @param onCreated 创建成功回调，参数为新分类的数据库 ID
 */
fun createCustomCategory(name: String, onCreated: (Long) -> Unit) {
    if (name.isBlank()) return
    viewModelScope.launch {
        try {
            val newCategory = Category(
                name = name.trim(),
                type = CategoryType.CUSTOM,
                isDefault = false
            )
            val newId = categoryRepository.insertCategory(newCategory)
            // 刷新内存中的分类列表
            _categories.value = categoryRepository.getAllCategoriesList()
            onCreated(newId)
            android.util.Log.w("TodoEditVM", "创建自定义分类成功: name='$name', id=$newId")
        } catch (e: Exception) {
            android.util.Log.e("TodoEditVM", "创建自定义分类失败", e)
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt
git commit -m "feat(todo-edit): add createCustomCategory for category dialog"
```

---

## Task 4: 新建 CategorySelectorDialog 组件

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/CategorySelectorDialog.kt`

- [ ] **Step 1: 创建文件**

文件路径：`app/src/main/java/com/corgimemo/app/ui/components/CategorySelectorDialog.kt`

完整内容：

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType

/**
 * 分类选择弹窗（AlertDialog）
 *
 * 用于待办编辑页底部"分类"按钮：
 * - 5 个默认分类以 Tag 标签形式展示
 * - 选中标签自动保存 + 关闭弹窗
 * - "自定义"按钮点击展开输入框
 * - 点击外部或"取消"按钮关闭
 *
 * @param categories 可选分类列表（已包含默认 + 用户自定义）
 * @param currentCategoryId 当前 todo 的分类 ID（用于高亮显示）
 * @param onDismiss 弹窗关闭回调（取消、点击外部）
 * @param onCategorySelected 分类选中回调，参数为 (id, name)。
 *        - id > 0 表示选中默认/已存在分类
 *        - id == 0L 表示"自定义分类"（name 为用户输入）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelectorDialog(
    categories: List<Category>,
    currentCategoryId: Long?,
    onDismiss: () -> Unit,
    onCategorySelected: (id: Long, name: String) -> Unit
) {
    /** 自定义输入框是否展开 */
    var showCustomInput by remember { mutableStateOf(false) }
    /** 自定义输入框当前内容 */
    var customInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择分类",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                /** 5 个默认分类 Tag */
                val defaultCategories = categories.filter { it.type != CategoryType.CUSTOM }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultCategories.forEach { category ->
                        CategoryTag(
                            category = category,
                            isSelected = category.id == currentCategoryId,
                            onClick = {
                                onCategorySelected(category.id, category.name)
                                onDismiss()
                            }
                        )
                    }

                    /** 自定义按钮 */
                    CustomCategoryButton(
                        onClick = { showCustomInput = !showCustomInput }
                    )
                }

                /** 自定义输入框（点击"自定义"按钮后展开） */
                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            placeholder = { Text("输入分类名称", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val name = customInput.trim()
                                if (name.isNotBlank()) {
                                    onCategorySelected(0L, name)
                                    onDismiss()
                                }
                            },
                            enabled = customInput.trim().isNotBlank()
                        ) {
                            Text("确定", fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * 分类标签组件
 *
 * 单个分类的可视化展示：
 * - 未选中：浅色背景 + 分类图标 + 名称
 * - 选中：边框 + 边框色背景加深
 */
@Composable
private fun CategoryTag(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val categoryColor = getCategoryColor(category.type)
    val bgColor = if (isSelected) categoryColor.copy(alpha = 0.25f)
                  else categoryColor.copy(alpha = 0.12f)
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = categoryColor,
            shape = RoundedCornerShape(20.dp)
        )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(borderModifier)
            .background(color = bgColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = getCategoryEmoji(category.type),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = category.name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 自定义分类按钮
 *
 * 点击后展开输入框；视觉上与分类标签风格统一但有"+ 号"标识
 */
@Composable
private fun CustomCategoryButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "自定义",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 分类类型 → Emoji 图标
 */
private fun getCategoryEmoji(type: Int): String = when (type) {
    CategoryType.STUDY -> "📚"
    CategoryType.WORK -> "💼"
    CategoryType.LIFE -> "🏠"
    CategoryType.SPORT -> "🏃"
    CategoryType.ENTERTAINMENT -> "🎮"
    else -> "📋"
}

/**
 * 分类类型 → 主题色（与 CategoryPickerSheet 保持一致）
 */
private fun getCategoryColor(type: Int): Color = when (type) {
    CategoryType.STUDY -> Color(0xFF7EC8A0)  // 薄荷绿
    CategoryType.WORK -> Color(0xFF90CAF9)   // 天空蓝
    CategoryType.LIFE -> Color(0xFFFFB74D)   // 暖橙色
    CategoryType.SPORT -> Color(0xFF7EB8DA)  // 运动蓝
    CategoryType.ENTERTAINMENT -> Color(0xFFE1BEE7)  // 紫粉
    else -> Color(0xFFB8A0D4)  // 默认紫色
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/CategorySelectorDialog.kt
git commit -m "feat(ui): add CategorySelectorDialog with default + custom categories"
```

---

## Task 5: CheckboxEditText 重构底部工具栏 + 新增 category 参数

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt`

- [ ] **Step 1: 修改 CheckboxEditText 函数签名**

打开 `CheckboxEditText.kt`，找到 `fun CheckboxEditText(` 函数（约第 81 行），在 `onReminderDelete: ((Int) -> Unit)? = null,` 之后追加：

```kotlin
    /** 各分组的分类 ID（null=未设置）；用于底部"分类"按钮显示 */
    categoryId: Long? = null,
    /** 各分组的分类名称（用于底部"分类"按钮显示） */
    categoryName: String? = null,
    /** "分类"按钮点击回调（无分组概念，整个容器共用） */
    onCategoryClick: (() -> Unit)? = null,
```

> **注**：由于 CheckboxEditText 支持多 groupId（每个 groupId 对应一个待办容器），需对每个容器单独传入。当前实现是默认值全为 `null`，调用方需提供额外数据时再覆盖。

- [ ] **Step 2: 在内部 TodoGroupContainer 调用处（groupId=0）传递新参数**

找到第一个 `TodoGroupContainer(` 调用（约第 227 行），在 `onPriorityClick = { onPriorityButtonClick?.invoke(0) },` 之后追加：

```kotlin
                categoryId = categoryId,
                categoryName = categoryName,
                onCategoryClick = { onCategoryClick?.invoke() },
```

- [ ] **Step 3: 在多 groupId 循环内的 TodoGroupContainer 调用处传递新参数**

找到第二个 `TodoGroupContainer(` 调用（约第 287 行），在 `onPriorityClick = { onPriorityButtonClick?.invoke(groupId) },` 之后追加：

```kotlin
                    categoryId = categoryId,
                    categoryName = categoryName,
                    onCategoryClick = { onCategoryClick?.invoke() },
```

> **说明**：每个 groupId 共享同一个 `categoryId` / `categoryName`（待办只有 1 个分类）。如需每个 groupId 独立分类，需后续扩展为 `Map<Int, Long?>` / `Map<Int, String?>`。

- [ ] **Step 4: 修改 TodoGroupContainer 函数签名**

找到 `private fun TodoGroupContainer(` 函数（约第 423 行），在 `onPriorityClick: (() -> Unit)? = null,` 之后追加：

```kotlin
    categoryId: Long? = null,
    categoryName: String? = null,
    onCategoryClick: (() -> Unit)? = null,
```

- [ ] **Step 5: 将底部操作栏从 Row 重构为 Column（两行布局）**

找到 `// 底部操作栏：[提醒按钮] [优先级按钮] ... [完成]` 注释（约第 479 行）及其下的 Row（到第 605 行），将整个 `Row { ... }` 块替换为：

```kotlin
        // 底部操作栏：两行布局
        // Row1：[优先级按钮] [8dp间距] [分类按钮]
        // Row2：[提醒按钮] ... [完成按钮]
        if (showBottomBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ===== Row1: 优先级 + 分类 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 优先级按钮（从原 Row 复制）
                    Box(
                        modifier = Modifier
                            .clickable(enabled = onPriorityClick != null) { onPriorityClick?.invoke() }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 优先级颜色圆点
                            if (borderColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(borderColor)
                                )
                            }
                            Text(
                                text = priorityLabel,
                                fontSize = 13.sp,
                                color = borderColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (priority > 0) androidx.compose.ui.text.font.FontWeight.SemiBold
                                             else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // ===== 新增：分类按钮 =====
                    if (onCategoryClick != null) {
                        Row(
                            modifier = Modifier
                                .clickable(enabled = onCategoryClick != null) { onCategoryClick?.invoke() }
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F5F5))
                                .widthIn(max = 120.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📋",
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = categoryName ?: "分类",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (categoryName != null) androidx.compose.ui.text.font.FontWeight.SemiBold
                                             else androidx.compose.ui.text.font.FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }

                // ===== Row2: 提醒 + 完成 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 实时刷新当前时间：进入页面时取一次，对齐到下一个 30s 整数倍开始轮询
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(reminderTime) {
                        now = System.currentTimeMillis()
                        while (true) {
                            val nextTick = ((System.currentTimeMillis() / 30_000L) + 1L) * 30_000L
                            kotlinx.coroutines.delay(nextTick - System.currentTimeMillis())
                            now = System.currentTimeMillis()
                        }
                    }

                    val isOverdue = reminderTime != null && reminderTime < now
                    val displayText = reminderTime?.let { formatReminderDisplay(it, now).text } ?: "设置提醒"
                    val iconTint: Color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                    val textColor: Color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .clickable(enabled = onReminderClick != null) { onReminderClick?.invoke() }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(
                                start = 10.dp,
                                top = 6.dp,
                                end = if (reminderTime != null) 6.dp else 10.dp,
                                bottom = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = if (reminderTime != null) "已设置提醒" else "设置提醒",
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = displayText,
                            fontSize = 13.sp,
                            color = textColor,
                            fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (reminderTime != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(Color(0xFFCCCCCC))
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clickable(enabled = onReminderDelete != null) { onReminderDelete?.invoke() }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = "×",
                                    fontSize = 16.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 完成 / 已保存 按钮
                    Text(
                        text = if (isSaved) "已保存 ✓" else "完成",
                        modifier = Modifier
                            .clickable(enabled = onSaveClick != null && !isSaved) { onSaveClick?.invoke() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = if (isSaved) androidx.compose.ui.graphics.Color(0xFF4CAF50) else Color(0xFFFF9A5C),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
```

- [ ] **Step 6: 添加缺失的 import**

在文件顶部 import 区域检查并添加（已存在则跳过）：

```kotlin
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.text.style.TextOverflow
```

- [ ] **Step 7: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。如出现未解析引用，按编译错误信息补全 import。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt
git commit -m "refactor(todo-edit): split bottom bar to two rows, add category button"
```

---

## Task 6: TodoEditScreen 接线 - 状态 + 弹窗

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt`

- [ ] **Step 1: 添加分类相关状态**

打开 `TodoEditScreen.kt`，在 `var showPriorityDialog by remember { mutableStateOf<Int?>(null) }`（约第 160 行）之后追加：

```kotlin
    /** 分类选择弹窗状态 */
    var showCategoryDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 2: 收集 categories / categoryId 状态**

找到 `val imagePaths by viewModel.imagePaths.collectAsState()`（约第 162 行），在它之前或合适位置追加：

```kotlin
    val categories by viewModel.categories.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val currentCategory = categories.find { it.id == categoryId }
```

- [ ] **Step 3: 在 CheckboxEditText 调用处传递新参数**

找到 `CheckboxEditText(` 调用（约第 927 行），找到 `onSaveClick = { groupId ->` 的位置（约第 1009 行），在 `onSaveClick` 之前或合适位置追加：

```kotlin
                categoryId = categoryId,
                categoryName = currentCategory?.name,
                onCategoryClick = { showCategoryDialog = true },
```

- [ ] **Step 4: 在优先级弹窗渲染附近添加分类弹窗渲染**

找到优先级弹窗的代码块：
```kotlin
    showPriorityDialog?.let { targetGroupId ->
        ...
    }
```
（约第 1601 行），在它的 `}` 之后追加：

```kotlin
    if (showCategoryDialog) {
        CategorySelectorDialog(
            categories = categories,
            currentCategoryId = categoryId,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { id, name ->
                if (id > 0L) {
                    // 默认/已存在分类
                    viewModel.setCategoryId(id)
                } else {
                    // 自定义分类：异步创建
                    viewModel.createCustomCategory(name) { newId ->
                        viewModel.setCategoryId(newId)
                    }
                }
                showCategoryDialog = false
            }
        )
    }
```

- [ ] **Step 5: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt
git commit -m "feat(todo-edit): wire CategorySelectorDialog in TodoEditScreen"
```

---

## Task 7: TodoListItem - 删除原独立分类行

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`

- [ ] **Step 1: 删除"分类行"独立 Row**

打开 `TodoListItem.kt`，找到以下代码块（约第 395-427 行）：

```kotlin
                            // 分类行
                            if (categoryName != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = categoryIcon ?: "📋",
                                        fontSize = 12.sp,
                                        color = if (todo.status == 1) CompletedColors.Text
                                                else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = categoryName,
                                        fontSize = 12.sp,
                                        color = if (todo.status == 1) CompletedColors.Text
                                                else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .background(
                                                color = if (todo.status == 1) CompletedColors.Text.copy(alpha = 0.12f)
                                                        else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                    // 注意：原"🎤 3s"语音备注内联显示已删除
                                    // 语音附件数量现在通过附件聚合计数（🎤×N）显示
                                }
                            }
```

将整段替换为：

```kotlin
                            // 分类行已删除（迁移到提醒行左侧，详见下方 CategoryTagWithShadow）
```

- [ ] **Step 2: 编译验证（中间步骤）**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL（`categoryName` 仍被使用，下一步用到）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "refactor(todo-card): remove standalone category row (moved inline)"
```

---

## Task 8: TodoListItem - 新增 CategoryTagWithShadow 组件 + 内联到提醒行

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`

- [ ] **Step 1: 添加缺失的 import**

在 `TodoListItem.kt` 顶部 import 区域追加：

```kotlin
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
```

- [ ] **Step 2: 修改提醒行 - 在 `if (hasReminder)` 之前插入分类渲染**

找到 `if (todo.reminderTime != null || aggregateCounts.first > 0 || aggregateCounts.second > 0) {` 所在的 `Row`（约第 344-393 行），在 `Row {` 之后立即插入：

```kotlin
                                    // 分类（内联展示，带阴影效果）
                                    if (categoryName != null) {
                                        CategoryTagWithShadow(
                                            categoryName = categoryName!!,
                                            categoryIcon = categoryIcon,
                                            isCompleted = todo.status == 1
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
```

- [ ] **Step 3: 在 TodoListItem.kt 文件底部新增 CategoryTagWithShadow 私有 Composable**

在文件最后（第 `}` 闭合大括号之前）追加：

```kotlin
/**
 * 带阴影效果的分类标签
 *
 * 用于 TodoListItem 卡片提醒行左侧：
 * - 阴影：水平偏移 2px，垂直偏移 2px，模糊半径 4px，颜色 rgba(0,0,0,0.1)
 * - 字号 12sp
 * - 已完成态使用 CompletedColors.Text 降权
 *
 * 实现：双层 Box
 * - 外层 Box：matchParentSize + offset(2.dp, 2.dp) + 半透明黑背景 + blur(4.dp) 模拟阴影
 * - 内层 Row：实际内容（背景色 + 图标 + 名称）
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(
                    color = Color(0x1A000000),  // rgba(0, 0, 0, 0.1)
                    shape = RoundedCornerShape(4.dp)
                )
                .blur(radius = 4.dp)
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin -q
```

预期：BUILD SUCCESSFUL。若 `Modifier.blur` 报"实验性 API"错误，按 IDE 提示添加 `@OptIn(ExperimentalComposeUiApi::class)`（已在函数签名标注）。如仍报错，回退方案：删除 `.blur(radius = 4.dp)` 修饰符，外层 Box 仍有 offset + 背景，视觉上仍是阴影感。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo-card): inline category tag with shadow in reminder row"
```

---

## Task 9: 全量编译 + 手动验证

**Files:** 无

- [ ] **Step 1: 完整编译**

```bash
.\gradlew :app:assembleDebug -q
```

预期：BUILD SUCCESSFUL，APK 输出到 `app/build/outputs/apk/debug/`。

- [ ] **Step 2: 手动验证清单（与设计文档验收标准对应）**

启动应用后逐项验证：

**编辑页**：
- [ ] 进入待办编辑页，确认"优先级"按钮和"分类"按钮在同一行（位于"设置提醒"上一行）
- [ ] 两个按钮间距为 8dp
- [ ] 分类按钮宽度自适应：长名称显示省略号
- [ ] 点击"分类"按钮，弹出 AlertDialog，标题"选择分类"
- [ ] AlertDialog 中有 5 个默认分类 Tag + 1 个"自定义"按钮
- [ ] 点击"📚学习"标签，弹窗关闭，按钮文字变为"📋 学习"
- [ ] 点击"自定义"展开输入框；输入"我的分类"后点"确定"，弹窗关闭，按钮文字变为"📋 我的分类"
- [ ] 点击弹窗外部 / 取消按钮，弹窗关闭，分类不变

**列表卡片**：
- [ ] 返回首页，待办卡片提醒行左侧显示分类名（带阴影）
- [ ] 分类与提醒时间之间有 1 空格间距
- [ ] 分类字号 12sp
- [ ] 阴影效果可见（向右下偏移 2px + 4px 模糊）
- [ ] 原独立分类行已不再显示
- [ ] 标记为已完成，分类颜色变灰（CompletedColors.Text）

**灵感页独立性**：
- [ ] 进入灵感编辑页，正常显示原有分类按钮和弹窗
- [ ] 灵感页分类选择弹窗中**也显示"娱乐"分类**（数据层加法生效）
- [ ] 灵感页"选择分类"流程正常

- [ ] **Step 3: 用户验收**

向用户报告验证结果，等待用户反馈。如有问题则修复并重新验证。

---

## 验收总结

| 验收项 | Task |
|--------|------|
| ENTERTAINMENT 枚举 + 默认分类 | Task 1, 2 |
| 分类选择 AlertDialog | Task 4 |
| 编辑页底部两行布局 + 分类按钮 | Task 5, 6 |
| 列表内联展示分类 + 阴影 | Task 7, 8 |
| 灵感页不受影响 | 手动验证（Task 9） |

---

## 风险与回退

| 风险 | 触发条件 | 回退方案 |
|------|----------|----------|
| `Modifier.blur` 编译失败 | 项目 Compose 版本不支持 | 删除 `.blur(radius = 4.dp)` 修饰符，阴影仍有 offset + 半透明背景 |
| `CategorySelectorDialog` FlowRow 编译失败 | 项目 Compose Foundation 版本 < 1.4 | 改用 `Column { Row { ... } Row { ... } }` 手动换行 |
| 自定义分类回调时序异常 | 数据库写入失败 | 异常已 try-catch 并 Log；弹窗正常关闭不阻塞用户 |
| 灵感页"娱乐"分类干扰用户 | 用户后续反馈 | 在 `CategorySelectorDialog` 和 `CategoryPickerSheet` 中按 type 过滤 |
