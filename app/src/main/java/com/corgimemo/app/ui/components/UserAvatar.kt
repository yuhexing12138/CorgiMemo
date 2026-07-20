package com.corgimemo.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.util.AvatarPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通用用户头像组件
 *
 * 设计目标：
 * 1. 跨页面统一头像入口（drawer 顶部 / "我的"页头卡 / 个人信息页）
 * 2. 明确视觉差异 — 圆形 + 主色背景 + 白色首字母大写，与柯基形象完全区分
 * 3. 支持三类头像来源：预设动作帧 / 用户上传 / 首字母占位
 *
 * avatarPath 字段三种取值：
 * - null              → 首字母占位（橙色主色背景 + 白色文字）
 * - "preset:xxx"      → 预设头像（surfaceVariant 背景 + 柯基动作帧循环动画）
 * - 绝对路径/URI      → 用户上传（私有目录路径用 BitmapFactory 直解码；其余走 Coil）
 *
 * 占位策略（avatarPath == null 时）：
 * - 取 `nickname` 首字符渲染
 * - 处理空串 / 纯空白 → "?"
 * - 处理 emoji 段 → 退回一个稳定的"🎉"占位
 * - 处理汉字 → 直接用原字
 * - 其它 → uppercase
 *
 * 主题适配：
 * - 占位 / 上传头像：背景色用 MaterialTheme.colorScheme.primary（自动跟随 6 色主题）
 * - 预设头像：背景色用 surfaceVariant（浅灰），与柯基图本身的暖色调形成对比
 * - 描边色用 MaterialTheme.colorScheme.surface（与卡片背景区分即可）
 *
 * @param nickname 用户昵称（用于提取首字母占位）
 * @param avatarPath 头像文件绝对路径 / content URI / "preset:xxx" 预设标识；null 时回退到首字母占位
 * @param preloadedBitmap 预加载的 Bitmap（推荐从 ViewModel 传入，可避免 Compose 内 IO 阻塞）
 * @param size 头像直径（dp）
 * @param onClick 头像点击回调；null 表示不可点
 * @param onAvatarLongClick 头像长按回调（本期预留，不接；未来接"更换头像"长按入口）
 */
@Composable
fun UserAvatar(
    nickname: String,
    avatarPath: String?,
    size: Dp,
    preloadedBitmap: android.graphics.Bitmap? = null,
    onClick: (() -> Unit)? = null,
    onAvatarLongClick: (() -> Unit)? = null
) {
    // 预设头像：用 surfaceVariant 浅灰底衬托柯基图
    // 占位 / 用户上传：保留 primary 主色底
    val isPreset = AvatarPath.isPreset(avatarPath)
    val containerColor = if (isPreset) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    // 基础样式：圆形 + 动态背景色 + surface 描边
    val baseModifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(containerColor)
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
        if (isPreset) {
            // 预设头像：查表找到 AnimationType + fps，用 FrameAnimation 循环播放柯基动作帧
            // 找不到对应预设时（如历史脏数据）回退到首字母占位
            val presetKey = AvatarPath.extractPresetKey(avatarPath)
            val preset = corgiPresets.firstOrNull { it.key == presetKey }
            if (preset != null) {
                FrameAnimation(
                    animationType = preset.animationType,
                    fps = preset.fps,
                    isLooping = true,
                    isPlaying = true,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                AvatarInitialText(nickname = nickname, size = size)
            }
        } else if (avatarPath != null) {
            // 用户上传头像：
            // 优先级 1：caller 预加载的 Bitmap（避免 Compose 内 IO 与 Coil 兼容问题）
            // 优先级 2：私有目录绝对路径，LaunchedEffect 异步 BitmapFactory 解码
            // 优先级 3：其他路径（content:// / http），Coil SubcomposeAsyncImage
            // 加载失败统一 fallback 到首字母占位
            val bitmap = preloadedBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (avatarPath.startsWith("/")) {
                var loadedBitmap by remember(avatarPath) {
                    mutableStateOf<android.graphics.Bitmap?>(null)
                }
                LaunchedEffect(avatarPath) {
                    loadedBitmap = withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(avatarPath) }.getOrNull()
                    }
                }
                val bm = loadedBitmap
                if (bm != null) {
                    Image(
                        bitmap = bm.asImageBitmap(),
                        contentDescription = "用户头像",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AvatarInitialText(nickname = nickname, size = size)
                }
            } else {
                val context = LocalContext.current
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = { AvatarInitialText(nickname = nickname, size = size) },
                    error = { AvatarInitialText(nickname = nickname, size = size) }
                )
            }
        } else {
            // 首字母占位徽章
            AvatarInitialText(nickname = nickname, size = size)
        }
    }
}

/**
 * 首字母占位文字（统一处理 emoji / 汉字 / ASCII / 空串）
 *
 * @param nickname 用户昵称
 * @param size     头像直径，用于计算字号
 */
@Composable
private fun AvatarInitialText(nickname: String, size: Dp) {
    Text(
        text = pickInitial(nickname),
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = (size.value * 0.42f).sp,
        fontWeight = FontWeight.Bold
    )
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
