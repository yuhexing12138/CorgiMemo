# 空状态引导功能设计文档

> **日期**: 2026-05-25  
> **方案**: 方案 A - 组件化增强  
> **状态**: 待审批  

---

## 1. 功能概述

### 1.1 目标
增强 CorgiMemo 应用的空状态页面，通过**柯基动画引导 + 操作指引 + 模板预设 + 首次使用引导**，帮助新用户快速上手，提升用户留存率。

### 1.2 核心功能点
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 空状态引导动画 | 柯基帧动画（TILT+WAG 交替）+ 气泡文字 | P0 |
| 操作指引增强 | 文字提示 + 箭头指向 FAB + 语音输入提示 | P0 |
| 模板预设功能 | 底部横向滚动模板卡片，一键创建待办 | P0 |
| 首次使用引导 | 4 步引导流程（仅首次 + 可重置） | P1 |

---

## 2. 架构设计

### 2.1 组件层次结构

```
HomeScreen.kt
└── EmptyState (增强版)
    ├── CorgiGuideAnimation        # 柯基引导动画区域
    │   ├── FrameAnimation         # TILT/WAG 帧动画
    │   └── SpeechBubble          # "柯基等你很久啦！"
    ├── OperationGuide             # 操作指引区域
    │   ├── GuideText             # "点击下方 + 按钮..."
    │   ├── ArrowAnimation        # 箭头指向 FAB
    │   └── VoiceInputHint        # 语音输入提示
    ├── TemplateCarousel           # 模板轮播区域
    │   └── TemplateCard[]        # 模板卡片列表
    └── FirstTimeGuideOverlay      # 首次引导覆盖层（条件渲染）
        ├── Step1: CorgiIntro     # 柯基自我介绍
        ├── Step2: FabHighlight   # 高亮 FAB 按钮
        ├── Step3: VoiceDemo      # 语音输入演示（可选）
        └── Step4: TemplateRec    # 推荐选择模板
```

### 2.2 文件变更清单

| 文件路径 | 操作 | 说明 |
|----------|------|------|
| `ui/components/EmptyState.kt` | **修改** | 增强为 EnhancedEmptyState |
| `ui/components/CorgiGuideAnimation.kt` | **新增** | 柯基引导动画组件 |
| `ui/components/OperationGuide.kt` | **新增** | 操作指引组件 |
| `ui/components/TemplateCarousel.kt` | **新增** | 模板轮播组件 |
| `ui/components/FirstTimeGuideOverlay.kt` | **新增** | 首次引导覆盖层 |
| `data/model/TemplateData.kt` | **新增** | 模板数据定义 |
| `data/local/datastore/CorgiPreferences.kt` | **修改** | 新增 first_guide_shown 键 |
| `ui/screens/home/HomeScreen.kt` | **修改** | 集成新组件 |

---

## 3. 详细设计

### 3.1 空状态引导动画 (`CorgiGuideAnimation`)

#### 功能特性
- **帧动画组合**: TILT（歪头）和 WAG（摇尾巴）交替播放，每 3 秒切换一次
- **浮动效果**: 整体上下浮动 8dp，使用 `Animatable` 实现平滑动画
- **气泡文字**: 柯基头部上方显示对话气泡"🐕 柯基等你很久啦！"
- **气泡动画**: 气泡淡入 + 轻微弹跳效果

#### 技术实现
```kotlin
@Composable
fun CorgiGuideAnimation(
    modifier: Modifier = Modifier
) {
    // 动画状态：当前播放的动画类型（TILT 或 WAG）
    var currentAnimationType by remember { mutableStateOf(AnimationType.TILT) }
    
    // 每 3 秒切换动画类型
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            currentAnimationType = if (currentAnimationType == AnimationType.TILT) 
                AnimationType.WAG else AnimationType.TILT
        }
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 对话气泡
        SpeechBubble(text = "🐕 柯基等你很久啦！")
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 柯基帧动画（带浮动效果）
        Box(modifier = Modifier.offset { IntOffset(0, floatOffset.value.toInt()) }) {
            FrameAnimation(
                animationType = currentAnimationType,
                fps = 8,
                isLooping = true,
                modifier = Modifier.size(140.dp)
            )
        }
    }
}
```

### 3.2 操作指引增强 (`OperationGuide`)

#### 功能特性
- **主引导卡片**: 圆角卡片显示"👆 点击下方 + 按钮添加你的第一个待办吧"
- **箭头动画**: 使用 Canvas 绘制向下箭头，带脉冲动画指向 FAB 位置
- **消失逻辑**: 用户点击 FAB 后箭头消失（通过回调通知父组件）
- **语音提示**: 底部显示"💡 试试说：'明天开会' 柯基会帮你创建待办哦~"

