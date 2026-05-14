# 柯基动画系统 - 实现计划 (Decomposed and Prioritized Task List)

## 任务依赖关系图

```
[ ] Task 1: 动画资源配置和枚举定义
        ↓
[ ] Task 2: 帧动画播放组件
        ↓
[ ] Task 3: 姿态切换系统
        ↓
[ ] Task 4: 情绪系统
        ↓
[ ] Task 5: 触摸互动反馈
        ↓
[ ] Task 6: 整合到首页和待办交互
```

## [ ] Task 1: 动画资源配置和枚举定义

- **Priority**: P0
- **Depends On**: None
- **Description**:
  - 定义动画类型枚举（AnimationType）
  - 定义姿态枚举（CorgiPose）
  - 定义情绪枚举（CorgiMood）
  - 创建动画资源映射配置，将枚举映射到实际资源 ID 列表
  - 建立动画帧资源管理工具类

- **Acceptance Criteria Addressed**: FR-1, FR-2, FR-4
- **Test Requirements**:
  - `programmatic` TR-1.1: AnimationType 枚举包含所有现有动画类型（sit, stand, lie, run, wink, wag, tilt, sleep, sad, proud, shy, worry, roll）
  - `programmatic` TR-1.2: CorgiPose 枚举包含 LIE, SIT, STAND, RUN 四种姿态
  - `programmatic` TR-1.3: CorgiMood 枚举包含开心、普通、期待、担心、困倦、兴奋、失落 7 种情绪
  - `human-judgement` TR-1.4: 动画资源映射正确，每帧图片都能正确加载
- **Notes**: 建议使用 Kotlin 的 sealed class 或 enum class 结合 when 表达式实现资源映射

## [ ] Task 2: 帧动画播放组件

- **Priority**: P0
- **Depends On**: Task 1
- **Description**:
  - 创建 FrameAnimation Composable 组件
  - 支持传入动画资源列表、FPS 帧率、循环/单次模式
  - 使用 LaunchedEffect 和 delay 实现帧切换
  - 支持动画开始、暂停、继续、停止控制
  - 动画结束回调支持
  - 添加动画状态管理

- **Acceptance Criteria Addressed**: FR-1, AC-1
- **Test Requirements**:
  - `programmatic` TR-2.1: 循环播放模式下，动画无限循环播放
  - `programmatic` TR-2.2: 单次播放模式下，播放完所有帧后停止
  - `programmatic` TR-2.3: FPS 参数生效，15 FPS 时每帧约 66ms
  - `programmatic` TR-2.4: 动画结束后正确触发 onFinish 回调
  - `human-judgement` TR-2.5: 动画播放流畅，无卡顿
- **Notes**: 使用 `remember` + `mutableStateOf` 管理当前帧索引，LaunchedEffect 控制帧切换时机

## [ ] Task 3: 姿态切换系统

- **Priority**: P0
- **Depends On**: Task 2
- **Description**:
  - 创建姿态管理器类
  - 实现基于时间的姿态自动切换逻辑
  - 实现姿态切换时的平滑过渡（使用 Compose 的 AnimatedContent 或 Crossfade）
  - 提供手动切换姿态的 API
  - 定义默认姿态映射（白天默认坐立，深夜默认睡觉等）

- **Acceptance Criteria Addressed**: FR-2, AC-2
- **Test Requirements**:
  - `programmatic` TR-3.1: 22:00-6:00 自动切换到 SLEEP 姿态
  - `programmatic` TR-3.2: 其他时间段默认使用 SIT 姿态
  - `programmatic` TR-3.3: 手动切换姿态 API 能正确触发切换
  - `human-judgement` TR-3.4: 姿态切换时有平滑过渡效果
- **Notes**: 可以使用 `AnimatedVisibility` 或 `Crossfade` 实现姿态间的淡入淡出过渡

## [ ] Task 4: 情绪系统

