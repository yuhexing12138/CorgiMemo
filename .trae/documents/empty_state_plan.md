# 空状态引导功能实现计划

## 1. 仓库调研结论

### 现有代码结构

| 文件                                                                                                                                         | 说明                                                  |
| -------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| [EmptyState.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/EmptyState.kt)             | 现有空状态组件（纯图标+文字，无柯基动画）             |
| [HomeScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L245-L246) | 首页调用 `EmptyState()` 当 `todos.isEmpty()`      |
| [InteractiveCorgi.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/animation/InteractiveCorgi.kt)     | 互动柯基组件（支持多种动画：TILT 歪头、WAG 摇尾巴等） |
| [FrameAnimation.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/FrameAnimation.kt)     | 帧动画组件，支持 `AnimationType` 枚举               |
| [AnimationType.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/AnimationType.kt)           | 动画类型枚举（TILT、WAG、SIT、LIE 等）                |
| [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L799-L801) | `FilterStatus` 枚举：ALL、PENDING、COMPLETED        |

### 现有功能

| 功能            | 状态    | 说明                                                                                                                             |
| --------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 基础空状态组件  | ✅ 已有 | [EmptyState.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/EmptyState.kt) |
| 首页空状态调用  | ✅ 已有 | `todos.isEmpty()` 时显示空状态                                                                                                 |
| 待办/已完成过滤 | ✅ 已有 | `FilterStatus.PENDING/COMPLETED`                                                                                               |
| 柯基动画系统    | ✅ 已有 | `AnimationType.TILT` 歪头、`AnimationType.WAG` 摇尾巴等                                                                      |
| 分类过滤        | ❌ 缺失 | 首页当前只有全部/待办/已完成过滤，无分类过滤                                                                                     |

### 关于 AnimatedVectorDrawable 的说明

项目当前使用的是 **FrameAnimation（帧动画）** 系统，而非 AnimatedVectorDrawable：

- 动画资源为 PNG 序列帧（如 `anim/tilt_00.png` ~ `anim/tilt_11.png`）
- `FrameAnimation` 组件已支持循环播放 `isLooping = true`
- 项目中没有 AnimatedVectorDrawable 资源

**建议**：使用现有的 FrameAnimation 系统实现空状态动画，保持技术栈一致。

---

## 2. 需求分析

### 功能需求

1. **空状态类型识别**

   - 根据 `FilterStatus` 判断当前显示的是"待办"还是"已完成"列表
   - 当前首页无分类过滤功能，暂时不实现"分类列表为空"的情况
2. **可复用的 EmptyState 组件**

   - 传入参数：
     - `emptyType: EmptyStateType`（PENDING / COMPLETED / CATEGORY）
     - `onAction: () -> Unit`（引导按钮点击回调）
     - `categoryName: String?`（分类名称，可选）
3. **三种空状态**

| 场景           | 柯基动画              | 文字内容                                                              | 引导按钮                               |
| -------------- | --------------------- | --------------------------------------------------------------------- | -------------------------------------- |
| 待办列表为空   | 歪头 + 摇尾巴（循环） | 标题："还没有待办~"`<br>`描述："添加第一个待办来和柯基互动吧！"     | "添加待办" → 打开添加待办 BottomSheet |
| 已完成列表为空 | 期待 + 摇尾巴（循环） | 标题："还没有已完成的待办~"`<br>`描述："完成任务就能在这里看到啦！" | "去添加" → 切换到"全部"或"待办"标签   |
| 分类列表为空   | 趴卧 + 摇尾巴（循环） | 标题："这个分类还没有待办~"`<br>`描述："在分类下添加待办试试？"     | "添加待办" → 打开添加待办 BottomSheet |

4. **动画循环**
   - 轻微摇尾巴（WAG）+ 歪头（TILT）组合或循环播放
   - 使用 `FrameAnimation(isLooping = true)`

---

## 3. 文件变更列表

| 文件                                                                  | 操作 | 说明                                                      |
| --------------------------------------------------------------------- | ---- | --------------------------------------------------------- |
| `app/src/main/java/com/corgimemo/app/ui/components/EmptyState.kt`   | 重写 | 替换现有实现，添加柯基动画、类型参数、引导按钮            |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 修改 | 传递 `filterStatus` 给 `EmptyState`，处理按钮点击回调 |

---

## 4. 实现步骤

### 步骤 1：定义空状态类型枚举

在 [EmptyState.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/EmptyState.kt) 中添加：

```kotlin
enum class EmptyStateType {
    PENDING,    // 待办列表为空
    COMPLETED,  // 已完成列表为空
    CATEGORY    // 分类列表为空（预留）
}
```

