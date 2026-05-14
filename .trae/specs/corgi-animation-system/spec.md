# 柯基动画系统 - Product Requirement Document

## Overview

- **Summary**: 为柯基陪伴系统实现完整的动画系统，包括帧动画播放、姿态切换、触摸互动反馈和情绪系统，提升用户与柯基的互动体验。
- **Purpose**: 将静态的柯基陪伴升级为动态、有情感反馈的互动体验，增强用户粘性和使用乐趣。
- **Target Users**: 所有使用柯基备忘录 APP 的用户，尤其是喜欢通过互动获得情感反馈的用户。

## Goals

- 实现帧动画播放组件，支持多组动画、帧率控制、循环/单次播放、结束回调
- 实现姿态切换系统，支持趴卧、坐立、站立、奔跑四种姿态的自动切换和平滑过渡
- 实现丰富的触摸互动反馈，包括单击、双击、长按、快速连点的不同动画反馈
- 实现情绪系统，基于任务完成情况计算情绪值，对应不同的表情和问候语
- 整合到首页，在完成任务时播放庆祝动画

## Non-Goals (Out of Scope)

- 不实现 3D 建模或骨骼动画
- 不实现语音合成或语音识别互动
- 不实现多人社交功能
- 不实现网络同步功能（本地存储即可）

## Background & Context

### 现有资源

项目中已准备以下动画资源（位于 `app/src/main/res/drawable/`）：

| 动画类型     | 帧数 | 文件命名                                                        |
| :----------- | :--: | :-------------------------------------------------------------- |
| 坐立 (sit)   |  2  | `corgi_sit_2frames_01.png`, `corgi_sit_2frames_02.png`      |
| 站立 (stand) |  2  | `corgi_stand_2frames_01.png`, `corgi_stand_2frames_02.png`  |
| 眨眼 (wink)  |  2  | `corgi_wink_2frames_01.png`, `corgi_wink_2frames_02.png`    |
| 摇尾巴 (wag) |  4  | `corgi_wag_4frames_01.png` ~ `corgi_wag_4frames_04.png`     |
| 歪头 (tilt)  |  2  | `corgi_tilt_2frames_01.png`, `corgi_tilt_2frames_02.png`    |
| 睡觉 (sleep) |  2  | `corgi_sleep_2frames_01.png`, `corgi_sleep_2frames_02.png`  |
| 难过 (sad)   |  2  | `corgi_sad_2frames_01.png`, `corgi_sad_2frames_02.png`      |
| 骄傲 (proud) |  2  | `corgi_proud_2frames_01.png`, `corgi_proud_2frames_02.png`  |
| 害羞 (shy)   |  2  | `corgi_shy_2frames_01.png`, `corgi_shy_2frames_02.png`      |
| 担心 (worry) |  2  | `corgi_worry_2frames_01.png`, `corgi_worry_2frames_02.png`  |
| 打滚 (roll)  |  4  | `corgi_roll_4framesl_01.png` ~ `corgi_roll_4framesl_04.png` |
| 奔跑 (run)   |  4  | `corgi_run_4frames_01.png` ~ `corgi_run_4frames_04.png`     |
| 趴卧 (lie)   |  3  | `corgi_lie_3frames_01.png` ~ `corgi_lie_3frames_03.png`     |

### 现有代码结构

- `CorgiCompanion.kt`: 柯基基础展示组件
- `CorgiData.kt`: 柯基数据模型（包含 `moodValue` 字段）
- `CorgiRepository.kt`: 柯基数据仓储
- `HomeViewModel.kt`: 首页视图模型

## Functional Requirements

### FR-1: 帧动画播放组件

- 支持定义多组动画资源列表
- 支持控制播放速度（FPS 帧率）
- 支持循环播放和单次播放两种模式
- 动画播放完成后触发回调
- 支持暂停、继续、停止动画

### FR-2: 姿态切换系统

- 定义 4 种姿态枚举：LIE(趴卧)、SIT(坐立)、STAND(站立)、RUN(奔跑)
- 不同时间场景自动切换姿态（如：深夜显示睡觉姿态）
- 姿态切换时有平滑过渡效果
- 可以手动触发姿态切换（用于动画反馈）

### FR-3: 触摸互动反馈

- **单击**：播放"眨眼"动画 + "摇尾巴"动画（循环播放）
- **双击**：播放"打滚"动画（单次播放，结束后恢复原姿态）
- **长按**：显示爱心粒子特效（从柯基位置向上飘散）
- **快速连点 (3秒内5次点击)**：柯基"害羞"动画 + 躲到屏幕边缘

