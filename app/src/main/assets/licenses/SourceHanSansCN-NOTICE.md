# 第三方字体授权快照 · 思源黑体（Source Han Sans CN）

本项目在 `app/src/main/res/font/` 内置了 **思源黑体简体中文** 字体，用于根治正文
B1(500) / B2(700) / B3(900) 三档字重「视觉撞档」问题（系统默认字体缺 500 字面，
被量化合并进 700）。本目录是该字体的授权与来源快照，满足 SIL OFL 1.1 的随附要求。

## 字体身份

| 项目 | 内容 |
| --- | --- |
| 名称 | Source Han Sans CN（思源黑体 简体中文） |
| 出品方 | Adobe |
| PostScript 名 | `SourceHanSansCN-*` |
| 字形数 | 30,888 |
| 授权 | **SIL Open Font License 1.1**（可商用、可修改、可再分发，唯一限制：不得单独售卖字体本身） |
| 授权文本 | 同目录 `SourceHanSansCN-OFL-1.1.txt`（权威英文原文） |

> 版权头（已从字体 name 表实际抽取核对）：
> `Copyright © 2014 Adobe Systems Incorporated. All Rights Reserved.`
> （Reserved Font Name 为 `Source`，依 Source Han Sans 系列惯例。）

## 来源

- 字体库合集：`jaywcjlove/free-font`（GitHub，仓库本身为 MIT，仅作索引，字体授权以字体自身为准）
- 实际路径：`free-font/docs/fonts/思源字体系列/思源黑体/`
- `free-font/` 已被 `.gitignore` 忽略（约 13 GB，不入库）；本快照记录其来源以供溯源。

## 入库的文件（app/src/main/res/font/）

| 资源名 | 字重 | 来源文件 | 大小 |
| --- | --- | --- | --- |
| `source_hans_sans_cn_regular.otf` | 400 Normal | 思源黑体-Regular.otf | 8.0 MB |
| `source_hans_sans_cn_medium.otf`  | 500 Medium | 思源黑体-Medium.otf  | 8.1 MB |
| `source_hans_sans_cn_bold.otf`    | 700 Bold   | 思源黑体-Bold.otf    | 8.3 MB |
| `source_hans_sans_cn_heavy.otf`   | 900 Black  | 思源黑体-Heavy.otf   | 8.4 MB |

## OFL 关键义务（摘要，非法律意见）

- 字体可随 App 打包、内嵌、再分发（第 2 条允许与软件捆绑分发）。
- **不得单独售卖字体文件本身**（第 1 条）。
- 每个副本须包含上述版权声明与 OFL 文本（第 2 条）——即本目录文件。
- 修改后的版本不得使用 Reserved Font Name「Source」等保留名（第 3 条）；本项目未改字体，仅重命名文件。
- 字体须始终以 OFL 授权分发（第 5 条）。
- 使用字体生成的文档（如用户笔记内容）不受 OFL 约束。

## 合规建议

- 上架应用商店前，建议在 App「关于 / 开源许可」页内展示本授权（可将本目录副本放入
  `app/src/main/assets/licenses/` 供运行时读取）。
- 若未来替换字体，须同步更新本快照与 `Type.kt` 的 `SourceHanSansCN` / `APP_FONT_AVAILABLE_WEIGHTS`。
