# CorgiMemo 项目长期记忆

## 项目约定
- 不需要自动编译：Kotlin/代码改动后不主动跑 gradlew，除非用户明确要求。

## 正文字体体系（2026-09-03 落地）
- 内置 6 款 OFL 1.1 可商用中文多字重字体（思源黑体/思源宋体/源音黑體/獅尾半月SC/悠哉/初夏明朝），35 个资源文件在 `res/font/`；授权随 APK 分发于 `assets/licenses/`（索引 `THIRD_PARTY_FONTS.md`）。
- 架构：`FontCatalog`(字体注册表 `FontEntry`：id/显示名/授权/FontFamily/字重→resId) + `FontManager`(反应式当前选中单例，默认思源黑体) + `buildTypography(fontFamily)`(动态 Typography)。
- 偏好存 `CorgiPreferences` 键 `font_id`（默认字面量 `"source_han_sans_cn"`，data 层不反向依赖 ui 包）；设置页 `AppearanceScreen`「正文字体」分组切换，全 App 即时生效（镜像 ThemeManager：仅 SettingsViewModel 初始化）。
- 加粗档位 `FontEntry.boldTiers`(>400 前三档) 随字体派生；`FontWeightProbe` 像素探测按 `FontManager.tag` 隔离缓存、用 `FontManager.typefaceForWeight` 取当前字体 Typeface。
- 新增字体：拷资源→`FontCatalog` 登记一条，设置页自动列出。

## 编辑态图文混排（路线 4，2026-09-01 决策）
- Compose 1.11 BasicTextField 无 inlineContent，覆盖层方案收敛性无保证→走**块级图文交错**（`BodyBlocksEditor` + 每块一个 RichTextEditor）。语音保持 `trigger:voice` 内联。详情见 `docs/路线4-块级图片-实施方案.md`。
- 撤销：自建 Command 命令栈（管块增删/排序/图片属性）+ 库内 `RichTextState.history`（管块内富文本），两套历史隔离；焦点判断是调度核心。
- 后续需求：图片裁剪/缩放/备注（存 `originalPath`+`cropRect`，不覆盖原图）。
- 警惕：`compose-rich-editor` 子模块指针可能指向已丢失提交，需重新提交指针。

## SwipeableImageStack（灵感页堆叠图）
- 可见深度锁 4；扇形按 `ei=min(stackIndex,M-1)` 夹取；旋转角 `-(M-1)*15`。
- 展开态收起按钮半胶囊吸附时间线竖线左缘 77dp；祖先 `animateContentSize` 裁剪→用「Stage 左扩 + 内容层补偿」机制。

## 资源位置
- 设计稿 Ardot fileId 707225018209249；字体素材库 `free-font/`(13GB,本地,.gitignore)；报告 `free-font-可商用字体库调研报告.md`。
