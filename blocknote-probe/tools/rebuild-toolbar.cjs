/* 一次性重组 RichTextFormatToolbar 的 Row 内容为「+ 菜单分类 + 浮层格式区」目标顺序 */
/**
 * ⚠️ 已停用（2026-09-17）：本脚本会用内置 NEW_CONTENT 整段覆盖 RichTextFormatToolbar.kt 的 Row 内容，
 * 会丢失此后对工具栏的全部手工修改（浮层 11 键重桥等）。后续请直接编辑该 Kotlin 文件。
 * 确需重跑时设环境变量 FORCE_REBUILD=1。
 */
if (!process.env.FORCE_REBUILD) {
  console.error(
    "已停用：rebuild-toolbar.cjs 会覆盖 RichTextFormatToolbar.kt 的手工桥接修改。" +
      "请直接编辑该 Kotlin 文件；确需重跑请设 FORCE_REBUILD=1。"
  );
  process.exit(1);
}
const fs = require("fs");
const P = "C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/RichTextFormatToolbar.kt";
let s = fs.readFileSync(P, "utf8").replace(/\r\n/g, "\n");

const rowStartMarker = "    Row(\n        modifier = modifier\n            .fillMaxWidth()\n            .horizontalScroll(rememberScrollState())";
const rowStartIdx = s.indexOf(rowStartMarker);
if (rowStartIdx < 0) { console.error("Row start not found"); process.exit(1); }

// 函数结束：Row 内容后第一个 "\n    }\n}"（函数体闭合）
const endMarker = "\n    }\n}";
const endIdx = s.indexOf(endMarker, rowStartIdx);
if (endIdx < 0) { console.error("Row end not found"); process.exit(1); }

