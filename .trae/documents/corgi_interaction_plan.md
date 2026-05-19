# 柯基触摸互动功能实现计划

## 一、需求分析

根据业务/产品描述，需要实现以下触摸互动功能：

| 功能点 | 需求描述 | 优先级 |
| :--- | :--- | :--- |
| 单击互动 | 单击柯基显示 Toast "柯基摇了摇尾巴~" | M |
| 双击互动 | 双击柯基显示 Toast "柯基开心地打滚~" | M |
| 长按互动 | 长按柯基显示 Toast "柯基很舒服地眯起眼睛~" | M |

## 二、现有代码分析

现有 `CorgiCompanion.kt` 组件已实现：
- 柯基图片展示
- 名字显示
- 问候语显示
- 简单的单击 Toast 反馈

需要修改为支持多种触摸事件。

## 三、实现方案

### 3.1 技术选型

| 分类 | 技术 | 说明 |
| :--- | :--- | :--- |
| 触摸事件处理 | `combinedClickable` | Compose 提供的组合点击事件处理器 |
| Toast提示 | `Toast.makeText` | Android 原生 Toast |
| 双击检测 | `detectTapGestures` 或 `combinedClickable` | Compose 手势检测 |

### 3.2 修改文件

| 文件路径 | 修改内容 |
| :--- | :--- |
| `ui/components/CorgiCompanion.kt` | 将 `clickable` 替换为 `combinedClickable`，添加双击和长按事件 |

### 3.3 实现思路

使用 `combinedClickable` 可以处理多种点击事件：
- `onClick`：单击事件
- `onDoubleClick`：双击事件
- `onLongClick`：长按事件

## 四、实现步骤

### 步骤1：修改 CorgiCompanion 组件

**目标**：替换 `clickable` 为 `combinedClickable`

**实现要点**：
- 导入 `androidx.compose.foundation.combinedClickable`
- 将 `.clickable { ... }` 替换为 `.combinedClickable { ... }`
- 添加 `onDoubleClick` 和 `onLongClick` 回调

### 步骤2：添加多种 Toast 反馈

根据不同的触摸事件显示不同的提示：
- 单击："柯基摇了摇尾巴~"
- 双击："柯基开心地打滚~"
- 长按："柯基很舒服地眯起眼睛~"

## 五、测试验证

| 测试场景 | 验证内容 |
| :--- | :--- |
| 单击测试 | 单击柯基显示正确的 Toast |
| 双击测试 | 双击柯基显示正确的 Toast |
| 长按测试 | 长按柯基显示正确的 Toast |
| 交互区分 | 三种交互方式能正确区分 |

---

**计划确认**：请确认此实现计划，确认后开始执行。