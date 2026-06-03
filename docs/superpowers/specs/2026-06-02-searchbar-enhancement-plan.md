# SearchBar 增强与 TextField 统一化 - 实施计划

**基于设计规范**: [2026-06-02-searchbar-enhancement-design.md](./2026-06-02-searchbar-enhancement-design.md)
**创建日期**: 2026-06-02
**预计总工时**: ~30 分钟（含编译验证）
**风险等级**: 🟢 低风险

---

## 📊 执行摘要

本计划将分 **5 个任务** 实施两项改进：
1. **SearchBar 组件增强**: 添加 `trailingIcon` 智能互斥功能
2. **TextField 统一化**: 修复 4 处编译错误，统一为 `OutlinedTextField`

**预期收益**:
- 减少 HomeScreen 约 10 行冗余代码
- 消除 4 个编译错误
- 提升组件复用性和一致性

---

## 🎯 任务概览

| 任务 ID | 任务名称 | 优先级 | 文件 | 预计时间 | 状态 |
|--------|---------|--------|------|----------|------|
| T1 | SearchBar 函数签名与逻辑增强 | 🔴 高 | SearchBar.kt | 8 min | ⏳ 待开始 |
| T2 | HomeScreen 调用点简化 | 🔴 高 | HomeScreen.kt | 5 min | ⏳ 待开始 |
| T3 | SettingsScreen TextField 替换 | 🟡 中 | SettingsScreen.kt | 4 min | ⏳ 待开始 |
| T4 | LocationPicker TextField 替换 | 🟡 中 | LocationPicker.kt | 4 min | ⏳ 待开始 |
| T5 | 编译验证与测试 | 🔴 高 | 全局 | 9 min | ⏳ 待开始 |

**依赖关系**:
```
T1 (SearchBar 增强)
    ↓
T2 (HomeScreen 更新) ← 依赖 T1 完成
    ↓
并行执行:
├── T3 (SettingsScreen)
└── T4 (LocationPicker)
    ↓
T5 (全局验证) ← 依赖 T2, T3, T4 完成
```

---

## 📝 详细任务说明

### ✅ 任务 T1: SearchBar 函数签名与逻辑增强

**文件路径**: `app/src/main/java/com/corgimemo/app/ui/components/SearchBar.kt`

#### 步骤 1.1: 更新函数签名（第 63-69 行）

**当前代码**:
```kotlin
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "输入要搜索的内容...",
    enabled: Boolean = true
)
```

**修改为**:
```kotlin
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "输入要搜索的内容...",
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,        // 新增
    showTrailingIconAlways: Boolean = false                // 新增
)
```

**验证点**:
- [ ] 新参数有默认值（向后兼容）
- [ ] 参数顺序合理（可选参数在后）

---

#### 步骤 1.2: 替换尾部区域逻辑（第 154-170 行）

**当前代码**:
```kotlin
// 清空按钮
if (localQuery.isNotEmpty()) {
    IconButton(
        onClick = {
            localQuery = ""
            onClear()
        },
        modifier = Modifier.size(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "清空",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
```

**替换为**:
```kotlin
/** 尾部区域：清空按钮 / trailingIcon 智能切换 */
when {
    // 场景 A：始终显示模式
    showTrailingIconAlways && trailingIcon != null -> {
        trailingIcon()
    }

    // 场景 B：有输入内容 → 显示清空按钮
    localQuery.isNotEmpty() -> {
        IconButton(
            onClick = {
                localQuery = ""
                onClear()
            },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "清空",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // 场景 C：无输入 + 有自定义图标 → 显示 trailingIcon
    trailingIcon != null -> {
        trailingIcon()
    }

    // 场景 D：无输入 + 无自定义图标 → 不显示
    else -> { /* 不渲染 */ }
}
```

**验证点**:
- [ ] 4 种场景逻辑完整
- [ ] 清空按钮行为不变
- [ ] 代码缩进和格式正确

---

#### 步骤 1.3: 更新 KDoc 注释（第 49-61 行）

在现有注释后添加新参数说明：

```kotlin
 * @param trailingIcon 尾部图标内容 Composable lambda（可选）
 *                   - 与清空按钮互斥显示（当 showTrailingIconAlways = false 时）
 *                   - 支持任意 Composable 内容（IconButton、Icon、Text 等）
 * @param showTrailingIconAlways 是否始终显示 trailingIcon（默认 false）
 *                             - true: 忽略清空按钮，始终显示 trailingIcon
 *                             - false: 当有输入内容时显示清空按钮，否则显示 trailingIcon
```

