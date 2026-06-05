# 骨架屏 + 预加载优化设计文档

> **日期**：2026-06-04  
> **状态**：待审核  
> **优先级**：高  

---

## 1. 背景与问题

### 1.1 问题描述
当用户退出 APP 再进入时，如果待办/灵感/日期三个页面有内容，会出现**内容从无到有的闪烁现象**。切换页面后不再闪烁（因为 ViewModel 已缓存数据）。

### 1.2 根本原因
- StateFlow 初始值为 `emptyList()`
- 数据通过异步 Flow 从 Room 数据库加载
- UI 先渲染空状态，数据库查询完成后再渲染真实列表 → 视觉闪烁

### 1.3 已完成的修复
已在 [HomeViewModel](../../app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt)、[InspirationViewModel](../../app/src/main/java/com/corgimemo/app/viewmodel/InspirationViewModel.kt)、[SpecialDateViewModel](../../app/src/main/java/com/corgimemo/app/viewmodel/SpecialDateViewModel.kt) 中添加 `isDataInitialized` 标志位。

当前使用 `CircularProgressIndicator` 作为加载占位，本次优化将其替换为**页面专属骨架屏**并增加**Application 级预加载**。

---

## 2. 设计目标

| 目标 | 说明 |
|------|------|
| **消除闪烁** | 冷启动时从骨架屏平滑过渡到真实内容 |
| **视觉一致性** | 骨架屏布局与真实页面布局高度相似 |
| **治愈感** | 符合"温暖、舒适、治愈"的设计理念 |
| **性能优化** | 通过预加载缩短首帧时间 |

---

## 3. 技术方案

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    CorgiMemoApplication                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           ProcessLifecycleOwner 监听                   │   │
│  │    (应用进入前台 → 触发 ViewModel 预加载)              │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              OnboardingRouter                         │   │
│  │    (检查引导状态 → 导航到 MainScreen)                  │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      MainScreen                              │
│  ┌──────────┬──────────┬──────────┐                        │
│  │ HomeScreen│Inspiration│ Date     │                        │
│  │ Skeleton │  Screen   │ Screen   │                        │
│  │          │  Skeleton │ Skeleton │                        │
│  └──────────┴──────────┴──────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 方案一：页面专属骨架屏

#### 3.2.1 设计原则
- **布局一致性**：骨架屏元素位置、尺寸与真实组件完全一致
- **颜色柔和**：使用 `surfaceVariant` 或自定义浅灰色（符合设计规范）
- **圆角统一**：卡片 20dp、按钮 16dp（遵循 UI 设计规范）
- **动画可选**：支持 shimmer 扫光效果（默认关闭，可配置）

#### 3.2.2 待办页骨架屏 (TodoSkeleton)

```
┌─────────────────────────────────────────────┐
│  ┌─────────────────────────────────────┐   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  ▭▭▭▭  │   │  ← 搜索栏
│  └─────────────────────────────────────┘   │
│                                             │
│  [全部] [待办] [已完成]                      │  ← 过滤器按钮
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │ ○  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │  ← 卡片1
│  │    ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │
│  │    ▭▭▭  ▭▭▭▭                    │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │ ○  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │  ← 卡片2
│  │    ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │
│  │    ▭▭▭  ▭▭▭▭                    │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │ ○  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │  ← 卡片3
│  │    ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │
│  │    ▭▭▭  ▭▭▭▭                    │   │
│  └─────────────────────────────────────┘   │
│                                             │
│                                    [✏️ FAB] │
└─────────────────────────────────────────────┘
```

**组件结构**：
- 搜索栏骨架（圆角 12dp，高度 48dp）
- 过滤按钮骨架（3 个胶囊形，宽度 60dp）
- 卡片骨架（3-5 个）：
  - 左侧圆形复选框（24dp）
  - 标题行（高度 20dp，宽度 70%）
  - 副标题行（高度 14dp，宽度 50%）
  - 底部标签 + 日期（高度 12dp）

#### 3.2.3 灵感页骨架屏 (InspirationSkeleton)

