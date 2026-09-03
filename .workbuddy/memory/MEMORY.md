# CorgiMemo 项目长期记忆

## 项目约定
- 不需要自动编译：Kotlin/代码改动后不主动跑 gradlew，除非用户明确要求。

## 正文字体体系（2026-09-03 落地，2026-09-03 改默认=系统字体）
- 内置 9 款 OFL 1.1 可商用中文（思源黑体/思源宋体/源音黑體/獅尾半月SC/悠哉/初夏明朝/马善政毛笔楷书/钟齐志莽行书/寒蝉·龙藏楷书）+ 3 款拉丁（Space Grotesk/Maple Mono/Caveat，作英文·数字回退层），共 49 资源文件在 `res/font/`；授权随 APK 分发于 `assets/licenses/`（索引 `THIRD_PARTY_FONTS.md`）。
- 架构：`FontCatalog`(注册表 `FontEntry`：id/显示名/授权/FontFamily/字重→resId；`isSystemDefault` 占位条目 `SYSTEM_DEFAULT` 用 `FontFamily.Default`，`isLatin` 区分拉丁回退层) + `FontManager`(反应式当前选中单例) + `buildTypography(fontFamily)`(动态 Typography)。
- 默认字体 = **系统默认字体**（`FontCatalog.DEFAULT_ID = "system_default"`，`CorgiPreferences.fontId` 默认值已改）；偏好存 `font_id` 键，设置页 `AppearanceScreen`「正文字体」分组切换，全 App 即时生效。
- 加粗档位 `FontEntry.boldTiers`(>400 前三档) 随字体派生；`FontWeightProbe` 像素探测按 `tag` 隔离缓存、用 `typefaceForWeight` 取当前字体 Typeface（系统默认走 `Typeface.DEFAULT`）。
- **字体加载结构（2026-09-03 五次 OOM 后根治；预览统一走 `ui/theme/FontPreviewEngine.kt`）**：CJK 单文件 14~19MB，凡预览批量渲染都不可让字体常驻。引擎 = 有界 `LruCache<String,Typeface>(3)` 池（字体资源拷 `cacheDir` 后 `Typeface.Builder(String)` 即时构建，**刻意绕过 TypefaceCompat**；注意本工程 android.jar **无 `Typeface.Builder(InputStream)` 重载**，只有 File/FileDescriptor/String）+ 预览位图 `LruCache(16)`（白色字形蒙版、compose 端 tint 着色，与主题无关）。页面 `LaunchedEffect` 里 `prerenderAll`（编辑页 刻记26sp/Corgi19sp）或 `prerenderBodyRows`（设置页 各字体 displayName@18sp）在 IO 线程顺序渲染，完成后 `clearTypefaces()` → **预览常态 0 常驻字体（只留位图）**。**结构性铁律：预览绝不用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**——那会把字体驻留进 TypefaceCompat LruCache(16)/FontFamilyResolver 永久缓存，与「内容」字体双计 → 低内存设备堆 OOM（设置页+编辑页均崩过，栈 `FontFamilyResolverImpl`→`TypefaceCompat`）。「内容」字体（全局 Typography/编辑 LocalContentTypography）经 FontFamilyResolver 常驻 ≤12 款 ≈204MB 是唯一允许的常驻集，与预览隔离后整体 <256MB。字重探测 `FontEntry.typefaceForWeight` 也走引擎有界池。系统默认条目用 `Typeface.DEFAULT` 渲染预览。设置页拉丁组预览已并入位图分支（仅选中行走实时 Text）。`AndroidManifest` `android:largeHeap="true"` 为安全网。
- **点选即预览 + 确认应用（2026-09-03 最终行为）**：FontFamilyResolver 无清缓存 API（compose-ui 1.11.2 javap 验证：Resolver 仅 preload/resolve），反复点选实时换字必 OOM（每款按 (族,字重) 永久缓存 ~20-50MB/款）。故字体选择一律「pending + 即时预览、确认才应用」：编辑页面板点选→正文覆预览位图（`FontPreviewEngine.contentPreviewBitmap` StaticLayout，零缓存）、点「完成」一次性写 ContentFontManager；设置页行预览全走引擎位图、点选只更新 pending、`DisposableEffect.onDispose` 离页才一次性写 FontManager。反复点选 0 新增常驻字体。
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