### FR-4: 情绪系统

- 7 种情绪状态：开心、普通、期待、担心、困倦、兴奋、失落
- 情绪值计算规则：
  - 完成待办 +10
  - 连续完成 3 天以上 +5 额外奖励
  - 长时间未操作 -5/天
- 不同情绪对应不同的基础动画和问候语
- 情绪值持久化存储

### FR-5: 整合到首页

- 首页顶部显示柯基互动区域
- 完成待办任务时播放"骄傲"庆祝动画
- 根据时间自动切换空闲姿态

## Non-Functional Requirements

### NFR-1: 性能

- 动画播放时帧率稳定在 30 FPS 以上
- 内存占用控制在合理范围（动画资源按需加载）
- 不影响待办列表的滚动流畅度

### NFR-2: 可扩展性

- 动画组件设计为可复用
- 情绪状态可以轻松扩展
- 姿态和动画映射配置集中管理

### NFR-3: 兼容性

- 支持 Android API 26 及以上
- 适配不同屏幕尺寸和密度
- 支持深色模式

## Constraints

- **技术**: 使用 Jetpack Compose 实现动画，不引入第三方动画库
- **业务**: 必须遵循现有 MVVM 架构
- **资源**: 仅使用项目中已有的图片资源

## Assumptions

- 所有动画资源的命名格式一致（`corgi_{type}_{n}frames_{index}.png`）
- 动画帧率默认 15 FPS 可满足需求
- 情绪值范围 0-100

## Acceptance Criteria

### AC-1: 帧动画播放

- **Given**: 用户打开 APP，首页显示柯基
- **When**: 柯基组件初始化
- **Then**: 自动播放对应姿态的循环动画，帧率稳定
- **Verification**: `programmatic`
- **Notes**: 验证动画循环播放，帧率可配置

### AC-2: 姿态自动切换

- **Given**: 时间是深夜（22:00-6:00）
- **When**: 柯基组件初始化
- **Then**: 显示睡觉姿态动画
- **Verification**: `programmatic`
- **Notes**: 测试不同时间段的姿态切换逻辑

### AC-3: 单击互动

- **Given**: 柯基处于坐立姿态
- **When**: 用户单击柯基
- **Then**: 播放眨眼动画 + 摇尾巴动画
- **Verification**: `human-judgment`
- **Notes**: 验证视觉反馈是否明显

### AC-4: 双击互动

- **Given**: 柯基处于坐立姿态
- **When**: 用户快速双击柯基
- **Then**: 播放打滚动画（单次），结束后恢复坐立
- **Verification**: `human-judgment`
- **Notes**: 验证双击检测是否准确

### AC-5: 长按互动

- **Given**: 柯基处于坐立姿态
- **When**: 用户长按柯基超过 500ms
- **Then**: 显示爱心粒子特效向上飘散
- **Verification**: `human-judgment`
- **Notes**: 验证长按检测和粒子效果

### AC-6: 快速连点互动

- **Given**: 柯基处于坐立姿态
- **When**: 用户在 3 秒内连续点击 5 次
- **Then**: 播放害羞动画 + 柯基移动到屏幕边缘
- **Verification**: `human-judgment`
- **Notes**: 验证连点检测和移动效果

### AC-7: 情绪值计算

- **Given**: 用户有 3 条待办，情绪值为 50
- **When**: 用户完成 2 条待办
- **Then**: 情绪值增加到 70，显示"开心"情绪
- **Verification**: `programmatic`
- **Notes**: 验证情绪值计算逻辑

### AC-8: 完成任务庆祝

- **Given**: 用户有未完成的待办
- **When**: 用户点击完成待办
- **Then**: 柯基播放骄傲动画庆祝
- **Verification**: `human-judgment`
- **Notes**: 验证任务完成时的动画触发

### AC-9: 情绪持久化

- **Given**: 用户情绪值为 80（开心）
- **When**: 用户关闭 APP 并重新打开
- **Then**: 情绪值保持 80，显示开心姿态
- **Verification**: `programmatic`
- **Notes**: 验证情绪值持久化存储

## Open Questions

- [X] 是否需要实现动画资源的延迟加载（优化内存）？
- [X] 情绪值的具体阈值范围需要确认（如：多少分对应"开心"）？
- [X] 快速连点的判定参数（3秒5次）是否合适？
- [X] 是否需要添加动画音效？
