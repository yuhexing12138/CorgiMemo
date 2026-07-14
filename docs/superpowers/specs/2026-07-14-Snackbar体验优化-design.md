# Snackbar 体验优化 - 需求规格

**日期**：2026-07-14
**类型**：UI 优化 + 交互改进
**前置任务**：[Snackbar 格式重设计](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar格式重设计-design.md)

---

## 1. 背景

Snackbar 格式重设计后，全项目已统一为 `AppSnackbarHost`。但仍有 4 项体验问题需要解决：

| # | 问题 | 期望行为 |
|---|------|---------|
| 1 | 无按钮的 Snackbar 仍占满全宽（如"权限不足"），视觉过重 | **自适应内容宽度**，底部居中，最大 560dp |
| 2 | 带按钮的 Snackbar 中，按钮与文字混在一起，文字过长时按钮被挤压 | 文字靠左（weight=1f），按钮靠右，宽度自适应 |
| 3 | 待办删除有 2 阶段 Snackbar（"已删除" → "已恢复"），多余 | 合并为 1 阶段（"已删除 N 个待办" + 撤销） |
| 4 | 灵感页删除灵感时无 Snackbar，无法撤销 | 添加"已删除" + 撤销按钮 Snackbar |
| 补 | Snackbar 整体偏高（图标 36dp + padding 10dp） | 缩小图标到 28dp，垂直内边距缩到 8dp |

---

## 2. 设计目标

| 目标 | 描述 |
|------|------|
| **宽度自适应** | Snackbar 容器宽度跟随内容，最大不超过 560dp（Material 3 推荐） |
| **底部居中** | 不带按钮时，Snackbar 居中显示而非 fillMaxWidth |
| **左文右按钮** | 带按钮时，提示文字靠左、按钮靠右，两者之间用 Spacer 隔开 |
| **删除单阶段** | 待办删除流程只显示 1 个 Snackbar（带撤销），不再有"已恢复"二级提示 |
| **灵感撤销** | 灵感删除时显示带撤销按钮的 Snackbar，撤销可恢复 |
| **高度紧凑** | Snackbar 整体高度降低 4-6dp，更轻盈 |

---

## 3. 视觉规范

### 3.1 AppSnackbarHost 容器

| 维度 | 当前值 | 新值 | 说明 |
|------|--------|------|------|
| **容器宽度** | `fillMaxWidth()` | `widthIn(min = 0.dp, max = 560.dp)` | 跟随内容，最大 560dp |
| **容器对齐** | 撑满底部 | 居中（通过 Scaffold 槽位外层 Box） | 短文本居中显示 |
| **容器外边距** | `padding(12.dp)` | `padding(horizontal = 16.dp, vertical = 12.dp)` | 加大左右留白，上下保持 |
| **容器内边距** | `padding(horizontal = 16.dp, vertical = 10.dp)` | `padding(horizontal = 16.dp, vertical = 8.dp)` | 垂直内边距 10→8 |
| **圆角** | `RoundedCornerShape(20.dp)` | 保持 | 已有 |
| **阴影** | `shadowElevation = 4.dp` | 保持 | 已有 |

### 3.2 左侧图标

| 维度 | 当前值 | 新值 | 说明 |
|------|--------|------|------|
| **图标大小** | `Modifier.size(36.dp)` | `Modifier.size(28.dp)` | 缩小 8dp |
| **图标资源** | `R.drawable.corgi_tilt_2frames_01` | 保持 | 已有 |
| **图标-文字间距** | `Spacer(width = 12.dp)` | 保持 | 已有 |

### 3.3 文字

| 维度 | 当前值 | 新值 | 说明 |
|------|--------|------|------|
| **字号** | `14.sp` | 保持 | 已有 |
| **maxLines** | `1` + `Ellipsis` | 保持 | 已有 |
| **权重** | `weight(1f, fill = false)` | `weight(1f, fill = false)` | 不带按钮时居中；带按钮时靠左 |

### 3.4 右侧按钮

