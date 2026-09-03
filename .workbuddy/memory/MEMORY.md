# CorgiMemo 项目长期记忆

## 项目约定
- 不需要自动编译：Kotlin/代码改动后不主动跑 gradlew，除非用户明确要求。

## 正文字体体系（2026-09-03 落地，2026-09-03 改默认=系统字体）
- 内置 9 款 OFL 1.1 可商用中文（思源黑体/思源宋体/源音黑體/獅尾半月SC/悠哉/初夏明朝/马善政毛笔楷书/钟齐志莽行书/寒蝉·龙藏楷书）+ 3 款拉丁（Space Grotesk/Maple Mono/Caveat，作英文·数字回退层），共 49 资源文件在 `res/font/`；授权随 APK 分发于 `assets/licenses/`（索引 `THIRD_PARTY_FONTS.md`）。
- 架构：`FontCatalog`(注册表 `FontEntry`：id/显示名/授权/FontFamily/字重→resId；`isSystemDefault` 占位条目 `SYSTEM_DEFAULT` 用 `FontFamily.Default`，`isLatin` 区分拉丁回退层) + `FontManager`(反应式当前选中单例) + `buildTypography(fontFamily)`(动态 Typography)。
- 默认字体 = **系统默认字体**（`FontCatalog.DEFAULT_ID = "system_default"`，`CorgiPreferences.fontId` 默认值已改）；偏好存 `font_id` 键，设置页 `AppearanceScreen`「正文字体」分组切换，全 App 即时生效。
- 加粗档位 `FontEntry.boldTiers`(>400 前三档) 随字体派生；`FontWeightProbe` 像素探测按 `tag` 隔离缓存、用 `typefaceForWeight` 取当前字体 Typeface（系统默认走 `Typeface.DEFAULT`）。
- **设置页预览 OOM 修复（2026-09-03）**：CJK 字体单文件 14~19MB，Compose `FontFamilyResolver` 永久缓存每款→10 款同时加载撑爆低内存设备堆。解法：正文预览改「有界 `LruCache<String,Bitmap>`(容量12) 把字体画进小位图（用 `Typeface.Builder(InputStream)` 即时构建、不走框架永久缓存、画完 GC）；选中行走全局 Typography 已加载字体（零额外内存），未选中走缓存位图」+ `AndroidManifest` `android:largeHeap="true"` 安全网。全部保留真实预览与完整字形集。
- 新增字体：拷资源→`FontCatalog` 登记一条，设置页自动列出。
- **字体作用域解耦（2026-09-02，请求 M）**：设置页「正文字体」只影响 App chrome（`MaterialTheme.typography`，由 `FontManager` 驱动）；用户编辑内容（灵感编辑/详情/主页）默认系统字体，与设置字体解耦。新增 `ContentFontManager`(默认 `FontCatalog.systemDefault`) + `LocalContentTypography` CompositionLocal（Theme.kt 注入，默认 `buildTypography(FontFamily.Default)`）；内容文本改走 `LocalContentTypography.current`，编辑工具栏 `FontWeightProbe` 探测改 `ContentFontManager`。未来编辑页专用字体选择器调 `ContentFontManager.setContentFont` 即可统一覆盖。编辑页内 UI 控件（RelationSelector/ImagePicker 标签、工具栏按钮）仍属 chrome，保留设置字体。

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
