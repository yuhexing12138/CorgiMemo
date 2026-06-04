# 待办编辑页面(TodoEditScreen) 全面UI优化方案

## 一、参考图分析 vs 现状对比

### 参考图布局结构（从上到下）：

1. **顶部栏**: ←返回 | ↩撤销 | ↪重做 | 🔔通知 | 🔒锁定 | **【完成】**(黄色按钮)
2. **标题区**: 大号"标题"占位文字（非输入框内嵌）
3. **分类选择器**: 药丸形下拉 "备忘录 ▼"（紧贴标题下方）
4. **编辑器区域**: 极简风格，"请在这里输入内容..." 占位符，无重卡片装饰
5. **底部工具栏**:

   * 左侧：A/A字体 | 列表 | ✏️编辑 | 📷相机 | 🎤麦克风 | 👕背景 | 分享

   * 右侧：🗑️删除 | 共0字

### 当前实现与参考图的关键差异：

| 区域      | 参考图设计                  | 当前实现                             | 差异等级   |
| ------- | ---------------------- | -------------------------------- | ------ |
| 顶部栏     | 返回\|撤销\|重做\|通知\|锁定\|完成 | 返回\|标题输入框\|完成\|时钟                | **重大** |
| 标题位置    | 顶部栏下方独立大标题区            | 嵌入在顶部栏的OutlinedTextField内        | **重大** |
| 分类位置    | 标题正下方的药丸按钮             | 编辑器下方的合并行（与优先级同行）                | **重大** |
| 编辑器样式   | 极简、无重卡片包裹              | Surface圆角卡片(12dp)+tonalElevation | **中等** |
| 撤销/重做位置 | 顶部栏图标按钮                | 底部工具栏文字按钮行                       | **重大** |
| 底部工具栏   | 图标行+右侧删除/字数            | Emoji标签按钮(📷照片📝文本等)             | **重大** |
| 优先级     | 图中未显示（可能折叠/隐藏）         | 编辑器下方芯片行(低中高)                    | 需决策    |

***

## 二、优化方案：对标参考图的渐进式重构

### 设计原则

* **功能零删减**: 所有现有功能全部保留（撤销/重做、优先级、分类、日期、时长、重复、地理位置、子任务、语音备注、图片、关联、格式化等）

* **布局对标参考图**: 按照参考图的视觉层次和空间排列重新组织