**完成标志**: SearchBar.kt 修改完成，无语法错误

---

### ✅ 任务 T2: HomeScreen 调用点简化

**文件路径**: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

#### 步骤 2.1: 定位修改区域

**位置**: 第 371-401 行（之前修复时添加的独立排序按钮 Row）

**当前代码结构**:
```kotlin
SearchBar(
    query = searchQuery,
    onQueryChange = { ... },
    onClear = { ... },
    modifier = Modifier.fillMaxWidth().padding(20.dp, 8.dp)
)

// 独立的排序按钮 Row（需要删除）
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.End
) {
    IconButton(onClick = { showSortSheet = true }, ...) {
        Text("📊", ...)
    }
}
```

---

#### 步骤 2.2: 合并到 SearchBar 调用

**修改为**:
```kotlin
SearchBar(
    query = searchQuery,
    onQueryChange = { newQuery ->
        viewModel.updateSearchQuery(newQuery)
    },
    onClear = {
        viewModel.clearSearch()
    },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
    trailingIcon = {  // ✨ 新增：将排序按钮移入
        IconButton(
            onClick = { showSortSheet = true },
            modifier = Modifier.size(40.dp)
        ) {
            Text(
                text = "📊",
                fontSize = 20.sp,
                color = Color(0xFFFF9A5C) // 暖橙色
            )
        }
    }
)
// 删除下面的独立 Row 布局（第 387-401 行）
```

---

#### 步骤 2.3: 清理代码

**删除内容**:
```kotlin
// 删除这段代码（约 12 行）
/** 排序按钮（独立于搜索栏） */
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.End
) {
    IconButton(...) { ... }
}
```

**验证点**:
- [ ] SearchBar 调用包含 trailingIcon 参数
- [ ] 独立 Row 已完全删除
- [ ] 无多余的空行或注释残留
- [ ] import 语句无需变更（已包含所有需要的）

**完成标志**: HomeScreen 搜索区域代码更简洁（净减少约 10 行）

---

### ✅ 任务 T3: SettingsScreen TextField 替换

**文件路径**: `app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt`

#### 步骤 3.1: 定位 TextField 使用（L790 和 L798）

使用 Grep 或 IDE 搜索确认位置：
```bash
# 在 SettingsScreen.kt 中搜索
grep -n "TextField(" app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt
```

**预期结果**:
```
790:                    TextField(
798:                    TextField(
```

---

#### 步骤 3.2: 替换为 OutlinedTextField

**修改操作**:
- 第 790 行：`TextField(` → `OutlinedTextField(`
- 第 798 行：`TextField(` → `OutlinedTextField(`

**示例**:
```kotlin
// 修改前（L790）
TextField(
    value = settingsState.username,
    onValueChange = { ... },
    ...
)

// 修改后
OutlinedTextField(
    value = settingsState.username,
    onValueChange = { ... },
    ...
)
// 注意：其他参数保持不变！
```

---

#### 步骤 3.3: 验证 Import 语句

**检查文件头部** 是否包含：
```kotlin
import androidx.compose.material3.OutlinedTextField  // 必需
```

**如果缺失**，添加到 import 区域（约第 30-50 行附近）。

**如果存在旧的 import**：
```kotlin
import androidx.compose.material.TextField  // 如果存在则删除
```

**完成标志**:
- [ ] 2 处 TextField 已替换
- [ ] Import 正确
- [ ] 无语法错误

---

### ✅ 任务 T4: LocationPicker TextField 替换

**文件路径**: `app/src/main/java/com/corgimemo/app/ui/components/LocationPicker.kt`

#### 步骤 4.1: 定位 TextField 使用（L105 和 L146）

```bash
grep -n "TextField(" app/src/main/java/com/corgimemo/app/ui/components/LocationPicker.kt
```

**预期结果**:
```
105:                        TextField(
146:                    TextField(
```

---

#### 步骤 4.2: 替换为 OutlinedTextField

与 T3 相同的操作：
- L105: `TextField(` → `OutlinedTextField(`
- L146: `TextField(` → `OutlinedTextField(`
- 保持所有其他参数不变

---

#### 步骤 4.3: 验证 Import 语句

确保文件包含：
```kotlin
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults  // 如果使用了自定义颜色
```

**完成标志**:
- [ ] 2 处 TextField 已替换
- [ ] Import 正确
- [ ] 无语法错误

---

### ✅ 任务 T5: 编译验证与测试

#### 步骤 5.1: 编译项目

**命令**（在项目根目录执行）:
```bash
.\gradlew.bat assembleDebug
```

**预期结果**:
```
BUILD SUCCESSFUL in Xs
```

