# 底部导航栏功能设计规格文档

> **项目**: CorgiMemo
> **日期**: 2026-05-27
> **状态**: 待审核
> **方案**: Navigation Compose + 自定义组件 (方案 A)

---

## 1. 功能概述

将当前右下角 FAB 按钮替换为底部导航栏（Bottom Navigation Bar），包含 5 个 Tab 项和中央编辑按钮。中央按钮点击后展开三个气泡菜单，支持扇形排列动画。

### 1.1 核心目标
- ✅ 提供 5 个主要页面的快速切换入口
- ✅ 中央编辑按钮突出显示，支持气泡展开动画
- ✅ 替代现有 FAB 按钮，提升操作效率
- ✅ 符合 Material Design 规范并保持 CorgiMemo 品牌风格

---

## 2. 技术架构

### 2.1 整体架构方案
**采用方案 A: Navigation Compose + 自定义组件**

```
┌─────────────────────────────────────────────┐
│              MainActivity                    │
│  ┌─────────────────────────────────────┐    │
│  │         AppNavHost                  │    │
│  │  ┌──────────┐ ┌────────────────┐   │    │
│  │  │NavGraph  │ │ MainScreen     │   │    │
│  │  │(路由配置)│ │ ┌────────────┐ │   │    │
│  │  │          │ │ │Scaffold    │ │   │    │
│  │  │- home    │ │ │ ├─ content │ │   │    │
│  │  │- inspire │ │ │ └──────────┘ │   │    │
│  │  │- date   │ │ │ bottomBar:   │ │   │    │
│  │  │- profile│ │ │ BottomNavBar │ │   │    │
│  │  └──────────┘ │ └─────────────┘ │   │    │
│  └───────────────┴─────────────────┘   │    │
└─────────────────────────────────────────────┘
```

### 2.2 技术栈
- **导航管理**: Navigation Compose (已使用)
- **UI 框架**: Jetpack Compose + Material3
- **动画系统**: Compose Animation API (animateFloatAsState, Animatable)
- **状态管理**: Compose State + ViewModel

---

## 3. 组件结构

### 3.1 核心组件清单

| 组件名称 | 职责 | 文件位置 |
|---------|------|---------|
| `MainScreen` | 主屏幕容器，管理 Scaffold + 底部导航栏 | `ui/screens/main/MainScreen.kt` |
| `CorgiBottomNavigationBar` | 底部导航栏主体（5个Tab） | `ui/components/navigation/CorgiBottomNavigationBar.kt` |
| `CenterEditButton` | 中央编辑按钮（⊕/✕ 切换） | `ui/components/navigation/CenterEditButton.kt` |
| `BubbleMenuOverlay` | 气泡菜单覆盖层（3个气泡+遮罩） | `ui/components/navigation/BubbleMenuOverlay.kt` |
| `NavItem` | 单个导航项（图标+文字） | 内部组件 |

### 3.2 组件层级关系

```kotlin
@Composable
fun MainScreen(navController: NavController) {
    // 状态管理
    var selectedTab by remember { mutableStateOf(TabItem.TODO) }
    var isBubbleExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { /* 保持现有 TopBar */ },
        floatingActionButton = { /* 移除现有 FAB */ },  // ❌ 移除
        bottomBar = {
            CorgiBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab -> selectedTab = tab },
                onCenterButtonClick = { isBubbleExpanded = !isBubbleExpanded }
            )
        }
    ) { padding ->
        // 内容区域：根据 selectedTab 显示不同页面
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                TODO -> HomeScreen(...)           // 已有
                INSPIRE -> InspireScreen()       // 新建
                DATE -> DateScreen()             // 新建
                PROFILE -> ProfileScreen(...)    // 已有
            }
        }

        // 气泡菜单覆盖层
        if (isBubbleExpanded) {
            BubbleMenuOverlay(
                onDismiss = { isBubbleExpanded = false },
                onBubbleClick = { bubbleType ->
                    isBubbleExpanded = false
                    navigateToEdit(bubbleType)
                }
            )
        }
    }
}
```

---

## 4. 详细设计规格

### 4.1 底部导航栏布局

