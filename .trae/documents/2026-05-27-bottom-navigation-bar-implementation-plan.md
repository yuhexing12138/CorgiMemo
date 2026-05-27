# 底部导航栏功能 - 实施计划

> **基于设计文档**: [2026-05-27-bottom-navigation-bar-design.md](./2026-05-27-bottom-navigation-bar-design.md)
> **方案**: Navigation Compose + 自定义组件 (方案 A)
> **预计工期**: 3-4 小时（核心功能）

---

## 📋 实施概览

### 核心目标
将现有 FAB 按钮替换为底部导航栏，包含 5 个 Tab 和中央编辑按钮气泡菜单。

### 实施策略
采用**增量式开发**，分 3 个 Phase 完成：
- **Phase 1**: 基础架构 + 导航栏布局（必须）
- **Phase 2**: 气泡菜单动画（核心体验）
- **Phase 3**: 完善优化（锦上添花）

---

## 🎯 Phase 1: 基础架构搭建

### 任务 1.1: 创建 MainScreen 主容器 ⏱️ 30分钟

**目标**: 创建主屏幕容器，管理底部导航栏和页面切换

**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt`

**具体步骤**:
1. 创建 `MainScreen` Composable 函数
2. 定义状态变量：
   - `selectedTab: TabItem` (当前选中的Tab)
   - `isBubbleExpanded: Boolean` (气泡是否展开)
3. 配置 Scaffold：
   - 保留现有 TopBar (EnhancedTopBar)
   - 移除 floatingActionButton 参数
   - 添加 bottomBar 参数指向 CorgiBottomNavigationBar
4. 实现页面内容区域：
   - 使用 `when(selectedTab)` 切换显示不同页面
   - 集成现有 HomeScreen 到 TODO tab
   - 集成现有 ProfileScreen 到 PROFILE tab
5. 添加气泡覆盖层条件渲染

**关键代码结构**:
```kotlin
@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(TabItem.TODO) }
    var isBubbleExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { /* EnhancedTopBar */ },
        bottomBar = {
            CorgiBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onCenterButtonClick = { isBubbleExpanded = !isBubbleExpanded }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                TabItem.TODO -> HomeScreen(navController)
                TabItem.INSPIRE -> InspireScreenPlaceholder()
                TabItem.DATE -> DateScreenPlaceholder()
                TabItem.PROFILE -> ProfileScreen(navController)
            }
        }

        if (isBubbleExpanded) {
            BubbleMenuOverlay(...)
        }
    }
}
```

**验证标准**:
- [ ] MainScreen 可编译运行
- [ ] 显示基础布局框架
- [ ] 状态管理正常工作

---

### 任务 1.2: 实现 CorgiBottomNavigationBar 组件 ⏱️ 45分钟

**目标**: 创建底部导航栏主体，包含 5 个 Tab 布局

**文件**: `app/src/main/java/com/corgimemo/app/ui/components/navigation/CorgiBottomNavigationBar.kt`

**具体步骤**:
1. 创建组件函数签名：
```kotlin
@Composable
fun CorgiBottomNavigationBar(
    selectedTab: TabItem,
    onTabSelected: (TabItem) -> Unit,
    onCenterButtonClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

2. 实现布局容器：
   - Surface (elevation=8dp, color=White, height=56dp)
   - Row (horizontalArrangement=SpaceAround)

3. 实现 NavItem 子组件：
```kotlin
@Composable
private fun NavItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            color = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
```

4. 添加 5 个 Tab 项：
   - 📝 待办 (TabItem.TODO)
   - 💡 灵感 (TabItem.INSPIRE)
   - ⊕ 编辑 (中央按钮占位)
   - 📅 日期 (TabItem.DATE)
   - 👤 我的 (TabItem.PROFILE)

5. 添加顶部边框：`border(top = 1.dp, Color(0xFFE0E0E0))`

**验证标准**:
- [ ] 显示 5 个 Tab（4个NavItem + 1个中央按钮占位）
- [ ] 选中状态颜色正确（暖橙色 #FF9A5C）
- [ ] 未选中状态颜色正确（灰色 #999999）
- [ ] 点击 Tab 触发回调

---

### 任务 1.3: 实现 CenterEditButton 中央按钮 ⏱️ 30分钟

**目标**: 创建中央编辑按钮，支持旋转动画

**文件**: `app/src/main/java/com/corgimemo/app/ui/components/navigation/CenterEditButton.kt`

**具体步骤**:
1. 创建组件函数：
```kotlin
@Composable
fun CenterEditButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

2. 实现旋转动画：
```kotlin
val rotation by animateFloatAsState(
    targetValue = if (isExpanded) 45f else 0f,
    animationSpec = tween(
        durationMillis = 200,
        easing = EaseInOutCubic
    ),
    label = "centerButtonRotation"
)
```

3. 实现按钮 UI：
   - Box/Surface (56x56dp, CircleShape)
   - 背景色 #FF9A5C
   - 向上偏移 8dp (offset y = -8.dp)
   - 阴影 elevation=12dp
   - 图标 ⊕/✕ (28sp, White)
   - 应用 rotation 变换

4. 添加点击波纹效果：
```kotlin
Modifier.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(color = Color.White, bounded = true),
    onClick = onClick
)
```

**验证标准**:
- [ ] 按钮居中显示，向上突出 8dp
- [ ] 暖橙色背景 (#FF9A5C)
- [ ] 点击时旋转 45° (⊕ → ✕)
- [ ] 动画流畅 (200ms)

---

### 任务 1.4: 更新导航路由配置 ⏱️ 20分钟

**目标**: 在 Navigation 中添加新的 Screen 定义和路由

**修改文件**:
- `app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt`
- `app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt`

**具体步骤**:

**Step 1**: 在 Screen.kt 中添加新枚举：
```kotlin
sealed class Screen(val route: String) {
    // ... 已有定义 ...
    object Inspire : Screen("inspire")
    object Date : Screen("date")
}
```

**Step 2**: 创建占位页面：
```kotlin
// InspireScreen.kt
@Composable
fun InspireScreenPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "💡", fontSize = 64.sp)
            Text(text = "灵感记录", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "功能开发中...", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

// DateScreen.kt (类似)
```

**Step 3**: 在 AppNavHost.kt 中注册路由：
```kotlin
composable(Screen.Inspire.route) {
    InspireScreenPlaceholder()
}
composable(Screen.Date.route) {
    DateScreenPlaceholder()
}
```

**验证标准**:
- [ ] 新增 Screen 编译通过
- [ ] 占位页面可访问
- [ ] 路由配置正确

---

### 任务 1.5: 集成到 MainActivity 入口 ⏱️ 15分钟

**目标**: 将 MainScreen 作为应用主入口

**修改文件**: `app/src/main/java/com/corgimemo/app/ui/MainActivity.kt` 或 AppNavHost.kt

**具体步骤**:
1. 修改 AppNavHost 的 startDestination 或默认路由
2. 将 HomeScreen 替换为 MainScreen
3. 确保导航参数正确传递

**备选方案**: 如果不想改变现有入口，可以在 HomeScreen 内部嵌入底部导航栏逻辑（不推荐，但兼容性更好）

**验证标准**:
- [ ] 应用启动后显示底部导航栏
- [ ] Tab 切换正常工作
- [ ] 现有功能不受影响

---

## 🎨 Phase 2: 气泡菜单动画

### 任务 2.1: 实现 BubbleMenuOverlay 覆盖层 ⏱️ 45分钟

**目标**: 创建气泡菜单遮罩层和布局容器

**文件**: `app/src/main/java/com/corgimemo/app/ui/components/navigation/BubbleMenuOverlay.kt`

**具体步骤**:
1. 创建组件签名：
```kotlin
@Composable
fun BubbleMenuOverlay(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onBubbleClick: (BubbleType) -> Unit,
    modifier: Modifier = Modifier
)
```

2. 实现半透明遮罩：
```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.3f))
        .clickable(onClick = onDismiss)
)
```

3. 实现气泡容器布局：
   - Column (fillMaxSize, center horizontally)
   - Spacer (weight=1f) // 推动内容到上方
   - 气泡1 (待办) - 上方
   - Spacer (16dp)
   - Row (左右气泡):
     - 气泡2 (灵感) - 左侧
     - 中央按钮 (✕状态)
     - 气泡3 (日期) - 右侧
   - Spacer (120dp) // 为底部导航栏留空间

**验证标准**:
- [ ] 遮罩层全屏覆盖
- [ ] 点击遮罩触发 onDismiss
- [ ] 气泡位置符合扇形布局

---

### 任务 2.2: 实现 AnimatedBubble 动画组件 ⏱️ 40分钟

**目标**: 创建单个气泡的弹出/收起动画

**文件**: 可内嵌在 BubbleMenuOverlay.kt 中或独立文件

**具体步骤**:
1. 创建 AnimatedBubble 组件：
```kotlin
@Composable
private fun AnimatedBubble(
    isVisible: Boolean,
    delayMillis: Long,
    content: @Composable () -> Unit
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(delayMillis)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 200,
                    easing = EaseOutCubic
                )
            )
        } else {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 150,
                    easing = EaseInCubic
                )
            )
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = animatable.value
            scaleY = animatable.value
            alpha = animatable.value
            translationY = (1f - animatable.value) * 20f
        }
    ) {
        content()
    }
}
```

2. 实现 BubbleItem UI：
```kotlin
@Composable
private fun BubbleItem(
    icon: String,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, Color(0xFFFF9A5C)),
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
        }
    }
}
```

3. 配置三个气泡的延迟时间：
   - 气泡1 (待办): delayMillis = 0
   - 气泡2 (灵感): delayMillis = 150
   - 气泡3 (日期): delayMillis = 300

**验证标准**:
- [ ] 气泡依次弹出（间隔150ms）
- [ ] 弹出动画流畅（缩放+位移+透明度）
- [ ] 收起动画快速（150ms，无延迟）
- [ ] 气泡样式符合设计稿

---

### 任务 2.3: 实现气泡点击交互逻辑 ⏱️ 25分钟

**目标**: 处理气泡点击事件，包含反馈动画和页面跳转

**具体步骤**:
1. 定义 BubbleType 枚举：
```kotlin
enum class BubbleType {
    CREATE_TODO,
    RECORD_INSPIRE,
    SPECIAL_DATE
}
```

2. 实现点击处理函数：
```kotlin
fun handleBubbleClick(bubbleType: BubbleType) {
    isBubbleExpanded = false  // 先收起气泡
    navController.navigate(
        when (bubbleType) {
            BubbleType.CREATE_TODO -> "todo_edit"
            BubbleType.RECORD_INSPIRE -> "inspire_edit"
            BubbleType.SPECIAL_DATE -> "date_edit"
        }
    )
}
```

3. （可选）添加点击缩放脉冲动画：
```kotlin
LaunchedEffect(Unit) {
    val scaleAnim = remember { Animatable(1f) }
    scaleAnim.animateTo(1.2f, tween(100))
    scaleAnim.animateTo(0.8f, tween(100))
    onComplete()
}
```

**验证标准**:
- [ ] 点击气泡触发收起动画
- [ ] 收起后跳转到对应编辑页
- [ ] （可选）有点击反馈动画

---

### 任务 2.4: 边界情况处理 ⏱️ 20分钟

**目标**: 处理特殊场景，确保稳定性

**具体步骤**:
1. **Tab 切换时自动收起气泡**:
```kotlin
onTabSelected = { tab ->
    isBubbleExpanded = false  // 先收起
    selectedTab = tab
}
```

2. **返回键拦截**:
```kotlin
BackHandler(enabled = isBubbleExpanded) {
    isBubbleExpanded = false
}
```

3. **防抖处理**:
```kotlin
var lastClickTime by remember { mutableLongStateOf(0L) }
val clickHandler = {
    val now = System.currentTimeMillis()
    if (now - lastClickTime > 300) {  // 300ms 防抖
        lastClickTime = now
        onCenterButtonClick()
    }
}
```

**验证标准**:
- [ ] 切换 Tab 时气泡自动收起
- [ ] 气泡展开时按返回键只收起气泡
- [ ] 快速连续点击不会导致异常

---

## ✨ Phase 3: 完善优化

### 任务 3.1: Tab 切换动画增强 ⏱️ 25分钟

**目标**: 为 Tab 切换添加微交互动画

**具体步骤**:
1. 为 NavItem 添加选中动画：
   - 图标缩放: 1.0 → 1.1
   - 文字上移: 0dp → (-4).dp
   - 颜色渐变: Gray → Orange

2. 使用 animateFloatAsState / animateDpAsState
3. 缓动函数: spring 或 EaseOutCubic (200ms)

**验证标准**:
- [ ] Tab 切换有平滑过渡动画
- [ ] 不影响性能

---

### 任务 3.2: 无障碍性适配 ⏱️ 20分钟

**目标**: 添加 Accessibility 支持

**具体步骤**:
1. 为所有交互元素添加 semantics：
```kotlin
Modifier.semantics {
    contentDescription = "打开编辑菜单"
    role = Role.Button
}
```
2. 测试 TalkBack 读屏
3. 验证键盘导航

**验证标准**:
- [ ] TalkBack 正确朗读
- [ ] 键盘可操作

---

### 任务 3.3: 性能优化与测试 ⏱️ 30分钟

**目标**: 确保动画流畅，无明显卡顿

**具体步骤**:
1. 使用 Layout Inspector 检查重组次数
2. 验证 graphicsLayer GPU 加速
3. 低端机型测试（如果可用）
4. 内存泄漏检查

**验证标准**:
- [ ] 60fps 流畅动画
- [ ] 无内存泄漏
- [ ] 无异常日志

---

## 📁 文件变更清单

### 新建文件（7个）
```
ui/screens/main/
└── MainScreen.kt                          # 主屏幕容器