- **Priority**: P1
- **Depends On**: Task 3
- **Description**:
  - 实现情绪值计算逻辑（完成任务 +10，连续 3 天 +5，长时间未操作 -5/天）
  - 创建情绪到动画/问候语的映射
  - 扩展 CorgiData 的情绪值持久化（已存在 moodValue 字段）
  - 在 HomeViewModel 中添加情绪状态管理
  - 创建情绪对应的问候语生成逻辑

- **Acceptance Criteria Addressed**: FR-4, AC-7, AC-9
- **Test Requirements**:
  - `programmatic` TR-4.1: 完成 1 个待办任务，情绪值增加 10
  - `programmatic` TR-4.2: 连续 3 天完成任务，额外增加 5 情绪值
  - `programmatic` TR-4.3: 情绪值范围 0-100，超过时自动截断
  - `programmatic` TR-4.4: 情绪值持久化，重启 APP 后保持
  - `human-judgement` TR-4.5: 不同情绪显示不同问候语
- **Notes**: 情绪阈值建议：<20 失落，20-40 担心，40-60 普通，60-80 开心，>80 兴奋

## [ ] Task 5: 触摸互动反馈

- **Priority**: P1
- **Depends On**: Task 2, Task 3, Task 4
- **Description**:
  - 扩展 CorgiCompanion 组件的触摸检测
  - 单击：播放 wink + wag 动画组合
  - 双击：播放 roll 动画（单次），结束后恢复原姿态
  - 长按：显示爱心粒子特效（使用 Canvas 或 AnimatedVisibility 实现）
  - 快速连点：检测 3 秒内 5 次点击，播放 shy 动画 + 移动到屏幕边缘
  - 使用防抖机制避免误触

- **Acceptance Criteria Addressed**: FR-3, AC-3, AC-4, AC-5, AC-6
- **Test Requirements**:
  - `programmatic` TR-5.1: 单击正确触发眨眼 + 摇尾巴动画
  - `programmatic` TR-5.2: 双击检测正确（<300ms 两次点击）
  - `programmatic` TR-5.3: 长按检测正确（>500ms 不松手）
  - `programmatic` TR-5.4: 快速连点检测正确（3秒内5次）
  - `human-judgement` TR-5.5: 爱心粒子效果美观自然
  - `human-judgement` TR-5.6: 害羞时移动到屏幕边缘的动画流畅
- **Notes**: 使用 `combinedClickable` 支持双击、长按，使用自定义计数器实现快速连点检测

## [ ] Task 6: 整合到首页和待办交互

- **Priority**: P1
- **Depends On**: Task 4, Task 5
- **Description**:
  - 重构 CorgiCompanion 组件，整合动画、姿态、情绪系统
  - 在 HomeViewModel 中添加动画状态管理
  - 完成待办任务时触发 proud 动画庆祝
  - 添加任务完成时的柯基反馈（情绪值增加 + 动画）
  - 确保与现有的待办列表交互不冲突

- **Acceptance Criteria Addressed**: FR-5, AC-8
- **Test Requirements**:
  - `programmatic` TR-6.1: 首页正确显示柯基动画组件
  - `programmatic` TR-6.2: 完成待办任务时播放骄傲动画
  - `programmatic` TR-6.3: 完成任务后情绪值正确增加
  - `human-judgement` TR-6.4: 整体交互流畅，不影响待办列表操作
- **Notes**: 使用 StateFlow 管理动画状态，确保 ViewModel 和 UI 的状态同步

## 实施阶段

### 第一阶段：基础能力（Task 1 + Task 2）
- 建立动画资源管理
- 实现核心帧动画播放

### 第二阶段：姿态和情绪（Task 3 + Task 4）
- 实现姿态自动切换
- 建立情绪系统

### 第三阶段：互动体验（Task 5 + Task 6）
- 实现丰富的触摸互动
- 整合到首页和待办流程