#### 视觉设计
```
┌─────────────────────────────┐
│                             │
│   👆 点击下方 + 按钮         │
│   添加你的第一个待办吧       │
│                             │
│          ↓                  │
│      (脉冲箭头)              │
│                             │
│  💡 试试说："明天开会"       │
│     柯基会帮你创建待办哦~    │
└─────────────────────────────┘
```

### 3.3 模板预设功能 (`TemplateCarousel`)

#### 数据结构
```kotlin
/**
 * 待办模板数据类
 */
data class TodoTemplate(
    val id: String,              // 模板唯一标识
    val name: String,            // 模板名称
    val icon: String,            // 模板图标（Emoji）
    val description: String,     // 模板描述
    val todos: List<TemplateTodo> // 包含的待办项列表
)

data class TemplateTodo(
    val title: String,           // 待办标题
    val category: String? = null // 可选分类
)

object TemplateData {
    /** 预定义模板列表 */
    val templates = listOf(
        TodoTemplate(
            id = "daily_habits",
            name = "每日习惯",
            icon = "☀️",
            description = "养成好习惯，从今天开始",
            todos = listOf(
                TemplateTodo("早起打卡"),
                TemplateTodo("喝8杯水"),
                TemplateTodo("运动30分钟"),
                TemplateTodo("阅读15分钟"),
                TemplateTodo("早睡准备")
            )
        ),
        TodoTemplate(
            id = "work_weekly",
            name = "工作周计划",
            icon = "💼",
            description = "高效工作，有序安排",
            todos = listOf(
                TemplateTodo("周一例会", "工作"),
                TemplateTodo("周三汇报", "工作"),
                TemplateTodo("周五总结", "工作"),
                TemplateTodo("处理邮件", "工作")
            )
        ),
        TodoTemplate(
            id = "study_plan",
            name = "学习计划",
            icon = "📚",
            description = "持续学习，不断进步",
            todos = listOf(
                TemplateTodo("背单词30个", "学习"),
                TemplateTodo("阅读专业书籍", "学习"),
                TemplateTodo("复习笔记", "学习"),
                TemplateTodo("完成练习题", "学习")
            )
        )
    )
}
```

#### UI 设计
- **横向滚动**: 使用 `LazyRow` 实现，卡片宽度 160dp，间距 12dp
- **卡片样式**: 圆角 16dp，阴影 elevation=4dp，包含图标+名称+描述
- **点击交互**: 点击后调用 `onTemplateSelected(template)` 回调，批量创建待办

### 3.4 首次使用引导 (`FirstTimeGuideOverlay`)

#### 触发条件
```kotlin
// 在 HomeScreen 中判断
val isFirstGuideShown by corgiPreferences.firstGuideShown.collectAsState(initial = false)
val showFirstTimeGuide = !isFirstGuideShown && todos.isEmpty()
```

#### 引导流程（4 步）

| 步骤 | 内容 | 交互 | 时长 |
|------|------|------|------|
| **Step 1** | 柯基自我介绍动画 + "嗨！我是你的待办小助手~" | 自动播放 → 点击"下一步" | 3s |
| **Step 2** | 高亮 FAB 按钮（脉冲光圈）+ "点击这里添加待办" | 用户点击 FAB 或跳过 | - |
| **Step 3** | 语音输入演示（可选）+ "试试语音创建待办" | 用户尝试或跳过 | - |
| **Step 4** | 显示模板推荐 + "或者选择一个模板开始" | 选择模板或跳过 | - |

#### 状态管理
```kotlin
/** 首次引导步骤枚举 */
enum class GuideStep {
    INTRO,          // 自我介绍
    FAB_HIGHLIGHT,  // 高亮 FAB
    VOICE_DEMO,     // 语音演示（可选）
    TEMPLATE_REC    // 模板推荐
}

/** 引导状态 */
data class GuideState(
    val currentStep: GuideStep = GuideStep.INTRO,
    val isVisible: Boolean = false,
    val isCompleted: Boolean = false
)
```

#### 完成标记
引导完成（用户走完流程或点击"跳过"）后：
```kotlin
// 在 CorgiPreferences 中新增
val FIRST_GUIDE_SHOWN = booleanPreferencesKey("first_guide_shown")

suspend fun setFirstGuideShown() {
    dataStore.edit { prefs ->
        prefs[FIRST_GUIDE_SHOWN] = true
    }
}

// 重置方法（用于设置中的"重新查看引导"选项）
suspend fun resetFirstGuide() {
    dataStore.edit { prefs ->
        prefs[FIRST_GUIDE_SHOWN] = false
    }
}
```

---

## 4. 交互流程

