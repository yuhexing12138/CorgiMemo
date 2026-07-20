package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * 通用用户头像组件
 *
 * 设计目标：
 * 1. 跨页面统一头像入口（drawer 顶部 / "我的"页头卡 / 后续列表项）
 * 2. 明确视觉差异 — 圆形 + 主色背景 + 白色首字母大写，与柯基形象完全区分
 * 3. 上传功能预留 — 当 `avatarPath` 非空时切换到 Coil AsyncImage 加载
 *
 * 占位策略（avatarPath == null 时）：
 * - 取 `nickname` 首字符渲染
 * - 处理空串 / 纯空白 → "?"
 * - 处理 emoji 段 → 退回一个稳定的"🎉"占位
 * - 处理汉字 → 直接用原字
 * - 其它 → uppercase
 *
 * 主题适配：
 * - 背景色用 MaterialTheme.colorScheme.primary（自动跟随 6 色主题）
 * - 描边色用 MaterialTheme.colorScheme.surface（与卡片背景区分即可）
 *
 * @param nickname 用户昵称（用于提取首字母占位）
 * @param avatarPath 头像文件绝对路径 / content URI；null 时回退到首字母占位
 * @param size 头像直径（dp）
 * @param onClick 头像点击回调；null 表示不可点
 * @param onAvatarLongClick 头像长按回调（本期预留，不接；未来接"更换头像"长按入口）
 */
@Composable
fun UserAvatar(
    nickname: String,
    avatarPath: String?,
    size: Dp,
    onClick: (() -> Unit)? = null,
    onAvatarLongClick: (() -> Unit)? = null
) {
    // 基础样式：圆形 + 主色背景 + surface 描边
    val baseModifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
        .border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape
        )

    // 点击反馈：有 onClick 时叠加 clickable
    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(
            onClick = onClick
            // 注：onAvatarLongClick 暂未实现 combinedClickable，预留接口后续接
        )
    } else {
        baseModifier
    }

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center
    ) {
        if (avatarPath != null) {
            // 未来分支：上传功能接入后启用
            // - Coil AsyncImage 加载本地路径或 content URI
            // - 圆形裁剪，ContentScale.Crop 避免变形
            AsyncImage(
                model = avatarPath,
                contentDescription = "用户头像",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // 当前分支：首字母占位徽章
            Text(
                text = pickInitial(nickname),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 提取昵称首字符（处理空 / emoji / 汉字 / ASCII）
 *
 * 设计依据：
 * - 用 `codePointAt(0)` 而非 `first()`，避免 Kotlin String 的 UTF-16 代理对拆分
 *   （例如 emoji "🐕" 是 2 个 char，高位代理 + 低位代理；按 char 取会拿到乱码）
 * - emoji 段判定覆盖两个主要区段：
 *   - 0x1F000-0x1FFFF：Miscellaneous Symbols and Pictographs / Emoticons / Transport / ...
 *   - 0x2600-0x27BF  ：Misc Symbols / Dingbats
 * - 汉字（0x4E00-0x9FFF）直接用原字，保持视觉一致性
 * - ASCII 转大写，避免"abc"和"Abc"出现不同占位
 *
 * 内部可见 — 单元测试时可在同包内调用。
 *
 * @param nickname 原始昵称
 * @return 单字符占位文本
 */
internal fun pickInitial(nickname: String): String {
    val trimmed = nickname.trim()
    if (trimmed.isEmpty()) return "?"

    val cp = trimmed.codePointAt(0)
    val ch = String(Character.toChars(cp))

    // emoji 段（避免把 emoji 当首字符渲染 — 视觉上与柯基混淆）
    if (cp in 0x1F000..0x1FFFF || cp in 0x2600..0x27BF) {
        return "🎉"
    }
    // 汉字直接用原字
    if (cp in 0x4E00..0x9FFF) {
        return ch
    }
    // 其它（ASCII / 拉丁文 / 阿拉伯文 / 藏文 ...）→ 转大写
    return ch.uppercase()
}