ui/components/navigation/
├── CorgiBottomNavigationBar.kt           # 底部导航栏
├── CenterEditButton.kt                   # 中央按钮
├── BubbleMenuOverlay.kt                 # 气泡菜单
└── (可选) NavItem.kt                     # 导航项子组件

ui/screens/
├── inspire/
│   └── InspireScreen.kt                 # 占位页
└── date/
    └── DateScreen.kt                    # 占位页
```

### 修改文件（4个）
```
ui/navigation/
├── Screen.kt                             # +2 个枚举值
├── AppNavHost.kt                        # +2 个 composable
│
ui/screens/home/
└── HomeScreen.kt                         # 移除 FAB（可选保留）
```

---

## 🔧 开发顺序建议

### 推荐执行顺序（依赖关系）
```
1.5 (入口集成)
    ↓
1.1 (MainScreen) ← 1.4 (路由更新)
    ↓
1.2 (导航栏) + 1.3 (中央按钮)  ← 可并行
    ↓
2.1 (气泡布局) ← 2.2 (动画组件)
    ↓
2.3 (点击逻辑) + 2.4 (边界处理)
    ↓
3.1-3.3 (优化)
```

### 并行任务
- **任务 1.2** 和 **任务 1.3** 可以并行开发（无依赖）
- **任务 2.3** 和 **任务 2.4** 可以并行开发

---

## ✅ 验证清单

### Phase 1 完成标准
- [ ] 底部导航栏正确显示 5 个 Tab
- [ ] 中央按钮突出 8dp，暖橙色
- [ ] Tab 切换正常工作（待办、我的已有内容）
- [ ] 灵感、日期显示占位页面
- [ ] 选中状态高亮正确

### Phase 2 完成标准
- [ ] 点击 ⊕ 展开三个气泡
- [ ] 气泡依次弹出（0ms, 150ms, 300ms）
- [ ] 扇形排列位置正确
- [ ] 点击气泡进入对应页面
- [ ] 点击遮罩/✕ 收起气泡
- [ ] 切换 Tab 自动收起气泡
- [ ] 返回键正确处理

### Phase 3 完成标准（可选）
- [ ] Tab 切换有微动画
- [ ] 无障碍支持完善
- [ ] 性能测试通过

---

## ⚠️ 注意事项

### 关键风险点
1. **HomeScreen 集成**: 可能需要调整 HomeScreen 的参数（移除 FAB 相关）
2. **状态同步**: 确保 MainScreen 的状态正确传递到所有子组件
3. **动画性能**: 使用 graphicsLayer 而非直接修改 layout 属性
4. **返回栈管理**: Tab 切换使用 popUpTo(0) 避免堆叠

### 回滚策略
- 每个 Phase 完成后提交 Git commit
- 如果 Phase 2 出问题，可以回滚到 Phase 1（基础导航栏仍可用）
- 保留原有 AnimatedFAB.kt 文件（暂不删除），确保可回退

### 测试设备建议
- 优先在真机测试（模拟器可能动画不流畅）
- 测试不同屏幕尺寸（手机、平板）
- 测试横竖屏切换

---

## 📊 时间估算总结

| Phase | 任务数 | 预计时间 | 优先级 |
|-------|--------|---------|--------|
| Phase 1 | 5个 | ~2.5小时 | 必须 |
| Phase 2 | 4个 | ~2小时 | 核心 |
| Phase 3 | 3个 | ~1.25小时 | 可选 |
| **总计** | **12个** | **~5.75小时** | - |

**最小可用版本 (MVP)**: Phase 1 完成（仅导航栏，无气泡动画）
**完整版本**: Phase 1 + Phase 2（包含所有核心功能）

---

## 🚀 下一步行动

1. ✅ 从 **Phase 1 任务 1.1** 开始（创建 MainScreen）
2. 按照"开发顺序建议"依次完成各任务
3. 每个 Phase 完成后进行本地测试
4. 全部完成后进行真机验证

---

**计划版本**: v1.0
**创建日期**: 2026-05-27
**基于设计文档**: v1.0 (已审核通过)
