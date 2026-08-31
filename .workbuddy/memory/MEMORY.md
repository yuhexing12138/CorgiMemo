# CorgiMemo 项目长期记忆

## 项目约定
- **不需要自动编译**：完成 Kotlin/代码改动后，不要主动运行 `./gradlew` 编译或构建验证（Gradle 在本机常因文件写权限 `AccessDeniedException` 失败）；除非用户明确要求，跳过构建。

## 关键组件：SwipeableImageStack（灵感页堆叠图）
- 可见深度 `visibleDepth: Int = 4`；`M = minOf(visibleDepth, cardCount).coerceIn(1,4)` 把可见深度上限锁 4。
- 扇形四要素（x/y/scale/rotation）均按 `ei = min(stackIndex, M-1)` 夹取，超出 M 的卡片叠栈底。
- 旋转角由可见张数派生：`effectiveTiltAngle = -(M-1)*15`（M=1→0°、2→-15°、3→-30°、4→-45°）；旧原型 `preview_stack.html` 用 120dp/−60° 已过时，源码展开态视觉边长 S≈150dp、fanAngle=−45°。
- 展开态「收起」按钮：半胶囊形，高度与「展开」按钮宽度动态相等，吸附**时间线竖线左缘 77dp**（相切、圆头朝外 `RoundedCornerShape(topStart=H/2,topEnd=0,bottomEnd=0,bottomStart=H/2)`）；水平 offset `(StackLeftCompensation - 11.dp - collapseBtnW)*(1-p)`；垂直 `topCardAnchorY + cardHeight/2 - H/2`。
- 因祖先 `animateContentSize` 的 clipToBounds 会裁掉移出 Stage 左缘的按钮，采用「Stage 左扩 `collapseBtnLeftExtend*(1-p)` + 内容层 `+collapseBtnLeftExtend*p` 补偿」机制。
- 调用方 `TimelineInspirationItem.kt` 图片外层 Box 挂 `pointerInput(detectTapGestures(onTap={}))` 兜底整行空白吞噬；组件内 Stage 层保留接住「屏宽-36~屏宽」段。

## 编辑态内联图片 / 语音（v2026-08-30 内联方案）
- 编辑态 `BasicRichTextEditor`（基于 `BasicTextField`）Compose 1.11 无 `inlineContent` 参数，问号方块根因；只读态 `BasicRichText` 才支持。库内 `InlineImageOverlay.kt` 已覆盖层方案：`resolveInlineImagePlacements`→`ApplyInlineImageSizes`→`drawInlineImages`。
- 图片是 `RichSpanStyle.Image`（非 token）；编辑态点击预览：`TokenInteractionHandlers.kt` 新增 `ImageClickHandler`+`LocalImageClickHandler`，`InlineImageOverlay.kt` 新增 `findInlineImagePlacementAt`+`pointerInputInlineImages`，App 侧 `InspirationEditScreen` 注入 handler 打开全屏预览 Dialog。
- 语音是 `trigger:voice` 的原子 token（点击经 `LocalTokenClickHandler` 播放）；旧 `VoicePlayerComponent` 不再渲染；`saveInlineMediaBlocks` 从 markdown 反解析重建 content_blocks。
- 警惕：主仓库 `compose-rich-editor` 子模块指针可能指向已丢失提交，需重新提交指针。

## 已弃用工具清理（2026-08-31 完成）
- 已编辑 3 份文档移除引用 → 提交 `564e19b8` → 推送 `origin/master`，云端已无追踪引用（`git grep` 校验通过）。
- 本地残留（被 safe-delete/genie-trash 守卫拦截）：输出目录 watch 日志、虚拟环境 Scripts 下 CLI 可执行文件，须本机文件管理器删。用户 10:54 手动删除后四层验证全过。
- 遗留：git 历史对象库可能仍含旧字样（清理提交 `564e19b8` 及更早历史），属 `.git` 对象库不出现在工作区，改写风险大故不动；服务端用户配置可能仍注入旧指令（需在平台用户设置清理）。

## 资源位置
- 设计稿：Ardot `刻记+ APP 线框图与交互原型`（fileId 707225018209249）。
- 堆叠图原型：`图片库/preview_stack.html`（已本地化 vendor 依赖，可离线打开）；`designs/stack-collapse-button/collapse-button-position.html` + `overview.md`。
- 源码：`app/.../ui/components/SwipeableImageStack.kt`、`TimelineInspirationItem.kt`、`InspirationEditScreen` 等。
