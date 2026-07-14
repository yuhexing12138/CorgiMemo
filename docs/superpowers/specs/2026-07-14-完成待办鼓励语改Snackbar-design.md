# 完成待办鼓励语改 Snackbar 设计

## 背景

待办页（HomeScreen）在用户完成待办时，会通过 `CelebrationOverlay` 全屏居中弹出一句带 emoji 的鼓励语（如 "😊 太棒了！又完成一个！"），同时叠加 `GlowOverlay` 边缘光晕效果。

**当前问题**：
- 全屏覆盖半透明黑色遮罩（`Color.Black.copy(alpha = 0.25f~0.6f)`），阻断用户对列表的视觉浏览
- 居中文字 + 边缘光晕强制聚焦注意力，反馈过强
- 与现代 App 习惯（Snackbar 轻提示）不一致
- 批量完成时也只显示同样的全屏提示，体验单一

**用户诉求**（图 1 → 图 2）：
- 图 1：当前全屏居中弹窗 "😊 太棒了！又完成一个！"
- 图 2：屏幕底部 Snackbar 提示（参考样式："🐶 柯基互动开发中，敬请期待~"）

## 设计目标

1. **去除全屏覆盖**：删除 `CelebrationOverlay` 及其黑色半透明遮罩
2. **改为 Snackbar 提示**：分级鼓励语通过 `SnackbarHostState` 在屏幕底部弹出
3. **保留边缘光晕**：保留 `GlowOverlay`（MEDIUM/HIGH/SUPER 级别仍然触发）
4. **单条 + 批量统一处理**：批量完成也走 Snackbar 通道，体验一致
5. **保留撤销能力**：单条完成的"撤销"按钮仍然可用

## 涉及文件

| 文件 | 改动类型 |
|------|----------|
| `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` | **不改动**：`CelebrationState` 数据结构和赋值逻辑保持原状 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 删除 `CelebrationOverlay` 渲染、新增 `celebrationState` 监听 Snackbar、保留 `GlowOverlay` |

## 数据结构改动

### `CelebrationState`（HomeViewModel.kt）

**改造前**：
```kotlin
data class CelebrationState(
    val isShowing: Boolean = false,
    val level: CelebrationLevel = CelebrationLevel.LOW,
    val message: String = "太棒了！"
)
```

**改造后**（**保持不变**，仅调整使用语义）：
```kotlin
data class CelebrationState(
    val isShowing: Boolean = false,
    val level: CelebrationLevel = CelebrationLevel.LOW,
    val message: String = "太棒了！"
)
```

**关键说明**：
- 数据结构保持不变，最小化改动
- 字段语义重定义：
  - `isShowing` 不再表示"全屏覆盖是否显示"，改为"光晕+Snackbar 是否处于活跃态"
  - `level` 仍控制 GlowOverlay 强度（LOW 内部已有防护不渲染）
  - `message` 改为驱动 Snackbar 文案

### 鼓励语映射（保持不变）

```kotlin
CelebrationLevel.LOW    -> "太棒了！又完成一个！"
CelebrationLevel.MEDIUM -> "完成得不错哦！"
CelebrationLevel.HIGH   -> "这么重要的任务都完成了！太厉害了！"
CelebrationLevel.SUPER  -> "抢在截止前完成了！柯基为你骄傲！"
```

### Emoji 映射

| 级别 | Emoji | 含义 |
|------|-------|------|
| LOW | 😊 | 鼓励 |
| MEDIUM | ⭐ | 小认可 |
| HIGH | 🎉 | 大认可 |
| SUPER | 🏆 | 抢在截止前完成 |

## ViewModel 改动（HomeViewModel.kt）

### 1. `handleTaskCompleted` 中的 `celebrationState` 赋值

**改造前**（line ~1861）：
```kotlin
_celebrationState.value = CelebrationState(
    isShowing = true,
    level = level,
    message = message
)
_currentPose.value = CorgiPose.STAND

delay(duration)
_celebrationState.value = CelebrationState(isShowing = false)
```

**改造后**（**保持不变**）：
```kotlin
_celebrationState.value = CelebrationState(
    isShowing = true,
    level = level,
    message = message
)
_currentPose.value = CorgiPose.STAND

// 保留 delay 用于柯基姿态恢复（与之前一致）
delay(duration)
if (_currentBehavior.value != BehaviorType.HAPPY_STREAK) {
    _currentPose.value = PoseManager.getDefaultPose()
}
```

**关键说明**：
- ViewModel 代码保持完全不变
- `delay(duration)` 仍用于柯基姿态恢复
- `isShowing = false` 重置保留（驱动 HomeScreen 中 AnimatedVisibility 的 exit 动画和 LaunchedEffect 重新启动）
- **本设计零改动 ViewModel**——所有调整都在 HomeScreen 渲染层

### 2. `triggerBatchCelebration`（line ~2804）

**保持不变**：
```kotlin
_celebrationState.value = CelebrationState(
    isShowing = true,
    level = level,
    message = message
)
```