**如果失败**:
1. 检查错误信息中的文件名和行号
2. 确认所有 `TextField` 都已替换
3. 确认所有 import 正确
4. 修复后重新编译

---

#### 步骤 5.2: 功能验证清单

**自动编译检查**:
- [ ] ✅ 0 个编译错误
- [ ] ✅ 0 个警告（或仅有不关键的警告）

**SearchBar 功能验证**（需要运行 App 或查看预览）:
- [ ] 默认模式：无 trailingIcon 时行为不变
- [ ] 互斥模式：HomeScreen 排序按钮正常显示和切换
- [ ] 清空功能：输入文字后清空按钮出现且可用
- [ ] 向后兼容：其他页面（如果有使用 SearchBar）正常

**TextField 统一化验证**:
- [ ] SettingsScreen：用户名输入框显示带边框样式
- [ ] LocationPicker：两个地址输入框正常工作
- [ ] 输入、删除、焦点状态都正常

---

## 🧪 测试用例详细步骤

### TC-02: SearchBar 互斥模式（重要）

**前置条件**: 已完成任务 T1 和 T2

**测试步骤**:
1. 启动 App 进入首页
2. 观察搜索框右侧 → 应显示 📊 排序按钮
3. 点击搜索框输入 "测试文字"
4. 观察搜索框右侧 → 应切换为 ✕ 清空按钮（📊 消失）
5. 点击清空按钮
6. 观察搜索框右侧 → 应恢复显示 📊 排序按钮

**预期结果**: 通过 ✅

**失败处理**: 检查 SearchBar.kt 的 when 逻辑顺序

---

### TC-05 & TC-06: TextField 显示验证

**TC-05 SettingsScreen**:
1. 进入设置页面
2. 找到用户名/昵称输入框
3. 确认：输入框有圆角边框轮廓（Outlined 样式）
4. 点击输入框 → 聚焦边框变色
5. 输入文字 → 正常显示

**TC-06 LocationPicker**:
1. 进入待办编辑页
2. 点击"位置选择"
3. 在弹出的地点选择器中找到地址输入框
4. 确认：两个输入框都有边框轮廓
5. 输入地址文字 → 正常工作

---

## ⚠️ 回滚方案

如果实施后出现问题，可以使用 Git 快速回滚：

```bash
# 查看修改的文件
git status

# 查看具体改动
git diff app/src/main/java/com/corgimemo/app/ui/components/SearchBar.kt
git diff app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git diff app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt
git diff app/src/main/java/com/corgimemo/app/ui/components/LocationPicker.kt

# 回滚单个文件（如果需要）
git checkout -- <file-path>

# 或回滚所有修改
git checkout .
```

**建议**: 在开始前先提交当前的编译错误修复（作为安全点）：
```bash
git add -A
git commit -m("fix: 修复编译错误（CornerDecoration/DeleteConfirmDialog/HomeScreen/TodoEditScreen）")
```

---

## 📈 成功指标

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| **编译错误数** | 0 | `gradlew assembleDebug` 输出 |
| **代码行数变化** | HomeScreen 净减少 ~10 行 | Git diff 统计 |
| **向后兼容性** | 100% 现有调用点无需修改 | 手动检查 + 编译通过 |
| **功能完整性** | 所有测试用例通过 | 手动测试清单 |

---

## 📌 实施后检查项

完成所有任务后，确认以下事项：

**代码质量**:
- [ ] 无 TODO/FIXME 残留
- [ ] 无调试代码（println、Log.d 等）
- [ ] 代码格式符合项目规范
- [ ] 中文注释清晰准确

**文档更新**:
- [ ] 如有必要，更新相关组件的使用文档
- [ ] 本实施计划标记为"已完成"

**Git 提交建议**:
```bash
git add -A
git commit -m("feat: 增强 SearchBar 组件支持 trailingIcon + 统一 TextField 为 OutlinedTextField")

# 建议的 commit message 格式
# feat: 新功能
# fix: 修复 bug
# refactor: 重构（不改变行为）
# style: 代码格式调整
```

---

## 🎓 学习要点

本次实施涉及的关键技术概念：

1. **Compose Lambda 参数**: `@Composable (() -> Unit)?` 用于传递任意 UI 内容
2. **智能条件渲染**: 使用 `when` 表达式实现多场景互斥逻辑
3. **Material3 组件体系**: TextField vs OutlinedTextField vs BasicTextField 的区别
4. **向后兼容设计**: 可选参数 + 合理默认值保证 API 稳定性

---

**计划结束**

*准备就绪，可以开始实施！*