* **保持项目风格**: 暖橙色主题色(#FF9A5C)、Material3组件规范

***

## 三、具体改动清单

### 改动1: 顶部栏重构 — 将Undo/Redo移入TopBar

**文件**: `TodoEditScreen.kt` 第439-530行(topBar代码块)

**当前结构**:

```
Row { IconButton(返回) | OutlinedTextField(标题输入) [weight=1f] | Button(完成) | Spacer | IconButton(时钟历史) }
```

**目标结构**:

```
Row { 
  IconButton(返回, 暖橙色) 
  Spacer 
  IconButton(撤销, canUndo控制enabled)   // 从底部工具栏上移
  IconButton(重做, canRedo控制enabled)    // 从底部工具栏上移
  IconButton(通知铃铛)                    // 新增：提醒快捷入口
  IconButton(锁定)                        // 新增：锁定编辑状态
  Button(完成, 黄色圆角) 
}
```

**要点**:

* 标题输入框从topBar中移出（变为内容区第一个元素）

* 撤销/重做使用 `Icons.Default.Undo` / `Icons.Default.Redo` Material图标

* 通知图标点击 → 打开提醒时间选择器(`showTimePicker = true`)

* 锁定图标 → 切换编辑只读状态（新增本地state `var isLocked`）

* 时钟历史按钮保留或合并到其他入口（参考图中无时钟图标，建议移除或改为长按完成按钮触发）

### 改动2: 标题区独立化为内容区首元素

**文件**: `TodoEditScreen.kt` 内容Column内部（第593行之后）

**新增标题区代码**（在Surface编辑器之前插入）:

```kotlin
/** 标题输入区（独立大标题） */
OutlinedTextField(
    value = title,
    onValueChange = { viewModel.setTitleWithRecommendation(it) },
    placeholder = { Text("标题", style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = Color(0xFFFF9A5C)
    ),
    textStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
)
```

**要点**:

* 使用 `headlineMedium` 风格的大号标题

* 移除边框(focused/unfocused均为Transparent)，仅保留下划线光标效果

* placeholder显示"标题"二字（对齐参考图）

### 改动3: 分类选择器移至标题正下方（药丸风格）

**文件**: `TodoEditScreen.kt`（紧接标题之后）

**当前**: 分类在优先级+分类合并行中（第762-769行），使用CategorySelector组件（含"分类"标签+横向Chip列表）

**目标**: 在标题下方显示单个选中分类的药丸按钮：

```kotlin
/** 分类药丸选择器（标题下方） */
val selectedCategory = categories.find { it.id == categoryId }
Row(
    modifier = Modifier.padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = { /* TODO: 显示分类选择BottomSheet或展开选项 */ }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedCategory?.name ?: "备忘录",
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
```

**要点**:

* 显示当前选中分类名+"▼"下拉箭头

* 点击展开分类选择（可复用CategoryPickerSheet或简单弹出选项）

* 默认文本"备忘录"（对齐参考图）

### 改动4: 编辑器区域简化

**文件**: `TodoEditScreen.kt` 第595-665行(Surface代码块)

**调整**:

* 保持Surface容器但减轻视觉重量：`tonalElevation = 0.dp`（去除阴影）

* 圆角减小为 `RoundedCornerShape(8.dp)`

* 内部padding调整为 `Modifier.padding(8.dp)`（更紧凑）

* RichTextEditor高度增加以填充更多空间：`.heightIn(min = 200.dp)`

* placeholder改为 `"请在这里输入内容..."`（对齐参考图原文）

### 改动5: 优先级选择调整为紧凑行内（可选展开）

**文件**: `TodoEditScreen.kt`（原第733-758行PriorityChip区域）

**方案**: 优先级从芯片行改为编辑器内部的行内小标签（类似Markdown工具栏的内联样式），或在底部设置区域保留但视觉降权：

```kotlin
/** 优先级内联选择（编辑器上方轻量行） */
Row(
    modifier = Modifier.padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text("优先级:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.width(8.dp))
    // 三个小型文字按钮：低 | 中 | 高
    listOf(Pair(0,"低"), Pair(1,"中"), Pair(2,"高")).forEach { (value, label) ->
        Text(
            text = label,
            modifier = Modifier
                .clickable { viewModel.setPriority(value) }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = if (priority == value) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (priority == value) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
        if (value < 2) Spacer(modifier = Modifier.width(4.dp))
    }
}
```

### 改动6: 底部工具栏(EditToolbar)重新设计

**文件**: `EditToolbar.kt` 全面修改 + `TodoEditScreen.kt` 调用处调整

**目标布局**（对标参考图）:

```
┌─────────────────────────────────────────────────┐
│ A/A  ☰  ✏️  📷  🎤  👕  ⎘    🗑️  共0字         │
│字体 列表 编辑 相机 麦克 背景 分享  删除  字数     │
└─────────────────────────────────────────────────┘
```

**改动点**:

1. **移除Undo/Redo行**（已移至顶部栏）
2. **移除emoji+标签的ToolButton竖列布局**，改为纯图标横排
3. **新增右侧信息区**: 删除图标 + 字数统计
4. **图标使用Material Icons替代Emoji**

**新的EditToolbar签名**:

```kotlin
@Composable
fun EditToolbar(
    onFontClick: () -> Unit,          // A/A 字体格式
    onListClick: () -> Unit,          // 列表
    onEditClick: () -> Unit,          // 编辑/铅笔
    onPhotoClick: () -> Unit,         // 相机
    onVoiceClick: () -> Unit,         // 麦克风
    onBackgroundClick: () -> Unit,    // 背景/衣服
    onShareClick: () -> Unit,         // 分享
    onDeleteClick: () -> Unit,        // 删除
    wordCount: Int = 0,               // 字数
    modifier: Modifier = Modifier
)
```

### 改动7: 时间/日期等设置区保持紧凑布局不变

**文件**: `TodoEditScreen.kt` 第817-958行

以下区域**保持现有紧凑布局不变**（已在上一轮优化中完成瘦身）:

* 开始日期 | 截止时间 （双列OutlinedButton）

* 提醒时间 | 预计时长 （双列）

* 推荐提醒时间标签

* 重复类型选择

* 地理位置(LocationPicker)

* 子任务列表(SubTaskList)

* 语音备注(VoicePlayerComponent + 录制按钮)

* 图片选择(ImagePicker)

* 关联选择(RelationSelector)

这些区域保持在编辑器下方滚动区域内的现有位置。

***

## 四、实施步骤（按顺序执行）

### Step 1: 顶部栏重构

* 修改 `TodoEditScreen.kt` 的 `topBar` 参数

* 移除标题OutlinedTextField和时钟按钮

* 添加Undo/Redo/通知/锁定IconButton

* 调整完成按钮位置

### Step 2: 内容区添加独立标题

* 在内容Column的首位添加标题OutlinedTextField（大号、无边框）

* 从topBar中删除旧标题输入代码

### Step 3: 添加分类药丸选择器

* 在标题下方添加药丸形分类按钮

* 处理点击展开逻辑（复用CategoryPickerSheet或新建简单选择器）

### Step 4: 简化编辑器Surface

* 减轻Surface视觉重量

* 调整placeholder文字

* 增加最小高度

### Step 5: 调整优先级展示

* 将PriorityChip行改为更紧凑的内联样式

* 位置放在编辑器Surface内部或紧邻其上方

### Step 6: 重构EditToolbar组件

* 重写 `EditToolbar.kt` 为横排图标布局

* 更新 `TodoEditScreen.kt` 的调用参数

* 添加字数统计和删除回调

### Step 7: 清理与验证

* 清理无用import

* 编译验证零错误

* 功能完整性检查（所有原有功能入口均存在）

***

## 五、涉及文件清单

| 文件                                          | 改动类型                             |
| ------------------------------------------- | -------------------------------- |
| `app/.../ui/screens/todo/TodoEditScreen.kt` | **重大修改** - topBar重构、内容区重排、调用参数更新 |
| `app/.../ui/components/EditToolbar.kt`      | **重写** - 全新图标横排布局                |
| `app/.../ui/components/CategorySelector.kt` | 小改 - 可能需要新增药丸模式                  |

***

## 六、风险与假设

**假设**:

1. 用户确认所有功能必须保留（包括锁定编辑功能为新增强功能）
2. 参考图为最终目标布局，尽量像素级对齐
3. EditToolbar的重构不影响其他页面的复用（需确认EditToolbar是否被其他页面使用）

**风险**:

1. 锁定编辑功能需要新增state和逻辑（参考图有锁图标）
2. 分类选择器的点击展开交互需要确定实现方式
3. EditToolbar如果被其他页面引用，接口变更会影响其他页面