**注意**：批量完成当前实现中没有 `delay`，因此 `isShowing` 不会自动重置为 false。本设计在 HomeScreen 层通过 LaunchedEffect 一次性消费即可，不依赖 `isShowing` 自动重置。

### 3. 不再使用的方法

无。`getCelebrationDuration` 等保持现有实现。

## HomeScreen 改动

### 1. 删除 `CelebrationOverlay` 渲染（line ~1027-1040）

**删除**：
```kotlin
// 对齐组件容器（包裹需要使用 align 的组件）
Box(modifier = Modifier.fillMaxSize()) {
    // 庆祝覆盖层
    AnimatedVisibility(
        visible = celebrationState.isShowing,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = Modifier.align(Alignment.Center)
    ) {
        CelebrationOverlay(
            level = celebrationState.level,
            message = celebrationState.message
        )
    }
    ...
}
```

**替换为**（直接删除 CelebrationOverlay 的 AnimatedVisibility 块）：
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // CelebrationOverlay 已移除：分级鼓励语改由 Snackbar 显示
    // （见下方 LaunchedEffect(celebrationState)）
    ...
}
```

### 2. 保留 `GlowOverlay` 渲染（line ~1058-1065）

**保持不变**（逻辑已正确）：
```kotlin
AnimatedVisibility(
    visible = celebrationState.isShowing,
    enter = fadeIn(),
    exit = fadeOut()
) {
    GlowOverlay(level = celebrationState.level)
}
```

**说明**：
- `GlowOverlay` 内部已有 `if (level == CelebrationLevel.LOW) return` 防护（line ~2360），LOW 级别不渲染
- MEDIUM/HIGH/SUPER 级别按 `getCelebrationDuration` 时长（2-4s）显示
- ViewModel 的 `delay(duration)` 完成后 `isShowing = false`，触发 `exit = fadeOut()`

### 3. 新增 celebrationState 监听 → 显示 Snackbar

**新增**（在 `LaunchedEffect(pendingCompleteTodo)` 附近）：
```kotlin
/**
 * 监听 CelebrationState 变化，通过 Snackbar 显示分级鼓励语
 *
 * 触发时机：
 * - 单条完成：handleTaskCompleted 设置 _celebrationState.isShowing = true
 * - 批量完成：triggerBatchCelebration 设置 _celebrationState.isShowing = true
 *
 * 显示策略：
 * - 等待升级弹窗（showLevelUp）/ 成就弹窗（showAchievementUnlock）关闭后再显示
 * - 避免与全屏 Dialog 抢占屏幕
 * - isShowing: false → true 时启动一次 Snackbar；不持续监听
 */