| 维度 | 当前值 | 新值 | 说明 |
|------|--------|------|------|
| **按钮组件** | `TextButton` | 保持 | 已有 |
| **按钮-文字间距** | `Spacer(width = 8.dp)` | `Spacer(width = 8.dp)` | 保持 |
| **按钮对齐** | 由 Row 决定 | 靠右 | 由 weight 1f 文字 + 按钮自动实现 |

### 3.5 关键约束

- **无按钮时**：文字 + 图标整体居中（通过 Row `horizontalArrangement = Arrangement.Center` + `fillMaxWidth` 失效时自然居中）
- **带按钮时**：文字 `weight(1f)` 占满剩余空间，按钮靠最右
- **底部位置**：保持 Material 3 默认底部居中（容器本身 widthIn 控制）

---

## 4. 行为规范

### 4.1 待办删除单阶段

**当前流程**（2 阶段）：
1. 删除 N 个待办 → Snackbar 1: "已删除 N 个待办" + 撤销
2. 用户点撤销 → 恢复 N 个待办 → Snackbar 2: "已恢复 N 个待办"

**新流程**（1 阶段）：
1. 删除 N 个待办 → Snackbar: "已删除 N 个待办" + 撤销
2. 用户点撤销 → 恢复 N 个待办 → **不再显示 Snackbar 2**（静默恢复）

**实现位置**：`HomeViewModel.kt` 中 `handleUndoDelete` 函数删除"已恢复"Snackbar 触发

### 4.2 灵感删除 Snackbar

**新增功能**：
- 灵感页长按删除 / 左滑删除 → 软删除到回收站
- Snackbar: "已删除" + 撤销
- 撤销：恢复灵感（从回收站移回灵感表）
- 自动消失：5s 后永久删除

**实现位置**：
- `InspirationViewModel.kt`：添加 `pendingDeletedInspiration` StateFlow + `undoDeleteInspiration()` 方法
- `InspirationScreen.kt` 或 `InspirationViewScreen.kt`：监听状态，触发 Snackbar

---

## 5. 改造清单

### 5.1 修改文件

| # | 文件 | 改造 |
|---|------|------|
| 1 | `AppSnackbarHost.kt` | 容器宽度自适应 + 图标缩小 + 垂直内边距 |
| 2 | `HomeViewModel.kt` | 删除 handleUndoDelete 中的"已恢复"Snackbar 触发 |
| 3 | `InspirationViewModel.kt` | 添加 pendingDeletedInspiration 状态 + 撤销方法 |
| 4 | `InspirationScreen.kt` 或 `InspirationViewScreen.kt` | 监听灵感删除状态，触发 Snackbar |

### 5.2 不在范围

| 项 | 说明 |
|-----|------|
| **其他类型 Snackbar（如日期删除、日期提醒）** | 保持现状，不在本任务 |
| **图片保存 Snackbar** | 保持现状 |
| **灵感回收站** | 已存在，本次只添加删除流程的 Snackbar |
| **多 Snackbar 队列** | 不在本任务 |
| **Snackbar 入场动画** | 不在本任务 |

---

## 6. 测试要点

| 场景 | 验证点 |
|------|--------|
| 短文本无按钮 | Snackbar 居中显示，宽度跟随内容 |
| 长文本无按钮 | 末尾省略号截断，不换行，最大 560dp |
| 短文本带按钮 | 文字靠左，按钮靠右，两者分隔 |
| 长文本带按钮 | 文字省略号截断，按钮不挤压 |
| 待办删除 | 只显示 1 个 Snackbar（带撤销），撤销后无第二个 Snackbar |
| 灵感删除 | 显示"已删除" + 撤销，撤销后灵感恢复 |
| 灵感删除超时 | 5s 后灵感永久消失 |

---

## 7. 后续可优化点

| 优化项 | 说明 |
|--------|------|
| **多 Snackbar 队列** | 当前单队列，并发会替换；可改用多 SnackbarHostState |
| **Snackbar 入场动画** | 从下方滑入 + 淡入 |
| **设计 token 化** | 560dp 最大宽度、28dp 图标可抽到 UiTokens |
| **可访问性** | Image contentDescription 可改为 "应用提示" 供 TalkBack |