const NEW_CONTENT = `    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** ====== 组一：面板与行内格式（+ 浮层同序前段：字体 / 字号颜色 / B / I / U / S） ====== */
        FormatButtonGroup {
            FormatIconButton(
                imageVector = LucideIcons.Type,
                isActive = isFontPanelOpen,
                onClick = onFontPickerClick,
                contentDescription = "字体"
            )
            FormatIconButton(
                imageVector = LucideIcons.CaseSensitive,
                isActive = isSizeColorPanelOpen,
                onClick = onSizeColorPanelClick,
                contentDescription = "字号与颜色"
            )
            /** 加粗主按钮：BlockNote 模式（单档）点击直接 toggle 加粗；Compose 模式展开字重菜单 */
            FormatWeightButton(
                tier = currentTier,
                expanded = !boldSingleTier && boldExpanded,
                isActive = if (boldSingleTier) false else currentTier != null,
                singleTier = boldSingleTier,
                onClick = {
                    if (boldSingleTier) onSetFontWeight(700)
                    else boldExpanded = !boldExpanded
                },
                contentDescription = "加粗"
            )
            /** 展开态：同行显示 B1/B2/B3 子按钮（候选档位 500/700/900；经像素探测无独立字形的档位置灰禁用），选中后自动收起
             *  BlockNote 模式不展开（单档加粗） */
            AnimatedVisibility(
                visible = !boldSingleTier && boldExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally()
            ) {
                Row {
                    contentEntry.boldTiers.forEachIndexed { index, weight ->
                        val tier = index + 1
                        FormatWeightTierButton(
                            tier = tier,
                            isActive = currentWeight == weight,
                            enabled = weight in distinctWeights,
                            onClick = {
                                onSetFontWeight(weight)
                                boldExpanded = false
                            }
                        )
                    }
                }
            }
            FormatIconButton(
                imageVector = Icons.Default.FormatItalic,
                isActive = state.currentSpanStyle.fontStyle == FontStyle.Italic,
                onClick = onToggleItalic,
                contentDescription = "斜体"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatUnderlined,
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                onClick = onToggleUnderline,
                contentDescription = "下划线"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatStrikethrough,
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                onClick = onToggleStrikethrough,
                contentDescription = "删除线"
            )
        }

        ToolbarDivider()

        /** ====== 组二（浮层同序中段）：对齐×3 / 颜色 / 嵌套± / 链接 ====== */
        FormatButtonGroup {
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                isActive = false,
                onClick = onAlignLeft,
                contentDescription = "左对齐"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatAlignCenter,
                isActive = false,
                onClick = onAlignCenter,
                contentDescription = "居中对齐"
            )
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                isActive = false,
                onClick = onAlignRight,
                contentDescription = "右对齐"
            )
            /** 颜色按钮（浮层 ColorStyleButton 同位）：打开文字/背景色板对话框 */
            FormatTextButton(
                label = "A",
                isActive = showColorStyleDialog,
                onClick = onOpenColorStyleDialog,
                contentDescription = "颜色"
            )
            /** 嵌套 +1（浮层 NestBlockButton 同位）：经宿主回调分支（BlockNote=nest / Compose=缩进） */
            FormatIconButton(
                imageVector = Icons.Default.FormatIndentIncrease,
                isActive = false,
                onClick = onIncreaseIndent,
                contentDescription = "增加缩进"
            )
            /** 嵌套 −1（浮层 UnNestBlockButton 同位） */
            FormatIconButton(
                imageVector = Icons.Default.FormatIndentDecrease,
                isActive = false,
                onClick = onDecreaseIndent,
                contentDescription = "减少缩进"
            )
            /** 插入链接（BlockNote 模式弹 URL 对话框 → format createLink） */
            FormatIconButton(
                imageVector = Icons.Default.Link,
                isActive = state.isLink,
                onClick = onInsertLink,
                contentDescription = "插入链接"
            )
        }

        ToolbarDivider()

        /** ====== 组三：Headings（+ 菜单 Headings 分类，Ri 同款图标） ====== */
        FormatButtonGroup {
            RiFormatButton("RiH1", onClick = { onTransform("heading1") }, contentDescription = "标题 1", enabled = onTransformEnabled)
            RiFormatButton("RiH2", onClick = { onTransform("heading2") }, contentDescription = "标题 2", enabled = onTransformEnabled)
            RiFormatButton("RiH3", onClick = { onTransform("heading3") }, contentDescription = "标题 3", enabled = onTransformEnabled)
            RiFormatButton("RiH4", onClick = { onTransform("heading4") }, contentDescription = "标题 4", enabled = onTransformEnabled)
            RiFormatButton("RiH5", onClick = { onTransform("heading5") }, contentDescription = "标题 5", enabled = onTransformEnabled)
            RiFormatButton("RiH6", onClick = { onTransform("heading6") }, contentDescription = "标题 6", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组四：Subheadings（可折叠标题，+ 菜单 Subheadings 分类） ====== */
        FormatButtonGroup {
            FormatTextButton("▸1", isActive = false, onClick = { onTransform("toggleHeading") }, contentDescription = "可折叠标题 1", enabled = onTransformEnabled)
            FormatTextButton("▸2", isActive = false, onClick = { onTransform("toggleHeading2") }, contentDescription = "可折叠标题 2", enabled = onTransformEnabled)
            FormatTextButton("▸3", isActive = false, onClick = { onTransform("toggleHeading3") }, contentDescription = "可折叠标题 3", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组五：Basic blocks（+ 菜单 Basic blocks 分类；图标与 + 菜单同款并按其顺序排列） ====== */
        FormatButtonGroup {
            /** Numbered List（+ 菜单同款 RiListOrdered；激活态跟随光标块类型） */
            RiFormatButton(
                "RiListOrdered",
                isActive = state.isOrderedList,
                onClick = onInsertOrderedList,
                contentDescription = "有序列表"
            )
            /** Bullet List */
            RiFormatButton(
                "RiListUnordered",
                isActive = state.isUnorderedList,
                onClick = onInsertUnorderedList,
                contentDescription = "无序列表"
            )
            /** Check List（复用原复选框回调与激活态） */
            RiFormatButton(
                "RiListCheck3",
                isActive = isCheckboxActive,
                onClick = onToggleCheckbox,
                contentDescription = "任务列表"
            )
            /** Paragraph（+ 菜单 Paragraph；BlockNote 模式转普通段落） */
            RiFormatButton(
                "RiText",
                onClick = onTransformParagraph,
                contentDescription = "普通段落",
                enabled = onTransformEnabled
            )
            /** Code Block（整块代码，toggle 语义） */
            RiFormatButton(
                "RiCodeBlock",
                onClick = { onTransform("codeBlock") },
                contentDescription = "代码块",
                enabled = onTransformEnabled
            )
            /** Divider（分割线：插入后点击可切样式） */
            RiFormatButton(
                "RiSubtractLine",
                onClick = onInsertDivider,
                contentDescription = "分割线"
            )
            /** Quote（+ 菜单 Quote） */
            RiFormatButton("RiQuoteText", onClick = { onTransform("quote") }, contentDescription = "引用", enabled = onTransformEnabled)
            /** Toggle List（+ 菜单 Toggle List） */
            RiFormatButton("RiPlayList2Fill", onClick = { onTransform("toggleList") }, contentDescription = "折叠列表", enabled = onTransformEnabled)
            /** Page Break（+ 菜单 Page Break） */
            RiFormatButton("RiFile2Line", onClick = { onTransform("pageBreak") }, contentDescription = "分页", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组六：Advanced（+ 菜单 Advanced 分类） ====== */
        FormatButtonGroup {
            RiFormatButton("RiTable2", onClick = { onTransform("table") }, contentDescription = "表格", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组七：Media（+ 菜单 Media 分类；宿主选择器 → Bridge 插入） ====== */
        FormatButtonGroup {
            RiFormatButton("RiImage2Fill", onClick = { onInsertMedia("image") }, contentDescription = "图片", enabled = onTransformEnabled)
            RiFormatButton("RiFilmLine", onClick = { onInsertMedia("video") }, contentDescription = "视频", enabled = onTransformEnabled)
            RiFormatButton("RiVolumeUpFill", onClick = { onInsertMedia("audio") }, contentDescription = "音频", enabled = onTransformEnabled)
            RiFormatButton("RiFile2Line", onClick = { onInsertMedia("file") }, contentDescription = "文件", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组八：Others（+ 菜单 Others 分类） ====== */
        FormatButtonGroup {
            RiFormatButton("RiEmotionFill", onClick = onOpenEmojiPicker, contentDescription = "表情", enabled = onTransformEnabled)
        }
    }
}`;

s = s.slice(0, rowStartIdx) + NEW_CONTENT + s.slice(endIdx + endMarker.length);
fs.writeFileSync(P, s);
console.log("Row content rebuilt. lines:", s.split("\n").length);
