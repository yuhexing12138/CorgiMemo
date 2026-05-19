# 姿态管理系统实现计划

## 一、代码库调研结论

### 1.1 当前已实现的内容

**已存在的文件：**

| 文件路径 | 状态 | 功能说明 |
|---------|------|---------|
| `animation/CorgiPose.kt` | ✅ 已存在 | 定义了 5 种姿态枚举（LIE, SIT, STAND, RUN, SLEEP） |
| `animation/AnimationType.kt` | ✅ 已存在 | 定义了动画类型枚举 |
| `animation/AnimationResourceManager.kt` | ✅ 已存在 | 管理动画资源映射，包含姿态→动画类型的映射 |
| `animation/FrameAnimation.kt` | ✅ 已存在 | 帧动画播放组件，支持循环/单次播放、FPS 控制 |
| `animation/PoseManager.kt` | ⚠️ 部分实现 | 已有时间驱动的默认姿态选择，缺少场景驱动的姿态切换 |
| `animation/PoseAnimation.kt` | ✅ 已存在 | 使用 AnimatedContent 实现平滑过渡 |
| `viewmodel/HomeViewModel.kt` | ⚠️ 部分实现 | 已有 `currentPose` 状态，但缺少完整的场景驱动逻辑 |

### 1.2 当前实现与用户需求的差异

| 需求点 | 当前状态 | 差距 |
|--------|---------|------|
| 姿态枚举 LIE/SIT/STAND/RUN | ✅ 已定义 | 无差距 |
| 每种姿态对应帧动画 | ✅ 已实现 | 无差距 |
| 默认姿态：趴卧 (LIE) | ❌ 当前是时间驱动（白天坐立，深夜睡觉） | 需要修改默认逻辑 |
| 创建待办时：坐立 (SIT) | ❌ 未实现 | 需要在创建流程中添加姿态切换 |
| 完成任务时：站立 (STAND) | ✅ 已实现（在 HomeViewModel 中） | 无差距 |
| 加载中：奔跑 (RUN) | ❌ 未实现 | 需要添加加载状态的姿态 |
| 姿态切换平滑过渡 | ✅ 已实现（AnimatedContent） | 无差距 |
| 姿态管理器集成到 ViewModel | ⚠️ 部分实现 | 需要添加场景驱动的方法 |
| UI 层使用示例 | ⚠️ 已有 InteractiveCorgi | 可以优化示例代码 |

### 1.3 依赖关系

```
CorgiPose (枚举)
    ↓
AnimationResourceManager (姿态→动画映射)
    ↓
PoseAnimation (AnimatedContent 平滑过渡)
    ↓
PoseManager (场景/时间驱动姿态选择)
    ↓
HomeViewModel (管理 currentPose 状态)
    ↓
HomeScreen / InteractiveCorgi (UI 层使用)
```

---

## 二、需求分析与用户意图澄清

### 2.1 用户的核心需求

用户需要一个**场景驱动的姿态管理系统**，让柯基能够根据不同的用户操作自动切换姿态，增强陪伴感。

### 2.2 需求场景映射

| 场景 | 姿态 | 触发时机 |
|------|------|---------|
| 默认状态 | LIE (趴卧) | App 启动、闲置状态 |
| 创建待办 | SIT (坐立) | 进入编辑页面、输入内容时 |
| 完成任务 | STAND (站立) | 勾选待办完成时 |
| 加载中 | RUN (奔跑) | 数据加载、保存操作时 |

### 2.3 需要确认的问题（已根据上下文推断）

- **Q: 默认姿态为什么要从"坐立"改为"趴卧"？**
  - A: 趴卧是更放松的状态，适合闲置时的默认展示
- **Q: "创建待办时"具体指什么时机？**
  - A: 进入 TodoEditScreen 时切换为坐立，返回首页后恢复默认
- **Q: "加载中"具体指哪些场景？**
  - A: 数据库操作、网络请求、页面初始化等异步操作期间

---

## 三、实现方案

### 3.1 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    PoseManager (增强)                            │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  场景驱动方法                                              │    │
│  │  - getDefaultPose() : LIE                               │    │
│  │  - getPoseForScene(CREATING) : SIT                      │    │
│  │  - getPoseForScene(LOADING) : RUN                       │    │
│  │  - getPoseForScene(CELEBRATING) : STAND                 │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    HomeViewModel (增强)                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  新增方法                                                 │    │
│  │  - setPoseForCreating() → SIT                           │    │
│  │  - setPoseForLoading() → RUN                            │    │
│  │  - resetPoseToDefault() → LIE                           │    │
│  │  - restorePoseWithDelay() → 延迟恢复默认                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  已有方法（增强）                                          │    │
│  │  - toggleTodoStatus() → STAND (已有) → 2秒后恢复         │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              UI 层（HomeScreen / TodoEditScreen）                │
│  - HomeScreen: 页面初始化时调用 resetPoseToDefault()            │
│  - TodoEditScreen: 页面启动时 setPoseForCreating()              │
│  - TodoEditScreen: 保存时 setPoseForLoading()                   │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 文件修改清单

