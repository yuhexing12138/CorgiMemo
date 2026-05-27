# 侧滑导航栏 HomeScreen 集成计划

## 当前状态

根据代码审查，以下文件**已完成**实现：

* ✅ `DeletedTodo.kt` — 最近删除 Room 实体

* ✅ `DeletedTodoDao.kt` — 最近删除 DAO

* ✅ `DeletedTodoRepository.kt` — 最近删除 Repository

* ✅ `CorgiMemoDatabase.kt` — 数据库版本 16，含 MIGRATION\_15\_16

* ✅ `DatabaseModule.kt` — Hilt 注入 DeletedTodoDao

* ✅ `TodoRepository.kt` — 软删除（删除前先存入最近删除表）

* ✅ `HomeViewModel.kt` — 侧滑栏相关状态和方法（分类过滤、计数、CRUD）

* ✅ `AppDrawer.kt` — 完整 UI 组件（用户区域、分组列表、弹窗、BottomSheet）

**未完成**（仅剩一个文件需要修改）：

* ❌ `HomeScreen.kt` — 需要集成 `ModalNavigationDrawer`

***

## 修改计划：HomeScreen.kt

### 1. 新增 import 语句

需要添加以下导入：

* `ModalNavigationDrawer` / `ModalDrawerSheet` — Material 3 侧滑抽屉组件

* `DrawerState` / `rememberDrawerState` / `DrawerValue` — 抽屉状态管理

* `AppDrawerContent` / `AddCategoryDialog` / `RenameCategoryDialog` — AppDrawer 子组件

* `DeleteCategoryConfirmDialog` / `CategoryOperationSheet` / `CategoryAction`

* `Category` — 分类模型（可能已导入）

### 2. 新增 UI 状态变量

在 HomeScreen composable 函数中，现有的状态变量声明区域（约 line 130-200）添加：

```kotlin
// ========== 侧滑导航栏状态 ==========
val drawerState = rememberDrawerState(DrawerValue.Closed)
val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
val todoCountByCategory by viewModel.todoCountByCategory.collectAsState()
val recentlyDeletedCount by viewModel.recentlyDeletedCount.collectAsState()

var showAddCategoryDialog by remember { mutableStateOf(false) }
var showRenameCategoryDialog by remember { mutableStateOf<Category?>(null) }
var showDeleteCategoryDialog by remember { mutableStateOf<Category?>(null) }
var showCategorySheet by remember { mutableStateOf<Category?>(null) }
```

### 3. 包裹 ModalNavigationDrawer

现有结构：

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(...) { ... }
    // 各种弹窗和覆盖层
}
```

改为：

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet(
            drawerState = drawerState,
            width = 280.dp
        ) {
            AppDrawerContent(
                corgiData = corgiData,
                categories = categories,
                todoCountByCategory = todoCountByCategory,
                recentlyDeletedCount = recentlyDeletedCount,
                selectedCategoryId = selectedCategoryId,
                onCategoryClick = { categoryId ->
                    viewModel.filterByCategory(categoryId)
                    coroutineScope.launch { drawerState.close() }
                },
                onAddCategoryClick = { showAddCategoryDialog = true },
                onCategoryAction = { action ->
                    when (action) {
                        is CategoryAction.ShowMenu -> {
                            showCategorySheet = action.category
                        }
                        is CategoryAction.Pin -> {
                            // TODO: 置顶功能（CategoryRepository 尚无此方法）
                        }
                        is CategoryAction.Rename -> {
                            showRenameCategoryDialog = action.category
                        }
                        is CategoryAction.Delete -> {
                            showDeleteCategoryDialog = action.category
                        }
                    }
                },
                onRecentlyDeletedClick = {
                    // TODO: 导航到最近删除页面
                    coroutineScope.launch { drawerState.close() }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                    coroutineScope.launch { drawerState.close() }
                },
                onHelpClick = {
                    // TODO: 帮助与反馈页面
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    }
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 原有的全部内容
        Scaffold(...) { ... }
        // 各种弹窗和覆盖层
    }
}
```

### 4. 连接 onMenuClick 打开抽屉

将 line 336：

```kotlin
onMenuClick = { /* 可扩展：打开侧滑导航 */ }
```

改为：

```kotlin
onMenuClick = { coroutineScope.launch { drawerState.open() } }
```

### 5. 动态标题

根据 `selectedCategoryId` 设置 EnhancedTopBar 的标题：

* `null` → "📝 我的待办"

* `0L` → "📦 未分类"

* `categoryId` → 对应分类的名称

### 6. 添加弹窗和 BottomSheet 处理

在 HomeScreen 末尾的弹窗区域添加：

```kotlin
// 添加分组对话框
if (showAddCategoryDialog) {
    AddCategoryDialog(
        onConfirm = { name ->
            viewModel.createCategory(name)
            showAddCategoryDialog = false
        },
        onDismiss = { showAddCategoryDialog = false }
    )
}

// 重命名分组对话框
showRenameCategoryDialog?.let { category ->
    RenameCategoryDialog(
        currentName = category.name,
        onConfirm = { newName ->
            viewModel.renameCategory(category.id, newName)
            showRenameCategoryDialog = null
        },
        onDismiss = { showRenameCategoryDialog = null }
    )
}

// 删除确认对话框
showDeleteCategoryDialog?.let { category ->
    DeleteCategoryConfirmDialog(
        categoryName = category.name,
        onConfirm = {
            viewModel.deleteCategory(category.id)
            showDeleteCategoryDialog = null
        },
        onDismiss = { showDeleteCategoryDialog = null }
    )
}

// 分类操作 BottomSheet
showCategorySheet?.let { category ->
    CategoryOperationSheet(
        category = category,
        onPin = {
            // TODO: 置顶功能
        },
        onRename = {
            showRenameCategoryDialog = category
        },
        onDelete = {
            showDeleteCategoryDialog = category
        },
        onDismiss = { showCategorySheet = null }
    )
}
```

***

## 执行步骤

1. **添加 import 语句** — 在文件顶部 import 区域添加所有需要的导入
2. **添加状态变量** — 在状态声明区添加 drawer 和弹窗状态
3. **包裹 ModalNavigationDrawer** — 用 ModalNavigationDrawer + ModalDrawerSheet 包裹现有 Scaffold
4. **修改 onMenuClick** — 连接到 drawerState.open()
5. **添加弹窗处理** — 在文件末尾添加所有弹窗和 BottomSheet
6. **验证编译** — 执行 Gradle 构建，检查是否有 import 缺失

***

## 注意事项

* 置顶功能 (`CategoryAction.Pin`) 暂不实现，因为 `CategoryRepository` 中没有排序/置顶方法

* "最近删除"点击后暂不导航到新页面（未来可扩展）

* "帮助与反馈"暂不实现导航（未来可扩展）

* 抽屉内容使用 `Column` + `weight(1f)` 布局，适配不同屏幕高度