### 4.1 正常空状态流程（非首次）

```
用户打开 APP
    ↓
待办列表为空？
    ↓ 是
显示 EnhancedEmptyState
    ↓
├─ 柯基动画循环播放（TILT ↔ WAG）
├─ 操作指引卡片 + 箭头动画
├─ 语音输入提示
└─ 模板轮播（可点击创建）
    ↓
用户操作：
 ├─ 点击 FAB → 导航到待办编辑页
 ├─ 点击模板 → 批量创建待办
 └─ 语音输入 → 创建待办
```

### 4.2 首次使用流程

```
首次打开 APP + 待办为空
    ↓
显示 FirstTimeGuideOverlay（覆盖层）
    ↓
Step 1: 柯基自我介绍 → [下一步]
    ↓
Step 2: 高亮 FAB → 用户点击 FAB 或 [跳过]
    ↓
Step 3: 语音演示（可选）→ [跳过]
    ↓
Step 4: 模板推荐 → 选择模板或 [开始使用]
    ↓
标记 first_guide_shown = true
    ↓
显示正常空状态界面
```

---

## 5. 数据存储

### 5.1 新增 DataStore 键

| 键名 | 类型 | 默认值 | 用途 |
|------|------|--------|------|
| `first_guide_shown` | Boolean | false | 是否已完成首次引导 |

### 5.2 修改文件
- **CorgiPreferences.kt**: 新增 `firstGuideShown` Flow、`setFirstGuideShown()`、`resetFirstGuide()` 方法

---

## 6. 性能考虑

### 6.1 动画优化
- **帧动画缓存**: 复用现有 `FrameAnimation` 的资源管理机制
- **动画暂停**: 当页面不可见时（`Lifecycle.Event.ON_STOP）暂停动画
- **内存管理**: 模板数据使用 `object` 单例，避免重复创建

### 6.2 渲染优化
- **LazyRow**: 模板轮播使用懒加载，只渲染可见卡片
- **AnimatedVisibility**: 条件渲染首次引导层，避免不必要的布局计算

---

## 7. 可访问性 & 国际化

### 7.1 无障碍支持
- 所有图片元素添加 `contentDescription`
- 箭头动画提供静态替代方案（对动态效果敏感的用户）
- 模板卡片支持 TalkBack 朗读

### 7.2 文本硬编码
- 当前版本所有文本中文硬编码（符合项目现状）
- 未来可通过 `strings.xml` 扩展国际化

---

## 8. 测试策略

### 8.1 单元测试
- `TemplateData`: 验证模板数据完整性
- `CorgiPreferences`: 验证 `firstGuideShown` 的读写逻辑

### 8.2 UI 测试
- 验证空状态组件正确显示所有子组件
- 验证首次引导流程的步骤切换
- 验证 FAB 点击后箭头消失

### 8.3 手动测试场景
| 场景 | 预期结果 |
|------|----------|
| 首次安装 + 无待办 | 显示完整引导流程 |
| 非首次 + 无待办 | 显示基础空状态（无引导覆盖层） |
| 有待办列表 | 不显示空状态 |
| 设置中重置引导 | 下次进入空状态时重新显示引导 |

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| EmptyState 组件过大 | 可维护性降低 | 拆分为独立子组件 |
| 首次引导打断用户 | 用户流失 | 提供"跳过"按钮，流程简洁 |
| 模板数据过时 | 用户体验下降 | 支持后续扩展为在线模板 |
| 动画性能问题 | 卡顿 | 使用 Compose 动画 API，避免过度绘制 |

---

## 10. 后续扩展方向

1. **A/B 测试**: 不同引导流程的转化率对比
2. **个性化模板**: 基于用户行为推荐模板
3. **引导编辑器**: 运营配置引导内容（无需发版）
4. **多语言支持**: 提取字符串到资源文件

---

## 附录 A: 组件接口定义

### EnhancedEmptyState
```kotlin
@Composable
fun EnhancedEmptyState(
    emptyType: EmptyStateType = EmptyStateType.PENDING,
    categoryName: String? = null,
    onAction: (() -> Unit)? = null,
    onFabClicked: () -> Unit,           // FAB 被点击时的回调
    onTemplateSelected: (TodoTemplate) -> Unit,  // 模板被选中时的回调
    showFirstTimeGuide: Boolean = false,  // 是否显示首次引导
    onGuideCompleted: () -> Unit,         // 引导完成的回调
    modifier: Modifier = Modifier
)
```

### TemplateCard
```kotlin
@Composable
fun TemplateCard(
    template: TodoTemplate,
    onClick: (TodoTemplate) -> Unit,
    modifier: Modifier = Modifier
)
```

---

*文档结束*