### 步骤 2：重写 EmptyState 组件

重写 [EmptyState.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/EmptyState.kt)：

**新组件签名：**

```kotlin
@Composable
fun EmptyState(
    emptyType: EmptyStateType = EmptyStateType.PENDING,
    categoryName: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

**实现内容：**

1. 柯基动画区域（FrameAnimation 循环播放）
   - PENDING：TILT（歪头）+ WAG（摇尾巴）组合
   - COMPLETED：SIT（坐立）+ 期待姿态
   - CATEGORY：LIE（趴卧）姿态
2. 标题文字（根据类型变化）
3. 描述文字（根据类型变化）
4. 引导按钮（可选，有 `onAction` 时显示）

### 步骤 3：修改 HomeScreen 传递状态和回调

修改 [HomeScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L245-L246)：

**修改前：**

```kotlin
if (todos.isEmpty()) {
    EmptyState()
}
```

**修改后：**

```kotlin
if (todos.isEmpty()) {
    EmptyState(
        emptyType = when (filterStatus) {
            HomeViewModel.FilterStatus.PENDING -> EmptyStateType.PENDING
            HomeViewModel.FilterStatus.COMPLETED -> EmptyStateType.COMPLETED
            else -> EmptyStateType.PENDING
        },
        onAction = {
            when (filterStatus) {
                HomeViewModel.FilterStatus.COMPLETED -> {
                    // 切换到待办标签
                    viewModel.setFilterStatus(HomeViewModel.FilterStatus.PENDING)
                }
                else -> {
                    // 打开添加待办 BottomSheet
                    // 查看现有添加逻辑，复用
                }
            }
        }
    )
}
```

需要查看 HomeScreen 中如何打开 `TodoCreateBottomSheet`，复用该逻辑。

---

## 5. 潜在依赖和考虑事项

### 5.1 动画组合策略

**问题**：`FrameAnimation` 一次只能播放一种动画类型，如何实现"摇尾巴 + 歪头"组合？

**方案对比：**

| 方案                 | 说明                                                       | 优缺点                   |
| -------------------- | ---------------------------------------------------------- | ------------------------ |
| 方案 A：动画交替循环 | 先播放 WAG（2秒）→ 再播放 TILT（2秒）→ 循环              | 实现简单，但不是同时发生 |
| 方案 B：单一动画循环 | 只播放 WAG 摇尾巴循环，文案体现场景                        | 简单，与现有系统兼容     |
| 方案 C：叠加动画     | 两层 FrameAnimation 叠加（底层是基础姿态，上层播放摇尾巴） | 复杂，需要资源支持       |

**建议采用方案 B**：

- PENDING：WAG（摇尾巴）循环 + 歪头表情文案
- COMPLETED：SIT（坐立）循环 + 期待表情文案
- CATEGORY：LIE（趴卧）循环 + 慵懒表情文案

### 5.2 现有添加待办逻辑

需要确认 HomeScreen 中如何打开 TodoCreateBottomSheet：

- 查看是否有 `showBottomSheet` 状态变量
- 查看 FAB 点击如何触发

### 5.3 分类过滤功能缺失

当前首页只有全部/待办/已完成过滤，无分类过滤。

**决策**：

- 本次实现 PENDING 和 COMPLETED 两种空状态
- CATEGORY 类型组件预留参数，待后续分类过滤功能上线后再使用

---

## 6. 风险处理

| 风险                         | 影响                                | 缓解措施                                             |
| ---------------------------- | ----------------------------------- | ---------------------------------------------------- |
| 动画资源缺失                 | 某些 AnimationType 可能没有资源     | 使用项目中已有的动画类型（WAG、TILT、SIT、LIE）      |
| 循环动画性能                 | 持续播放帧动画可能耗电              | 使用较低的 fps（如 6 fps），仅在空状态页面可见时播放 |
| 与现有 EmptyState 调用不兼容 | 可能有其他地方调用 `EmptyState()` | 给所有参数添加默认值，保持向后兼容                   |

---

## 7. 实施后的验证点

1. **待办列表空状态**：

   - 切换到"待办"标签，清空所有待办
   - 显示"还没有待办~" + 柯基摇尾巴动画
   - 点击"添加待办"按钮打开 BottomSheet
2. **已完成列表空状态**：

   - 切换到"已完成"标签，清空所有已完成
   - 显示"还没有已完成的待办~" + 柯基坐立动画
   - 点击"去添加"按钮切换到"待办"标签
3. **分类列表空状态**（预留）：

   - 组件参数支持传入 `EmptyStateType.CATEGORY`
   - 文案显示"这个分类还没有待办~"