#### 尺寸规范
| 属性 | 值 | 说明 |
|------|-----|------|
| 导航栏高度 | 56dp | Material Design 标准高度 |
| 背景色 | White (#FFFFFF) | 白色背景 |
| 顶部边框 | 1px solid #E0E0E0 | 分割线 |
| 阴影 | elevation = 8.dp | Material3 标准 |

#### Tab 项布局
```
┌──────────────────────────────────────────────────┐
│  [📝]      [💡]    [⊕]↑    [📅]      [👤]       │
│  待办      灵感    (编辑)    日期      我的        │
│  (选中)                                        │
└──────────────────────────────────────────────────┘
   ↑         ↑       ↑↑       ↑         ↑
  Tab1      Tab2   中央按钮  Tab4      Tab5
```

**Tab 项间距计算：**
- 屏幕宽度等分为 5 份
- 每个 Tab 占据 20% 宽度
- 中央按钮位于正中心，向上偏移 8dp

#### 图标与文字规格
| 属性 | 未选中状态 | 选中状态 |
|------|----------|---------|
| 图标尺寸 | 24×24dp | 24×24dp |
| 图标颜色 | #999999 | #FF9A5C (暖橙色) |
| 文字大小 | 12sp | 12sp |
| 文字颜色 | #999999 | #FF9A5C (暖橙色) |
| 字重 | Normal | SemiBold (600) |

### 4.2 中央编辑按钮设计

#### 按钮规格
| 属性 | 值 |
|------|-----|
| 形状 | 圆形 (CircleShape) |
| 尺寸 | 56×56dp |
| 背景色 | #FF9A5C (暖橙色) |
| 图标颜色 | White (#FFFFFF) |
| 图标大小 | 28sp (⊕ / ✕) |
| 向上偏移 | 8dp (突出导航栏) |
| 阴影 | elevation = 12.dp, 暖色调阴影 |
| 点击波纹效果 | ripple(color = White, alpha = 0.3) |

#### 状态切换逻辑
```kotlin
// 按钮状态枚举
enum class CenterButtonState {
    COLLAPSED,  // 显示 ⊕ (加号)
    EXPANDED    // 显示 ✕ (关闭号，旋转45°)
}

// 旋转动画
val rotation by animateFloatAsState(
    targetValue = if (isExpanded) 45f else 0f,
    animationSpec = tween(durationMillis = 200, easing = EaseInOut),
    label = "centerButtonRotation"
)
```

### 4.3 气泡菜单设计

#### 气泡布局（扇形展开）
```
              ┌─────────────┐
              │ 📝 创建待办  │  ← 气泡1 (上方, y=-80dp)
              └──────┬──────┘
                     │
    ┌────────────┐   │   ┌────────────┐
    │ 💡 记录灵感 │───⊕───│ 📅 特殊日期 │  ← 气泡2&3 (左右, x=±100dp)
    └────────────┘       └────────────┘
```

#### 气泡规格
| 属性 | 值 |
|------|-----|
| 形状 | 胶囊形 (RoundedCornerShape(24dp)) |
| 尺寸 | 宽度自适应（最小96dp），高度 48dp |
| 背景色 | White (#FFFFFF) |
| 边框 | 2px solid #FF9A5C |
| 阴影 | elevation = 6.dp |
| 内边距 | horizontal = 16dp, vertical = 0dp |
| 图标尺寸 | 20sp emoji |
| 文字大小 | 14sp |
| 文字颜色 | #333333 |
| 字重 | Medium (500) |

#### 气泡位置坐标（相对于中央按钮）
| 气泡 | 方向 | X 偏移 | Y 偏移 |
|------|------|--------|--------|
| 待办 | 上方 | 0dp | -90dp |
| 灵感 | 左侧 | -110dp | 0dp |
| 日期 | 右侧 | +110dp | 0dp |

#### 遮罩层
| 属性 | 值 |
|------|-----|
| 覆盖范围 | 全屏（除了底部导航栏区域） |
| 背景色 | Black, alpha = 0.3 (30% 不透明度) |
| 点击行为 | 收起气泡菜单 |
| 动画 | fadeIn/fadeOut 150ms |

---

## 5. 动画规格

### 5.1 Tab 切换动画
```kotlin
// 图标缩放 + 颜色渐变
val iconScale by animateFloatAsState(
    targetValue = if (isSelected) 1.1f else 1.0f,
    animationSpec = spring(dampingRatio = 0.7, stiffness = 400.0),
    label = "tabIconScale"
)

// 文字上移 + 透明度
val textOffsetY by animateDpAsState(
    targetValue = if (isSelected) (-4).dp else 0.dp,
    animationSpec = tween(durationMillis = 200, easing = EaseOutCubic),
    label = "textOffset"
)
```

**时序参数：**
- 总时长: 200ms
- 缓动函数: EaseOutCubic (先快后慢)
- 并行执行: 缩放、位移、颜色同时进行

### 5.2 中央按钮旋转动画
```kotlin
val rotation by animateFloatAsState(
    targetValue = if (isExpanded) 45f else 0f,
    animationSpec = tween(
        durationMillis = 200,
        easing = EaseInOutCubic
    ),
    label = "centerRotation"
)
```

**时序参数：**
- 时长: 200ms
- 旋转角度: 0° → 45° (⊕ → ✕)
- 缓动函数: EaseInOutCubic (匀速过渡)

### 5.3 气泡弹出动画（核心）

#### 弹出序列
```
时间轴:
0ms     ────→ 气泡1 (待办) 开始弹出
150ms   ────→ 气泡2 (灵感) 开始弹出
300ms   ────→ 气泡3 (日期) 开始弹出

每个气泡动画曲线:
opacity:  0 ─────────────→ 1 (200ms, easeOut)
scale:   0.5 ──────────→ 1.0 (200ms, easeOut)
offsetY: 20dp ─────────→ 0dp (200ms, easeOut)
```

**实现代码示例：**
```kotlin
@Composable
fun AnimatedBubble(
    isVisible: Boolean,
    delayMillis: Long,
    content: @Composable () -> Unit
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(delayMillis)  // 错开启动时间
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
        modifier = Modifier
            .graphicsLayer {
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

**收起动画（反向）：**
- 所有气泡同时开始收起（无延迟）
- 时长: 150ms（比弹出快）
- 缓动函数: EaseInCubic (先慢后快)

### 5.4 气泡点击反馈动画
```kotlin
// 点击时的缩放脉冲
LaunchedEffect(Unit) {
    scale.animateTo(1.2f, tween(100))   // 放大
    scale.animateTo(0.8f, tween(100))    // 缩小
    onComplete()                          // 执行跳转
}
```

---

## 6. 导航路由设计

### 6.1 新增 Screen 定义
```kotlin
sealed class Screen(val route: String) {
    // ... 已有的 Screen 定义 ...

    object Inspire : Screen("inspire")        // 新增：灵感页面
    object Date : Screen("date")              // 新增：特殊日期页面
}
```

### 6.2 NavGraph 配置更新
```kotlin
@Composable
fun AppNavHost(navController: NavController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        // 已有路由
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Profile.route) { ProfileScreen(navController) }

        // 新增路由
        composable(Screen.Inspire.route) { InspireScreen(navController) }
        composable(Screen.Date.route) { DateScreen(navController) }

        // 其他已有路由...
    }
}
```

### 6.3 页面跳转逻辑
```kotlin
// Tab 切换（不加入返回栈）
fun onTabSelected(tab: TabItem) {
    when (tab) {
        TODO -> navController.navigate(Screen.Home.route) { popUpTo(0) }
        INSPIRE -> navController.navigate(Screen.Inspire.route) { popUpTo(0) }
        DATE -> navController.navigate(Screen.Date.route) { popUpTo(0) }
        PROFILE -> navController.navigate(Screen.Profile.route) { popUpTo(0) }
    }
}

// 气泡点击（进入编辑页面）
fun onBubbleClick(type: BubbleType) {
    when (type) {
        TODO -> navController.navigate("todo_edit")
        INSPIRE -> navController.navigate("inspire_edit")
        DATE -> navController.navigate("date_edit")
    }
}
```

---

## 7. 状态管理

### 7.1 需要管理的状态
```kotlin
data class BottomNavState(
    val selectedTab: TabItem = TabItem.TODO,           // 当前选中的 Tab
    val isBubbleExpanded: Boolean = false,              // 气泡是否展开
    val centerButtonState: CenterButtonState = CenterButtonState.COLLAPSED
)

enum class TabItem {
    TODO,      // 待办
    INSPIRE,   // 灵感
    EDIT,      // 编辑（中央按钮，非真实 Tab）
    DATE,      // 日期
    PROFILE    // 我的
}

enum class BubbleType {
    CREATE_TODO,    // 创建待办
    RECORD_INSPIRE, // 记录灵感
    SPECIAL_DATE    // 特殊日期
}
```

### 7.2 状态提升策略
- **MainScreen** 持有所有状态（单一数据源）
- 通过参数传递给子组件
- 子组件通过回调通知父组件状态变更

---

## 8. 交互流程

### 8.1 正常使用流程
```
用户打开 APP
    ↓
显示首页（TODO Tab 高亮）
    ↓
用户点击其他 Tab（如"灵感"）
    ↓
切换到灵感页面（INSPIRE Tab 高亮，气泡自动收起）
    ↓
用户点击中央 ⊕ 按钮
    ↓
⊕ 旋转 45° 变为 ✕
    ↓
三个气泡依次弹出（上、左、右）
    ↓
用户点击"记录灵感"气泡
    ↓
气泡执行缩放脉冲动画
    ↓
气泡收起（快速收回 100ms）
    ↓
跳转到灵感编辑页面
```

### 8.2 边界情况处理
| 场景 | 处理方式 |
|------|---------|
| 气泡展开时切换 Tab | 自动收起气泡（100ms 快速收回） |
| 气泡展开时按返回键 | 收起气泡（不退出应用） |
| 快速连续点击 ⊕ | 忽略动画中的点击（防抖） |
| 页面滚动时 | 导航栏保持固定（不隐藏） |

---

## 9. 无障碍性（Accessibility）

### 9.1 内容描述
```kotlin
// 中央按钮
Modifier.semantics {
    contentDescription = if (isExpanded) "关闭编辑菜单" else "打开编辑菜单"
    role = Role.Button
}

// 气泡项
Modifier.semantics {
    contentDescription = "创建新待办"
    role = Role.Button
}
```

### 9.2 键盘导航
- 支持 D-Pad 方向键在 Tab 间移动
- 中央按钮可通过 Enter/Space 触发
- ESC 键收起气泡菜单

### 9.3 TalkBack 支持
- 所有元素都有正确的 contentDescription
- 自定义动作（如"双击创建待办"）

---

## 10. 性能优化

### 10.1 渲染优化
- 使用 `remember` 缓存昂贵的计算结果
- 气泡组件使用 `key()` 确保 Compose 正确重组
- 避免在动画回调中创建新对象

### 10.2 内存优化
- 气泡收起时从组合中移除（而非仅设置 visibility=GONE）
- 使用 `DisposableEffect` 清理动画资源

### 10.3 动画性能
- 使用 `graphicsLayer` 进行属性动画（GPU 加速）
- 避免在动画期间触发重新布局（layout）

---

## 11. 测试要点

### 11.1 UI 测试
- [ ] 5 个 Tab 正确显示
- [ ] 选中状态高亮正确
- [ ] 中央按钮突出 8dp
- [ ] 点击 ⊕ 展开三个气泡
- [ ] 气泡位置符合设计稿
- [ ] 动画流畅无卡顿

### 11.2 交互测试
- [ ] Tab 切换正常工作
- [ ] 气泡点击进入对应页面
- [ ] 点击遮罩收起气泡
- [ ] 切换 Tab 时自动收起气泡
- [ ] 返回键正确处理

### 11.3 边界测试
- [ ] 快速连续点击不会导致异常
- [ ] 旋转屏幕后状态正确恢复
- [ ] 低端机型动画性能可接受

---

## 12. 实现文件清单

### 12.1 新建文件
```
app/src/main/java/com/corgimemo/app/ui/
├── screens/main/
│   └── MainScreen.kt                    # 主屏幕容器（新建）
├── components/navigation/
│   ├── CorgiBottomNavigationBar.kt      # 底部导航栏主体
│   ├── CenterEditButton.kt              # 中央编辑按钮
│   ├── BubbleMenuOverlay.kt             # 气泡菜单覆盖层
│   └── NavItem.kt                       # 导航项组件
└── screens/
    ├── inspire/
    │   └── InspireScreen.kt             # 灵感页面（占位）
    └── date/
        └── DateScreen.kt               # 日期页面（占位）
```

### 12.2 修改文件
```
app/src/main/java/com/corgimemo/app/ui/
├── navigation/
│   ├── AppNavHost.kt                   # 添加新路由
│   └── Screen.kt                       # 添加新的 Screen 对象
├── screens/home/
│   └── HomeScreen.kt                   # 移除 FAB，集成到 MainScreen
└── components/
    └── AnimatedFAB.kt                  # 可保留或删除（不再使用）
```

---

## 13. 实现优先级

### Phase 1: 核心功能（必须实现）
1. ✅ 创建 `MainScreen` 容器
2. ✅ 实现 `CorgiBottomNavigationBar` 基础布局
3. ✅ 实现 `CenterEditButton` 及旋转动画
4. ✅ 实现 Tab 切换逻辑
5. ✅ 集成现有 `HomeScreen` 和 `ProfileScreen`

### Phase 2: 气泡菜单（核心体验）
6. ✅ 实现 `BubbleMenuOverlay` 布局
7. ✅ 实现气泡弹出/收起动画
8. ✅ 实现遮罩层及交互
9. ✅ 实现气泡点击跳转

### Phase 3: 完善优化（锦上添花）
10. ⭐ 添加 Tab 切换动画细节（缩放、位移）
11. ⭐ 创建占位页面（InspireScreen、DateScreen）
12. ⭐ 无障碍性适配
13. ⭐ 性能优化和边界处理

---

## 14. 风险与依赖

### 14.1 技术风险
| 风险 | 影响 | 应对措施 |
|------|------|---------|
| 动画卡顿 | 用户体验差 | 使用 GPU 加速动画，低端机降级 |
| 状态同步复杂 | Bug 多发 | 单一状态源，明确数据流 |
| 与现有 FAB 冲突 | 功能重复 | 完全移除 AnimatedFAB |

### 14.2 外部依赖
- Navigation Compose 2.7+ （已满足）
- Material3 1.1+ （已满足）
- Compose Animation 1.5+ （已满足）

---

## 15. 验收标准

### 15.1 功能完整性
- [ ] 底部导航栏显示 5 个 Tab（待办、灵感、编辑、日期、我的）
- [ ] 中央 ⊕ 按钮突出 8dp，暖橙色
- [ ] 点击 ⊕ 展开三个气泡（扇形排列）
- [ ] 气泡动画流畅（200ms 弹出，150ms 收起）
- [ ] 点击气泡进入对应编辑页
- [ ] 当前页面 Tab 高亮显示
- [ ] 切换 Tab 正确切换页面

### 15.2 设计还原度
- [ ] 布局尺寸符合设计稿（误差 < 2dp）
- [ ] 颜色值精确匹配（#FF9A5C 等）
- [ ] 动画时序符合规格（±10ms）
- [ ] 交互反馈符合预期

### 15.3 代码质量
- [ ] 组件化清晰，职责单一
- [ ] 状态管理合理，无明显内存泄漏
- [ ] 代码注释完整（中文注释）
- [ ] 无明显性能问题

---

## 附录 A: 关键代码片段

### A.1 底部导航栏基础结构
```kotlin
@Composable
fun CorgiBottomNavigationBar(
    selectedTab: TabItem,
    onTabSelected: (TabItem) -> Unit,
    onCenterButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shadowElevation = 8.dp,
        color = Color.White,
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(top = 1.dp, Color(0xFFE0E0E0)),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: 待办
            NavItem(
                icon = "📝",
                label = "待办",
                isSelected = selectedTab == TabItem.TODO,
                onClick = { onTabSelected(TabItem.TODO) }
            )

            // Tab 2: 灵感
            NavItem(
                icon = "💡",
                label = "灵感",
                isSelected = selectedTab == TabItem.INSPIRE,
                onClick = { onTabSelected(TabItem.INSPIRE) }
            )

            // 中央编辑按钮（占位）
            Box(modifier = Modifier.size(56.dp)) {
                CenterEditButton(
                    onClick = onCenterButtonClick
                )
            }

            // Tab 4: 日期
            NavItem(
                icon = "📅",
                label = "日期",
                isSelected = selectedTab == TabItem.DATE,
                onClick = { onTabSelected(TabItem.DATE) }
            )

            // Tab 5: 我的
            NavItem(
                icon = "👤",
                label = "我的",
                isSelected = selectedTab == TabItem.PROFILE,
                onClick = { onTabSelected(TabItem.PROFILE) }
            )
        }
    }
}
```

### A.2 气泡动画实现骨架
```kotlin
@Composable
fun BubbleMenuOverlay(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onBubbleClick: (BubbleType) -> Unit,
    modifier: Modifier = Modifier
) {
    // 半透明遮罩
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onDismiss)
    ) {
        // 气泡容器
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // 气泡1: 创建待办（上方）
            AnimatedBubble(
                isVisible = isExpanded,
                delayMillis = 0
            ) {
                BubbleItem(
                    icon = "📝",
                    text = "待办",
                    onClick = { onBubbleClick(BubbleType.CREATE_TODO) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 气泡2 & 3: 左右排列
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                AnimatedBubble(
                    isVisible = isExpanded,
                    delayMillis = 150
                ) {
                    BubbleItem(
                        icon = "💡",
                        text = "灵感",
                        onClick = { onBubbleClick(BubbleType.RECORD_INSPIRE) }
                    )
                }

                // 中央 ✕ 按钮
                CenterEditButton(isExpanded = true, onClick = onDismiss)

                AnimatedBubble(
                    isVisible = isExpanded,
                    delayMillis = 300
                ) {
                    BubbleItem(
                        icon = "📅",
                        text = "日期",
                        onClick = { onBubbleClick(BubbleType.SPECIAL_DATE) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(120.dp))  // 为底部导航栏留空间
        }
    }
}
```

---

**文档版本**: v1.0
**最后更新**: 2026-05-27
**作者**: AI Assistant
**审核状态**: ⏳ 待用户审核
