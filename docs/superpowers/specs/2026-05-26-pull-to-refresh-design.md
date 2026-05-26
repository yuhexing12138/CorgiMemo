# 下拉刷新功能设计方案

**日期**: 2026-05-26
**方案**: Accompanist SwipeRefresh
**状态**: ✅ 已批准

## 1. 背景与目标

### 1.1 当前问题
- Material3 1.2.1 版本的 `PullToRefreshBox` API 编译错误或不可用
- HomeScreen.kt 已移除下拉刷新功能，使用普通 LazyColumn
- CorgiPullToRefreshIndicator 组件已创建但未集成

### 1.2 目标
启用首页待办列表的下拉刷新功能，使用柯基动画作为刷新指示器，提供流畅的用户体验。

## 2. 技术选型

### 2.1 方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **A: Accompanist SwipeRefresh** ✅ | 成熟稳定、已有依赖、完美集成 | 需要额外导入 | ⭐⭐⭐⭐⭐ |
| B: 升级 Material3 1.3.x+ | 原生 API、未来兼容性 | 可能有 breaking changes | ⭐⭐⭐ |
| C: 手动 NestedScroll | 完全可控 | 工作量大、易出错 | ⭐⭐ |

### 2.2 最终选择
**方案 A: Accompanist SwipeRefresh (0.34.0)**
- 项目已有 accompanist 依赖（当前仅使用 permissions）
- 成熟稳定，被广泛使用
- 与 Compose 动画系统完美集成
- 支持自定义 indicator（可复用 CorgiPullToRefreshIndicator）

## 3. 架构设计

### 3.1 组件层次结构

```
HomeScreen.kt
└── SwipeRefresh (Accompanist)
    ├── CorgiPullToRefreshIndicator (自定义柯基动画)
    └── LazyColumn
        └── animatedItems()
            └── TodoListItem × N
```

### 3.2 数据流

```
用户下拉手势
    ↓
SwipeRefresh 检测到下拉距离超过阈值
    ↓
触发 onRefresh 回调
    ↓
HomeViewModel.onRefresh()
    ├── _isRefreshing.value = true
    ├── loadTodos()
    ├── refreshSubTaskProgress()
    ├── refreshGreetingIfNeeded()
    ├── delay(800ms) // 最小刷新时间
    └── _isRefreshing.value = false
    ↓
UI 更新：
- SwipeRefreshState.isRefreshing 同步更新
- CorgiPullToRefreshIndicator 显示/隐藏动画
- LazyColumn 数据刷新
```

## 4. 实现细节

### 4.1 依赖配置

#### gradle/libs.versions.toml (无需修改)
```toml
accompanist = "0.34.0"  # 已有
```

#### app/build.gradle.kts (需要添加)
```kotlin
dependencies {
    // 现有的 accompanist permissions
    implementation(libs.google.accompanist.permissions)

    // 新增：swiperefresh
    implementation("com.google.accompanist:accompanist-swiperefresh:${libs.versions.accompanist.get()}")
}
```

### 4.2 HomeScreen.kt 修改

#### 导入语句
```kotlin
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.corgimemo.app.ui.components.CorgiPullToRefreshIndicator
```

#### 核心实现代码
```kotlin
// 在 HomeScreen Composable 内部
val isRefreshing by viewModel.isRefreshing.collectAsState()
val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

// 替换现有的 LazyColumn 包裹逻辑
if (!filteredTodos.isEmpty()) {
    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = { viewModel.onRefresh() },
        indicator = { state, refreshTrigger ->
            CorgiPullToRefreshIndicator(
                isRefreshing = isRefreshing,
                pullProgress = if (refreshTrigger > 0f) 1f else 0f
            )
        }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            animatedItems(
                items = filteredTodos,
                key = { it.id }
            ) { todo, _ ->
                // 现有的 TodoListItem 渲染代码保持不变
            }
        }
    }
}
```

### 4.3 HomeViewModel.kt (已实现)

```kotlin
// 刷新状态定义 (已有)
private val _isRefreshing = MutableStateFlow(false)
val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

// 刷新方法 (已有)
fun onRefresh() {
    viewModelScope.launch {
        _isRefreshing.value = true
        try {
            loadTodos()
            refreshSubTaskProgress()
            refreshGreetingIfNeeded()
            delay(800) // 最小刷新时间，避免闪烁
        } catch (e: Exception) {
            e.printStackTrace()
            // 可选：通过 Snackbar 或 Toast 通知用户
        } finally {
            _isRefreshing.value = false
        }
    }
}
```

### 4.4 CorgiPullToRefreshIndicator (已创建)

组件位于：`app/src/main/java/com/corgimemo/app/ui/components/CorgiPullToRefreshIndicator.kt`