```
┌─────────────────────────────────────────────┐
│  ┌─────────────────────────────────────┐   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  ▭▭▭▭  │   │  ← 搜索栏
│  └─────────────────────────────────────┘   │
│                                             │
│  ═══ 2026年6月4日 周四 ═══                  │  ← 时间线分组头
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │  ← 灵感卡片1
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭            │   │
│  │  📎 ▭▭▭  🏷️ ▭▭▭▭  ⏰ ▭▭:▭▭    │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │  ← 灵感卡片2
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭            │   │
│  │  📎 ▭▭▭  🏷️ ▭▭▭▭  ⏰ ▭▭:▭▭    │   │
│  └─────────────────────────────────────┘   │
│                                             │
│                                    [✏️ FAB] │
└─────────────────────────────────────────────┘
```

**组件结构**：
- 搜索栏骨架
- 时间线分组标题（日期文字）
- 灵感卡片骨架（2-3 个）：
  - 标题行（高度 18sp）
  - 内容预览（2 行）
  - 底部元信息行（图片/标签/时间图标）

#### 3.2.4 日期页骨架屏 (SpecialDateSkeleton)

```
┌─────────────────────────────────────────────┐
│  ┌─────────────────────────────────────┐   │
│  │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  ▭▭▭▭  │   │  ← 搜索栏
│  └─────────────────────────────────────┘   │
│                                             │
│  ═══ 即将到来 ═══                            │  ← 分组标题
│  ┌─────────────────────────────────────┐   │
│  │ 🔴 ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭    ▭▭天后    │   │  ← 日期卡片
│  │     ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭        │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  ═══ 正在纪念 ═══                            │
│  ┌─────────────────────────────────────┐   │
│  │ 🟢 ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭    ▭▭天      │   │
│  │     ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭        │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  ═══ 已过期 ═══                              │
│  ┌─────────────────────────────────────┐   │
│  │ ⚪ ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭    ▭▭天前    │   │
│  │     ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭        │   │
│  └─────────────────────────────────────┘   │
│                                             │
│                                    [✏️ FAB] │
└─────────────────────────────────────────────┘
```

**组件结构**：
- 搜索栏骨架
- 三组分组标题（即将到来/正在纪念/已过期）
- 每组 1-2 个日期卡片骨架：
  - 左侧颜色圆点（优先级指示）
  - 标题行
  - 副标题/分类
  - 右侧倒计时文字

### 3.3 方案二：Application 级预加载

#### 3.3.1 实现方式
在 [CorgiMemoApplication](../../app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt) 中：

```kotlin
@HiltAndroidApp
class CorgiMemoApplication : Application() {
    
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var inspirationRepository: InspirationRepository
    @Inject lateinit var specialDateRepository: SpecialDateRepository
    
    override fun onCreate() {
        super.onCreate()
        
        // ... 现有初始化代码 ...
        
        // 监听应用生命周期，进入前台时预热数据
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                @OnResume(STARTED)
                fun onForeground() {
                    // 触发 Repository 的 Flow 订阅（预热 Room 缓存）
                    CoroutineScope(Dispatchers.IO).launch {
                        // 预热待办数据
                        todoRepository.getAllTodos().first()
                        // 预热灵感数据
                        inspirationRepository.getAllInspirations().first()
                        // 预热日期数据
                        specialDateRepository.getAllDates().first()
                    }
                }
            }
        )
    }
}
```

#### 3.3.2 预加载时机
| 时机 | 说明 |
|------|------|
| **应用进入前台** | `ProcessLifecycleOwner.onStart()` |
| **用户解锁屏幕** | 自动触发 |
| **从最近任务恢复** | 自动触发 |

#### 3.3.3 注意事项
- 使用 `.first()` 仅获取首个值，不保持订阅（避免内存泄漏）
- 在 IO 线程执行，不阻塞主线程
- 与 ViewModel 的 `WhileSubscribed(5000)` 策略配合：
  - Application 预加载 → Room 返回缓存 → ViewModel 订阅时立即拿到数据

---

## 4. 组件 API 设计

### 4.1 通用骨架屏基础组件

