# CorgiMemo 项目长期记忆

## 项目约定
- 不需要自动编译：Kotlin/代码改动后不主动跑 gradlew，除非用户明确要求。

## 正文字体体系（2026-09-03 落地，2026-09-03 改默认=系统字体）
- 内置 9 款 OFL 1.1 可商用中文（思源黑体/思源宋体/源音黑體/獅尾半月SC/悠哉/初夏明朝/马善政毛笔楷书/钟齐志莽行书/寒蝉·龙藏楷书）+ 3 款拉丁（Space Grotesk/Maple Mono/Caveat，作英文·数字回退层），共 49 资源文件在 `res/font/`；授权随 APK 分发于 `assets/licenses/`（索引 `THIRD_PARTY_FONTS.md`）。
- 架构：`FontCatalog`(注册表 `FontEntry`：id/显示名/授权/FontFamily/字重→resId；`isSystemDefault` 占位条目 `SYSTEM_DEFAULT` 用 `FontFamily.Default`，`isLatin` 区分拉丁回退层) + `FontManager`(反应式当前选中单例) + `buildTypography(fontFamily)`(动态 Typography)。
- 默认字体 = **系统默认字体**（`FontCatalog.DEFAULT_ID = "system_default"`，`CorgiPreferences.fontId` 默认值已改）；偏好存 `font_id` 键，设置页 `AppearanceScreen`「正文字体」分组切换，全 App 即时生效。
- 加粗档位 `FontEntry.boldTiers`(>400 前三档) 随字体派生；`FontWeightProbe` 像素探测按 `tag` 隔离缓存、用 `typefaceForWeight` 取当前字体 Typeface（系统默认走 `Typeface.DEFAULT`）。
- **字体加载结构（2026-09-03 五次 OOM 后根治；2026-09-04 改为分离式预览；预览统一走 `ui/theme/FontPreviewEngine.kt`）**：CJK 单文件 14~19MB，凡预览批量渲染都不可让字体常驻。引擎 = 两个**按用途分离**的有界池（字体资源拷 `cacheDir` 后 `Typeface.Builder(String)` 即时构建，**刻意绕过 TypefaceCompat**；注意本工程 android.jar **无 `Typeface.Builder(InputStream)` 重载**，只有 File/FileDescriptor/String）：`previewTypefacePool`(容量 **2** = 中文1+拉丁1，预览位图渲染专用，预渲染后 `clearPreviewTypefaces()` 清空) + `probeTypefacePool`(容量 3 = 同一款字体 B1/B2/B3 三档字重文件，供 `FontWeightProbe`)；另有预览位图 `LruCache(32)`（白色字形蒙版、compose 端 tint 着色，与主题无关；32 ≥ 两页各 13 张，避免滚动重渲染）。页面 `LaunchedEffect` 里 `prerenderAll`（编辑页 刻记26sp/Corgi19sp）或 `prerenderBodyRows`（设置页 各字体 displayName@18sp）在 IO 线程顺序渲染 → **预览常态 0 常驻字体（只留位图）**。**结构性铁律：预览绝不用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**——那会把字体驻留进 TypefaceCompat LruCache(16)/FontFamilyResolver 永久缓存，与「内容」字体双计 → 低内存设备堆 OOM（设置页+编辑页均崩过，栈 `FontFamilyResolverImpl`→`TypefaceCompat`）。系统默认条目用 `Typeface.DEFAULT` 渲染预览。`AndroidManifest` `android:largeHeap="true"` 为安全网。
- **分离式预览（2026-09-04 最终行为，取代「点选即预览」位图复刻）**：反复实时换字必 OOM（compose 全局缓存按 (族,字重) 长期持有 ~20-50MB/款）。硬约束 = **一次最多只同时加载两种字体（中文字体 1 + 英文/数字字体 1）**，预览一律走引擎位图、不常驻字体：① 编辑页：面板点选**只改 pending 高亮**、正文**不**预览（已删除旧的 `contentPreviewBitmap/Async` 正文位图覆盖层），面板头按钮**「应用」= 应用字体但保持面板展开**（连续点选对比），应用后变「完成」= 再点才收起（`hasPendingChange` 参数控制文案与行为），应用走 `viewModel.onCjk/LatinFontSelected` 写 ContentFontManager → 正文换字 + 工具栏字重按钮（档位/探测可用态）随新字体同步更新；② 设置页：「正文字体」「英文/数字字体」两个分组标题行右侧各一个「确定」按钮（`FontConfirmButton`，无 pending 改动时置灰），点选只改 pending，点「确定」才 `setFontId/setLatinFontId` 写全局字体，**未点确定直接返回 = 丢弃选择**（已删除原 `DisposableEffect` 离页自动应用）；行上的字形预览本身恒为引擎位图。两处提交后都调 `FontPreviewEngine.clearTypefaces()` 清预览池+探测池（位图与探测结果均已缓存，释放 Typeface 无损）。
- **FontFamilyResolver 硬约束（2026-09-04，`ui/theme/FontResolverPolicy.kt` + Theme.kt 注入 `LocalFontFamilyResolver`）**：compose-ui-text 1.11.2 源码核实——`createFontFamilyResolver` 的**所有实例共享进程级全局缓存**（GlobalTypefaceRequestCache=LruCache(16) + GlobalAsyncTypefaceCache），**换实例不丢缓存**；真丢弃需 `emptyCacheFontFamilyResolver`（@InternalTextApi+@RestrictTo(LIBRARY_GROUP)，私有缓存，官方给测试/基准用）+ `androidx.core.graphics.TypefaceCompat.clearCache()`（**公开 API**，清 ResourcesCompat 静态 LruCache(16)——Compose 加载 ResourceFont 的实际通道）。Theme.kt 以 `fontCacheKey = chrome中文|chrome拉丁|内容中文|内容拉丁` 为 key `remember { FontResolverPolicy.createIsolatedResolver(appContext) }`，组合一变即换新实例并清 core 静态缓存 → 旧字体全部强引用断开可 GC。已知取舍：① 隔离 resolver 不带 AndroidFontResolveInterceptor（系统「粗体文字」无障碍字重加成失效；兜底路径不受影响）；② 每次切字体整树重解析+当前字体文件重读（单次几十 ms）；③ 反射性失败兜底回退 `createFontFamilyResolver`（退化为软约束）；④ 列表页多款内容字体同屏属合法显示需求，不算违反约束。
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