LaunchedEffect(
    celebrationState.isShowing,
    celebrationState.message,
    showLevelUp,
    showAchievementUnlock
) {
    // 只在 isShowing 由 false 变 true 时触发
    if (!celebrationState.isShowing) {
        return@LaunchedEffect
    }
    // 等待弹窗关闭（与 pendingBatchCompleteCount 策略一致）
    if (showLevelUp != null || showAchievementUnlock != null) {
        return@LaunchedEffect
    }

    val emoji = when (celebrationState.level) {
        CelebrationLevel.LOW -> "😊"
        CelebrationLevel.MEDIUM -> "⭐"
        CelebrationLevel.HIGH -> "🎉"
        CelebrationLevel.SUPER -> "🏆"
    }
    val message = "$emoji ${celebrationState.message}"

    snackbarHostState.showSnackbar(
        message = message,
        duration = SnackbarDuration.Short
    )
}
```

**关键设计点**：
- **key 选择**：`celebrationState.isShowing` + `celebrationState.message` + `showLevelUp` + `showAchievementUnlock`
  - `isShowing` 变化：从 true→false 时，LaunchedEffect 重新启动但不执行（return）
  - 从 false→true 时：触发 Snackbar
  - `message` 变化：保证不同 Snackbar 文案都能触发
  - 弹窗状态变化：弹窗关闭后重新检查
- **Snackbar 持续时间**：使用 `SnackbarDuration.Short`（约 4 秒），与项目其他 Snackbar 一致
- **不消费 isShowing**：依赖 ViewModel 的 `delay(duration)` 后自动重置，避免双向状态同步

### 4. 与现有 `pendingCompleteTodo` Snackbar 的关系

**现有逻辑**（line ~377-388）：
```kotlin
LaunchedEffect(pendingCompleteTodo) {
    pendingCompleteTodo?.let { (todo, _) ->
        val result = snackbarHostState.showSnackbar(
            message = "✅ '${todo.title}' 已完成",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoComplete()
        }
    }
}
```

**冲突分析**：
- 单条完成会同时触发：
  1. `pendingCompleteTodo` Snackbar（"✅ 'XXX' 已完成" + 撤销）
  2. `celebrationState` Snackbar（"😊 太棒了！又完成一个！"）
- Snackbar 队列式显示（一次只显示一个），第二个会等第一个消失才显示
- 撤销按钮只对"已完成" Snackbar 有效

**用户感知**：
- 第一个 Snackbar 显示约 4s（"✅ 'XXX' 已完成" 带撤销）
- 第一个消失后，第二个 Snackbar 立即显示约 4s（"😊 太棒了！又完成一个！"）
- 总反馈时长 8s，比原 CelebrationOverlay（1-4s）更长，但都是非阻塞的

**保留这种行为**：撤销体验优先，鼓励语作为补充。如果用户希望合并，单独需求处理。

### 5. 与批量完成 `pendingBatchCompleteCount` 的关系

**现有逻辑**（line ~411-430）：
```kotlin
LaunchedEffect(
    pendingBatchCompleteCount,
    showLevelUp,
    showAchievementUnlock
) {
    val count = pendingBatchCompleteCount ?: return@LaunchedEffect
    if (showLevelUp != null || showAchievementUnlock != null) {
        return@LaunchedEffect
    }
    snackbarHostState.showSnackbar(
        message = "✅ 已完成 $count 项",
        duration = SnackbarDuration.Short
    )
    viewModel.clearPendingBatchComplete()
}
```

**统一处理后的队列**：
1. "✅ 已完成 N 项"（批量完成提示）
2. "😊 太棒了！又完成一个！" 或更高级别鼓励语（基于最高优先级 todo）

这与单条完成体验一致，遵循"先具体结果、再情绪反馈"的节奏。

## 边界情况

### 1. 升级弹窗/成就弹窗拦截

升级弹窗（`LevelUpDialog`）和成就弹窗（`AchievementUnlockDialog`）是全屏 Dialog，会遮挡 SnackbarHost。LaunchedEffect 中已通过 `showLevelUp`、`showAchievementUnlock` 等待弹窗关闭（与 `pendingBatchCompleteCount` 策略一致）。

### 2. LOW 级别不显示光晕

`GlowOverlay` 内部已有 `if (level == CelebrationLevel.LOW) return` 防护（line ~2360），所以 LOW 级别即使触发 GlowOverlay 也不会渲染。

### 3. 重复触发

短时间内连续完成多个待办：
- 单条完成：每次 `_celebrationState.isShowing` 从 false→true，LaunchedEffect 触发新 Snackbar
- 批量完成：`_pendingBatchCompleteCount` 只设置一次（最后完成时），`triggerBatchCelebration` 只在批量完成最终步骤调用一次
- `isShowing` 字段已重置，LaunchedEffect 准备好接收下一次触发

### 4. Snackbar 与 FAB 视觉冲突

项目已有处理：`snackbarHostState` 由 MainScreen 顶层 Scaffold 渲染（`snackbarHost` 槽位），自动避让 FAB 和底部导航栏（详见 HomeScreen.kt line 1042-1047 注释）。

### 5. 批量完成时 `isShowing` 未自动重置

`triggerBatchCelebration` 当前实现没有 `delay(duration)` 后的 `isShowing = false` 重置。这不会影响本设计：
- 批量完成只调用一次 `triggerBatchCelebration`
- LaunchedEffect 在 `isShowing = true` 时显示 Snackbar，不依赖 `isShowing` 重置
- GlowOverlay 在批量完成时也不会自动隐藏（因为没有 `delay` + `isShowing = false`）
- **潜在改进**（见"后续可优化" #2）：在 `triggerBatchCelebration` 末尾添加 `delay + isShowing = false`，与单条完成保持一致

## 验证清单

实施完成后，需通过以下检查：

- [ ] 单条完成显示两个顺序 Snackbar：先"✅ 'XXX' 已完成"（带撤销），再分级鼓励语
- [ ] 批量完成显示两个顺序 Snackbar：先"✅ 已完成 N 项"，再分级鼓励语
- [ ] MEDIUM/HIGH/SUPER 级别边缘光晕保留 2-4s
- [ ] LOW 级别无光晕
- [ ] 升级弹窗/成就弹窗期间不显示鼓励语 Snackbar，关闭后正常显示
- [ ] 撤销按钮仍然有效
- [ ] 柯基姿态变化（`_currentPose`）与之前一致
- [ ] 编译通过，无 Lint 警告
- [ ] 无 `isShowing` 误删（应保留，ViewModel 仍依赖其状态机）

## 不在本次改动范围

- 撤销 Snack 与鼓励语 Snack 的合并/排队优化（潜在 follow-up）
- Snackbar 文案的多语言支持（i18n）
- 鼓励语的"高级别特殊动效"（如 SUPER 级别 Lottie 动画）
- 柯基姿态的进一步细分（CelebrationLevel → BehaviorType 映射）
- ViewModel 中 `getCelebrationDuration` 的调整

## 后续可优化

1. **Snackbar 合并显示**：将"已完成"和鼓励语合并为单条（如 "✅ XXX 已完成 · 😊 太棒了！又完成一个！"），减少 8s 反馈时长
2. **批量完成时 `isShowing` 自动重置**：在 `triggerBatchCelebration` 末尾添加 `delay + isShowing = false`，与单条完成保持一致
3. **批量完成特殊体验**：当 N >= 5 时显示更醒目的提示
4. **可访问性**：为 Snackbar 添加 `TalkBack` 朗读优先级（重要程度）
5. **配置化**：将 `getEncouragementMessage` 提取到 `R.string`，支持多语言
