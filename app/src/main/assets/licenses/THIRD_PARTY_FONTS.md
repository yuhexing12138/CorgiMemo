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
| 马善政毛笔楷书 | Ma Shan Zheng | MaShanZheng-OFL-1.1.txt | Google Fonts（github.com/googlefonts/ma-shan-zheng） | ma_shan_zheng_regular（1档：400）· 正文字体·中文手写 |
| 钟齐志莽行书 | Zhi Mang Xing | ZhiMangXing-OFL-1.1.txt | Google Fonts（github.com/googlefonts/liu-jian-mao-cao） | zhi_mang_xing_regular（1档：400）· 正文字体·中文手写 |
| 寒蝉·龙藏楷书 | Long Cang | LongCangKaiShu-OFL-1.1.txt | free-font/docs/fonts/寒蝉字型/寒蝉书体·龙藏楷书.otf | chill_long_cang_kaishu_regular（1档：400）· 正文字体·中文手写 |
| Caveat | Caveat | Caveat-OFL-1.1.txt | free-font/docs/fonts/english/caveat/ | caveat_*（2档：400/700）· 英文/数字·拉丁回退层·手写 |

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

## 手写字体（v2026-09-03 新增）

原 6 款正文 + 2 款拉丁均无手写体。本轮精选 4 款严格 OFL-1.1 的手写体接入，覆盖「中文手写正文」与「英文/数字手写回退」两类需求：

- **马善政毛笔楷书（Ma Shan Zheng）**：Google Fonts 毛笔楷书，单档 400；作「正文字体」可选项，适合标题、摘录、手账感中文。
- **钟齐志莽行书（Zhi Mang Xing）**：Google Fonts 行书，单档 400；潇洒连贯，适合随手记、随感。
- **寒蝉·龙藏楷书（Long Cang）**：ChillType 楷书，单档 400；清秀克制，适合笔记正文手写化。
- **Caveat**：拉丁手写体（Pabla Stanley），2 档（Regular/Bold）；标记 `isLatin = true`，作「英文/数字」拉丁回退层，使中文手写正文里的英文/数字也带手写感。

**接入方式**：

- 3 款中文手写单档（仅 400 Regular），`boldTiers` 自动为空 —— 设置页切换为手写体后，工具栏加粗档位（B1/B2/B3）会置灰（手写体无更重字面，符合既有「无独立字面则置灰」行为）；渲染按字面正常显示。
- Caveat 走拉丁回退层（与 Space Grotesk / Maple Mono 同机制），中文手写正文里英文/数字走 Caveat、中文走选中的手写正文字体。
- 3 款中文手写体与 Caveat 版权头与授权正文均经 `licenses/*-OFL-1.1.txt` 随 APK 分发于 `app/src/main/assets/licenses/`。

> 说明：马善政毛笔楷书与钟齐志莽行书在本地 free-font 快照中缺失，由 Google Fonts 上游仓库直接取得（版权头与 github 仓库一致、OFL-1.1 授权）；寒蝉·龙藏楷书与 Caveat 取自 free-font 库。