| 文件 | 操作类型 | 修改内容 |
|------|---------|---------|
| `animation/PoseManager.kt` | 修改 | 1. 修改 `getDefaultPose()` 返回 LIE<br>2. 新增 `PoseScene` 枚举<br>3. 新增 `getPoseForScene()` 方法 |
| `viewmodel/HomeViewModel.kt` | 修改 | 1. 新增 `setPoseForCreating()`<br>2. 新增 `setPoseForLoading()`<br>3. 新增 `resetPoseToDefault()`<br>4. 新增 `restorePoseWithDelay()` |
| `ui/screens/todo/TodoEditScreen.kt` | 修改 | 1. 页面启动时调用 `setPoseForCreating()`<br>2. 保存按钮点击时调用 `setPoseForLoading()` |
| `animation/PoseAnimation.kt` | 可选优化 | 添加加载姿态的使用示例 |

---

## 四、详细实现步骤

### 步骤 1: 增强 PoseManager

**文件**: `app/src/main/java/com/corgimemo/app/animation/PoseManager.kt`

**修改内容**:

1. **修改默认姿态逻辑**: 将 `getDefaultPose()` 改为始终返回 `LIE`
   - 用户明确要求默认姿态为趴卧
   - 移除时间驱动的默认姿态（如有需要可后续添加为可选功能）

2. **新增场景枚举**:
   ```kotlin
   enum class PoseScene {
       DEFAULT,      // 默认/闲置
       CREATING,     // 创建待办
       LOADING,      // 加载中
       CELEBRATING   // 庆祝/完成任务
   }
   ```

3. **新增场景驱动方法**:
   ```kotlin
   fun getPoseForScene(scene: PoseScene): CorgiPose
   ```

### 步骤 2: 增强 HomeViewModel

**文件**: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`

**新增方法**:

| 方法 | 功能 | 姿态 |
|------|------|------|
| `setPoseForCreating()` | 创建待办时调用 | SIT |
| `setPoseForLoading()` | 加载操作时调用 | RUN |
| `resetPoseToDefault()` | 立即恢复默认 | LIE |
| `restorePoseWithDelay(delayMs)` | 延迟恢复默认 | LIE |

**逻辑说明**:
- `setPoseForLoading()` 后，操作完成时调用 `restorePoseWithDelay(500)`
- `toggleTodoStatus()` 中已有的姿态切换逻辑保持不变

### 步骤 3: 集成到 TodoEditScreen

**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt`

**修改点**:

1. **页面启动时**:
   - 使用 `LaunchedEffect(Unit)` 调用 `viewModel.setPoseForCreating()`

2. **保存按钮点击时**:
   - 调用 `viewModel.setPoseForLoading()` 显示加载姿态
   - 保存完成后页面返回，首页会恢复默认姿态

### 步骤 4: 验证和测试

1. **编译测试**: `gradle compileDebugKotlin`
2. **功能验证**:
   - 首页打开 → 柯基趴卧 ✅
   - 点击 FAB 进入编辑页 → 柯基坐立 ✅
   - 点击保存 → 柯基奔跑（短暂）✅
   - 返回首页 → 柯基恢复趴卧 ✅
   - 勾选待办完成 → 柯基站立（2秒后恢复趴卧）✅

---

## 五、潜在风险与应对

### 5.1 风险清单

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| TodoEditScreen 需要访问 HomeViewModel | 导航架构限制 | 通过 Hilt 获取 HomeViewModel 实例，或使用 SharedViewModel |
| 页面返回时姿态未及时恢复 | 状态不同步 | 在 HomeScreen 的 `onResume` 或 LaunchedEffect 中重置姿态 |
| 快速连续操作导致姿态冲突 | 动画不流畅 | 在 ViewModel 中添加姿态切换的防抖逻辑 |
| 用户偏好时间驱动姿态 | 不符合原始设计 | 将时间驱动改为可配置选项 |

### 5.2 技术依赖

| 依赖项 | 用途 | 状态 |
|--------|------|------|
| `MutableStateFlow` | 管理姿态状态 | ✅ 已使用 |
| `AnimatedContent` | 平滑过渡 | ✅ 已使用 |
| `LaunchedEffect` | 页面生命周期回调 | ✅ 已可用 |
| `delay` | 延迟恢复姿态 | ✅ 已可用 |

---

## 六、开发计划

| 阶段 | 任务 | 预估复杂度 |
|------|------|-----------|
| 1 | 增强 PoseManager（场景枚举+默认姿态修改） | 低 |
| 2 | 增强 HomeViewModel（新增姿态控制方法） | 低 |
| 3 | 集成到 TodoEditScreen（页面生命周期回调） | 中 |
| 4 | 编译验证和功能测试 | 低 |

---

## 七、验收标准

- [ ] `getDefaultPose()` 返回 `CorgiPose.LIE`
- [ ] `setPoseForCreating()` 将姿态切换为 `SIT`
- [ ] `setPoseForLoading()` 将姿态切换为 `RUN`
- [ ] `resetPoseToDefault()` 将姿态恢复为 `LIE`
- [ ] 进入 TodoEditScreen 时柯基变为坐立
- [ ] 点击保存按钮时柯基变为奔跑
- [ ] 返回首页后柯基恢复趴卧
- [ ] 编译无错误