```kotlin
/**
 * 骨架屏基础修饰符扩展
 * 提供统一的骨架屏样式（颜色、圆角、动画）
 */
fun Modifier.skeleton(
    shape: Shape = RoundedCornerShape(8.dp),
    shimmerEnabled: Boolean = false
): Modifier

/**
 * 骨架屏文本占位
 */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    lineHeight: TextLineHeight = TextLineHeight.Single
)

/**
 * 骨架屏卡片容器
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
)
```

### 4.2 页面专属骨架屏组件

```kotlin
/**
 * 待办页骨架屏
 */
@Composable
fun TodoSkeleton(
    itemCount: Int = 4,  // 显示的骨架卡片数量
    modifier: Modifier = Modifier
)

/**
 * 灵感页骨架屏
 */
@Composable
fun InspirationSkeleton(
    groupCount: Int = 1,     // 分组数量
    itemsPerGroup: Int = 2,  // 每组卡片数
    modifier: Modifier = Modifier
)

/**
 * 日期页骨架屏
 */
@Composable
fun SpecialDateSkeleton(
    modifier: Modifier = Modifier
)
```

---

## 5. 文件变更清单

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/Skeleton.kt` | **新建** | 通用骨架屏基础组件 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/TodoSkeleton.kt` | **新建** | 待办页专属骨架屏 |
| `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationSkeleton.kt` | **新建** | 灵感页专属骨架屏 |
| `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateSkeleton.kt` | **新建** | 日期页专属骨架屏 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | **修改** | 替换 CircularProgressIndicator 为 TodoSkeleton |
| `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt` | **修改** | 替换为 InspirationSkeleton |
| `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt` | **修改** | 替换为 SpecialDateSkeleton |
| `app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt` | **修改** | 添加预加载逻辑 |
| `app/build.gradle.kts` | **修改** | 添加 lifecycle-runtime-compose 依赖（如需要） |

---

## 6. 实现步骤

### Phase 1：骨架屏基础组件（核心）
1. 创建 `Skeleton.kt` - 通用骨架屏修饰符和组件
2. 定义骨架屏颜色常量（跟随主题）
3. 实现 shimmer 动画（可选）

### Phase 2：页面专属骨架屏
4. 创建 `TodoSkeleton.kt`
5. 创建 `InspirationSkeleton.kt`
6. 创建 `SpecialDateSkeleton.kt`

### Phase 3：集成到页面
7. 修改 `HomeScreen.kt` - 使用 TodoSkeleton
8. 修改 `InspirationScreen.kt` - 使用 InspirationSkeleton
9. 修改 `SpecialDateScreen.kt` - 使用 SpecialDateSkeleton

### Phase 4：预加载优化
10. 修改 `CorgiMemoApplication.kt` - 添加预加载逻辑
11. 测试验证冷启动体验

---

## 7. 验收标准

| 标准 | 验证方法 |
|------|---------|
| **无闪烁** | 冷启动进入有数据的页面，无空白/闪烁 |
| **布局一致** | 骨架屏与真实页面布局完全对齐 |
| **主题适配** | 亮色/深色模式下骨架屏颜色正确 |
| **性能无损** | 预加载不阻塞启动，内存无明显增长 |
| **平滑过渡** | 骨架屏 → 真实内容的过渡自然（可加 fade 动画） |

---

## 8. 后续优化方向（不在本次实现范围）

1. **Paging 3 集成**：对于大量数据场景，使用 Paging 3 替代全量加载
2. **骨架屏复用库**：提取为独立模块，供其他项目使用
3. **智能预加载策略**：根据用户习惯预测常用页面，优先预加载
4. **离线缓存增强**：使用 DataStore + 内存缓存双层策略

---

## 9. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 预加载导致启动变慢 | 低 | 使用 IO 线程 + 非阻塞 |
| 骨架屏布局与真实页面不一致 | 中 | 严格遵循 UI 设计规范的尺寸参数 |
| 内存占用增加 | 低 | 预加载仅获取首个值，不保持订阅 |
| shimmer 动画性能问题 | 低 | 默认关闭，按需开启 |

---

*文档结束*