**动画序列**：
1. **用户下拉时**：柯基从右侧跑入（RUN 帧动画），进度跟随下拉距离
2. **达到阈值后**：柯基完全显示，切换为摇尾巴（WAG 帧动画）
3. **刷新中**：柯基持续摇尾巴 + 提示文字"柯基努力加载中~"
4. **刷新完成**：柯基向右滑出消失（800ms EaseIn 动画）

**参数接口**：
```kotlin
@Composable
fun CorgiPullToRefreshIndicator(
    isRefreshing: Boolean,      // 是否正在刷新
    pullProgress: Float,         // 下拉进度 (0f-1f+)
    modifier: Modifier = Modifier
)
```

## 5. 边界情况处理

### 5.1 场景矩阵

| 场景 | 处理方式 | 代码位置 |
|------|----------|----------|
| **快速连续下拉** | 防抖：isRefreshing 为 true 时忽略新请求 | ViewModel.onRefresh() |
| **网络异常** | finally 块确保状态重置 + 日志记录 | ViewModel.onRefresh() |
| **空列表时** | SwipeRefresh 正常工作，EmptyState 显示在内部 | HomeScreen.kt |
| **滚动中下拉** | NestedScrollConnection 自动协调 | Accompanist 内部处理 |
| **列表项动画冲突** | animatedItems 与 SwipeRefresh 独立运行 | Compose 框架保证 |
| **深色模式** | CorgiPullToRefreshIndicator 使用 MaterialTheme 颜色 | Indicator 组件内 |

### 5.2 性能优化

- **最小刷新时间 (800ms)**：避免 UI 闪烁，让用户感知到加载过程
- **NestedScrollConnection**：Accompanist 使用高效的嵌套滚动机制，无额外布局开销
- **状态双向绑定**：ViewModel.isRefreshing ↔ SwipeRefreshState 自动同步
- **动画帧率控制**：CorgiPullToRefreshIndicator 使用 8fps 帧动画，节省性能

## 6. 用户体验设计

### 6.1 视觉反馈流程

```
[初始状态] → [手指接触屏幕] → [开始下拉]
     ↓              ↓               ↓
  静态列表      无视觉变化     柯基从右侧出现（透明度渐入）
                                   ↓
                            [继续下拉] → [达到阈值]
                                 ↓           ↓
                           柯基跟随移动   文字提示："释放刷新"
                                               ↓
                                         [释放手指]
                                               ↓
                                    触发 onRefresh()
                                               ↓
                              [刷新中] → [刷新完成]
                                  ↓             ↓
                          "柯基努力加载中~"   柯基向右滑出消失
                              + 摇尾巴动画       (800ms)
```

### 6.2 交互规范

- **触发阈值**：默认 80dp（Accompanist 标准）
- **回弹动画**：弹性效果（Spring Animation）
- **刷新指示器高度**：80dp（CorgiPullToRefreshIndicator）
- **柯基尺寸**：64dp（适中，不遮挡内容）
- **文字大小**：12sp（次要信息）

## 7. 测试计划

### 7.1 功能测试

- [ ] 正常下拉刷新：验证数据重新加载
- [ ] 快速连续下拉：验证防抖机制
- [ ] 空列表下拉：验证 EmptyState 兼容性
- [ ] 滚动中下拉：验证 NestedScroll 协调
- [ ] 网络异常：验证错误处理和状态重置
- [ ] 深色模式：验证颜色适配

### 7.2 性能测试

- [ ] 内存泄漏检测：确认无泄漏
- [ ] 帧率监控：确保 60fps 流畅
- [ ] 长列表测试：1000+ 项时的滚动性能

## 8. 后续优化方向 (可选)

1. **自定义下拉阈值**：允许用户在设置中调整敏感度
2. **下拉统计**：记录用户下拉频率，用于行为分析
3. **多场景扩展**：其他列表页面（已完成、分类）也支持下拉刷新
4. **离线缓存**：下拉时优先显示本地缓存，后台静默刷新
5. **Haptic 反馈**：达到阈值时提供触觉反馈（需配合 HapticFeedbackManager）

## 9. 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| Accompanist 版本兼容性 | 低 | 中 | 使用项目已有的 0.34.0 版本 |
| 与现有动画冲突 | 低 | 低 | 独立的 Compose State 管理 |
| 性能问题 | 极低 | 中 | 使用 NestedScrollConnection 高效实现 |

## 10. 总结

本方案采用 **Accompanist SwipeRefresh** 实现下拉刷新功能：

✅ **优势**：
- 复用已有的稳定依赖（0.34.0）
- 完美集成现有 CorgiPullToRefreshIndicator 柯基动画
- 代码改动量小（仅需修改 HomeScreen.kt 和添加依赖）
- 成熟稳定，社区广泛使用

📝 **工作量估计**：
- 修改文件数：2 个（build.gradle.kts, HomeScreen.kt）
- 新增代码行数：~20 行
- 测试时间：30 分钟

🚀 **下一步**：进入实施阶段，按照此设计方案进行编码实现。
