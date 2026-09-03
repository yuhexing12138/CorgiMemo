# 第三方字体授权索引（CorgiMemo 内置字体）

全部为 **SIL Open Font License 1.1**（可商用、可修改、可再分发，唯一限制：不得单独售卖字体本身）。

每个字体目录下的 `*-OFL-1.1.txt` 含权威英文授权正文 + 该字体版权头；`free-font/` 已被 .gitignore 忽略。


| 字体 | 英文/PostScript | 授权文件 | 来源路径 | 入库文件 |

---|---|---|---|---|
| 思源黑体 | Source Han Sans CN | SourceHanSansCN-OFL-1.1.txt + SourceHanSansCN-NOTICE.md | free-font/docs/fonts/思源字体系列/思源黑体/ | source_hans_sans_cn_*（4档：400/500/700/900） |
| 思源宋体 | SourceHanSerifCN | SourceHanSerifCN-OFL-1.1.txt | free-font/docs/fonts/思源字体系列/思源宋体/ | source_han_serif_cn_*（7档：200/300/400/500/600/700/900） |
| 源音黑體 | GenneGothic | GenneGothic-OFL-1.1.txt | free-font/docs/fonts/源音黑體/ | genne_gothic_*（6档：200/300/400/500/700/900） |
| 獅尾半月字體(SC) | SweiHalfMoonSC | SweiHalfMoonSC-OFL-1.1.txt | free-font/docs/fonts/獅尾半月字體/ | swei_half_moon_sc_*（7档：100/300/350/400/500/700/900） |
| 悠哉字体 | Yozai | Yozai-OFL-1.1.txt | free-font/docs/fonts/悠哉字体/ | yozai_*（4档：300/400/500/700） |
| 初夏明朝體 | EarlySummerMincho | EarlySummerMincho-OFL-1.1.txt | free-font/docs/fonts/初夏明朝體/ | early_summer_mincho_*（7档：200/300/400/500/600/700/900） |
| Space Grotesk | SpaceGrotesk | SpaceGrotesk-OFL-1.1.txt | free-font/docs/fonts/english/SpaceGrotesk/ | space_grotesk_*（4档：300/400/500/700）· 英文/数字·拉丁回退层 |
| Maple Mono | MapleMono | MapleMono-OFL-1.1.txt | free-font/docs/fonts/english/MapleMono/ | maple_mono_*（5档：300/400/500/600/700）· 英文/数字·拉丁回退层 |

## 运行期接入（v2026-09-03）

- 字体注册表：`app/src/main/java/com/corgimemo/app/ui/theme/FontCatalog.kt`（`FontEntry` 列表，含 `FontFamily` 与字重→资源映射）。
- 选中状态：反应式单例 `FontManager`（默认思源黑体），持久化于 `CorgiPreferences` 键 `font_id`（`EncryptedSharedPreferences`）。
- 设置页：外观设置页「正文字体」分组列出全部条目，切换即时生效（全 App Typography 与编辑工具栏加粗档位随之更新）。
- 授权文本：随 App 分发于 `app/src/main/assets/licenses/`（本索引与各 `*-OFL-1.1.txt`）。
- 新增字体流程：拷贝资源到 `res/font/` → 在 `FontCatalog` 登记一条 `FontEntry`，设置页自动列出，无需改动其他代码。

## 英文/数字字体（拉丁回退层，v2026-09-03 新增）

原 6 款均为中文（CJK）字体，自带拉丁字形仅作回退、并非专门的英文/数字字体。本轮从 free-font 的 `english/` 目录精选 2 款严格 OFL-1.1 的拉丁字体接入：

- **Space Grotesk**：现代几何无衬线，数字设计有特色（适合正文英文与标题数字）。
- **Maple Mono**：现代等宽字体，数字严格对齐（适合代码块、账目、时间线等需对齐数字的场景）。

**接入方式 = 拉丁回退层（非独立正文字体）**：中文笔记 App 若把纯 Latin 字体设为「正文字体」，中文会回退系统默认字体。故这两款标记 `isLatin = true`，在设置页「英文/数字字体」分组单独列出，作为**选中正文字体的字形回退层**——

- 中文走选中的正文字体（如思源黑体），英文/数字走该 Latin 字体，二者互不打架；
- 合成规则：`FontManager.combinedFamily(cjk, latin)` = `FontFamily(latin.fonts + cjk.fonts)`（拉丁在前、中文在后，Compose 按字形回退）；
- 偏好键 `latin_font_id`（空串 = 不叠加，英文/数字走正文字体自带拉丁字形），持久化于同一 `EncryptedSharedPreferences`；
- 加粗档位（B1/B2/B3）仍由正文字体决定，Latin 字体只作字形回退，不参与工具栏档位探测。
