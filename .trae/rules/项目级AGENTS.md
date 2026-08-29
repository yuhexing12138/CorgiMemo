---
alwaysApply: true
---

# 项目级 AGENTS 入口（subagent 启动提示）

**所有 subagent 启动时必须先读本文件 + `.trae/rules/` 下所有规则**，再开始任何回答或代码操作。

---

## 1. 项目一句话

**CorgiMemo** —— Android 待办事项 / 日程管理 App。

- 本地优先、Jetpack Compose、Material 3
- 4 个 todo 区：PINNED_PENDING / PENDING / PINNED_COMPLETED / COMPLETED
- 拖拽跨区 + 合并拖拽
- 富文本笔记（compose-rich-editor）
- 农历 / 节气提醒（tyme4kt）
- Room 本地数据库 + Hilt 注入

## 2. 技术栈速查

| 维度 | 版本 / 选型 |
| --- | --- |
| 语言 | Kotlin（仅 Kotlin，无 Java） |
| UI | Jetpack Compose（Material 3） |
| DI | Hilt（KSP 注解处理） |
| DB | Room（KSP） |
| 构建 | Gradle KTS + AGP 8.x + KSP |
| JDK | 17 |
| Compose BOM | 2026.04.01（Compose 1.9.2） |
| Min SDK | 见 `app/build.gradle.kts` |

> **Compose BOM 2026.04.01 + Compose 1.9.2 是非常新的版本**：当 API 不可用或不兼容时，**首先寻找原因修复**，实在不可用再考虑其他方法。详见 `.trae/rules/api不可用处理规则.md`。

## 3. 工作目录与子模块

**仓库根**：`c:\Users\EDY\Desktop\CorgiMemo`

3 个 Git 子模块：

| 子模块 | 用途 |
| --- | --- |
| `compose-rich-editor/` | 富文本编辑器 |
| `Reorderable/` | 拖拽排序组件 |
| `tyme4kt/` | 农历 / 节气 |

## 5. 规则链（按优先级，必须全部遵守）

| 序号 | 规则文件 | 主题 |
| --- | --- | --- |
| 1 | `.trae/rules/编译验证.md` | 编译前必须询问用户（**最高优先级**） |
| 2 | `.trae/rules/调用AskUserQuestion 工具询问.md` | 任务中必须用 AskUserQuestion 询问下一步 |
| 3 | `.trae/rules/UI设计规范.md` | UI 代码引用 `docs/superpowers/specs/UI设计规范.md` |
| 4 | `.trae/rules/UI 原型展示.md` | UI 设计需在浏览器展示完整原型 |
| 5 | `.trae/rules/api不可用处理规则.md` | 优先修复 API 不兼容 |
| 6 | `.trae/rules/entity与 migration同步检查.md` | `@ColumnInfo(defaultValue)` 与 Migration DEFAULT 一致 |
| 7 | `.trae/rules/import语句检查.md` | 编辑后必须检查 import 缺失 |
| 8 | `.trae/rules/lambda 捕获陷阱防御.md` | Compose 长效 lambda 用最新状态 |
| 9 | `.trae/rules/优化建议.md` | 任务末尾提供优化建议 |
| 10 | `.trae/rules/文档命名语言要求.md` | .md 文档用中文命名 |
| 11 | `.trae/rules/git提交.md` | 任务后询问 commit，提交信息用中文 |
| 12 | `.trae/rules/安卓应用开发.md` | Android 开发规范 |
| 13 | `.trae/rules/巨石组件拆分规范.md` | 单文件 ≥ 800 行时按 model/sections/dialogs 拆分，薄壳保 API 兼容 |

## 6. 主要命令

| 任务 | 命令 | 谁来执行 |
| --- | --- | --- |
| 构建 Debug | `./gradlew assembleDebug` | **用户**（AI 不擅自执行） |
| 跑测试 | `./gradlew test` | **用户**（AI 不擅自执行） |

## 7. 重要约束（来自项目记忆）

> 这些是反复踩过的坑，新代码 / 改动必须遵守。

- **TodoZone 4 值枚举**（来自 `isPinned × status`）：PINNED_PENDING / PENDING / PINNED_COMPLETED / COMPLETED
- **Sort order 段位**：PINNED_PENDING (0-9999) / PENDING (10000-19999) / PINNED_COMPLETED (20000-29999) / COMPLETED (30000-39999)
- **拖拽状态机**：`DragZoneStateMachine`（单体）+ `MergeDragZoneStateMachine`（批量），拖拽中 visual state 必须实时更新
- **多卡架构**：`groupId=0` 是主卡片，`getSavedSubTodos()` 必须排除
- **Sharing**：`ShareCoordinator` 单一入口，`getMainTodoForShare()` + `getSavedSubTodos()` + `listOf(mainTodo) + savedSubTodos`
- **Snackbar**：用 `AppSnackbarHost`，**禁用** Android 系统 Toast
- **Seed 数据**：由 `KEY_SEEDED` 标志控制幂等
- **长按面板**：恰好 4 个选项（置顶/标签/改日期/删除），**无"调试输出 imagePaths"**

## 8. 不自动做的事

- ❌ **不自动编译** — 编译前必须用 AskUserQuestion 询问用户
- ❌ **不自动 commit** — 每次任务结束后用 AskUserQuestion 询问
- ❌ **不擅自跑 `gradlew` / `./gradlew`** — 同上
- ❌ **不擅自改 `app/build.gradle.kts` 的 Compose BOM 版本** — 修复优先

## 9. 与用户交流

- **全程中文**
- **复杂概念用 Markdown 表格 / 列表**
- **每个操作 / 代码变更解释背后的原因**
- **生成代码时加函数级注释**
- **只使用 Windows 系统命令**（PowerShell）

---
