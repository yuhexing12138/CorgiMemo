package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.util.AvatarPath

/**
 * 柯基动作姿态预设（取自 drawable 中的帧动画）
 *
 * 设计要点：
 * - `key` 既是 UI 内部唯一标识，也是 [AvatarPath] 中的预设后缀
 *   （如 `key="corgi_sit"` → `avatarPath="preset:corgi_sit"`）
 * - `animationType` 复用项目已有的 [AnimationType] 枚举，避免手写 drawable 名映射
 *   （特别注意：`corgi_roll` 实际 drawable 命名为 `corgi_roll_4framesl_xx`，
 *   这是历史 typo，由 [com.corgimemo.app.animation.AnimationResourceManager] 统一处理，
 *   调用方不需关心）
 * - `fps` 字段替代原计划的 `frameDurationMs`：
 *   因为项目已有的 [FrameAnimation] 帧动画组件使用 `fps` 而非毫秒间隔
 *   （详见 `FrameAnimation.kt` 实际签名）
 *
 * @param key          唯一 key，同时也是 avatarPath 的预设后缀
 * @param displayName  中文显示名（UI 标签）
 * @param description  短描述（可扩展 tooltip/语义说明，本期暂不展示）
 * @param animationType 关联的动画类型枚举（用于 FrameAnimation 渲染）
 * @param fps          帧率（FPS），范围 2~7 适配各动作节奏
 */
data class CorgiPreset(
    val key: String,
    val displayName: String,
    val description: String,
    val animationType: AnimationType,
    val fps: Int
)

/**
 * 项目中所有可用的柯基动作预设（13 个）
 *
 * 顺序按"日常 → 情绪"自然分组：基础姿态（坐/站/趴/跑/睡）
 * → 表情动作（眨眼/摇尾/歪头）→ 情绪（难过/骄傲/害羞/焦虑/打滚）
 */
val corgiPresets: List<CorgiPreset> = listOf(
    CorgiPreset("corgi_sit",    "坐下",  "安静坐姿", AnimationType.SIT,   fps = 4),
    CorgiPreset("corgi_stand",  "站立",  "活泼站立", AnimationType.STAND, fps = 4),
    CorgiPreset("corgi_lie",    "趴卧",  "舒适趴卧", AnimationType.LIE,   fps = 3),
    CorgiPreset("corgi_run",    "奔跑",  "欢快奔跑", AnimationType.RUN,   fps = 7),
    CorgiPreset("corgi_sleep",  "睡觉",  "瞌睡打盹", AnimationType.SLEEP, fps = 2),
    CorgiPreset("corgi_wink",   "眨眼",  "俏皮眨眼", AnimationType.WINK,  fps = 4),
    CorgiPreset("corgi_wag",    "摇尾",  "开心摇尾", AnimationType.WAG,   fps = 6),
    CorgiPreset("corgi_tilt",   "歪头",  "歪头卖萌", AnimationType.TILT,  fps = 4),
    CorgiPreset("corgi_sad",    "难过",  "垂头丧气", AnimationType.SAD,   fps = 4),
    CorgiPreset("corgi_proud",  "骄傲",  "昂首挺胸", AnimationType.PROUD, fps = 4),
    CorgiPreset("corgi_shy",    "害羞",  "害羞躲闪", AnimationType.SHY,   fps = 4),
    CorgiPreset("corgi_worry",  "焦虑",  "焦虑踱步", AnimationType.WORRY, fps = 4),
    CorgiPreset("corgi_roll",   "打滚",  "开心打滚", AnimationType.ROLL,  fps = 5)
)

/**
 * 3 列动态网格（复用 [FrameAnimation] 循环播放柯基动作帧）
 *
 * 用途：在"个人信息 → 更换头像 → 预设头像库"中展示全部 13 个可选项
 * 选中后通过 [onPresetSelect] 回调将 `key` 抛给调用方，
 * 调用方负责将 key 转成 [AvatarPath.toPresetPath] 存入数据库
 *
 * @param selectedKey    当前选中的预设 key（null 表示无）
 * @param onPresetSelect 选中回调，参数为预设 key
 * @param modifier       外层修饰符
 */
@Composable
fun PresetAvatarGrid(
    selectedKey: String?,
    onPresetSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(corgiPresets, key = { it.key }) { preset ->
            PresetAvatarItem(
                preset = preset,
                isSelected = selectedKey == preset.key,
                onClick = { onPresetSelect(preset.key) }
            )
        }
    }
}

/**
 * 单个预设头像项（圆形缩略图 + 中文标签）
 *
 * 视觉规范：
 * - 圆形遮罩 + 选中时主色 3dp 边框，未选中时 30% 透明 outline 1dp
 * - 圆形内复用 [FrameAnimation] 循环播放该动作的 drawable 帧
 * - 下方 4dp 间隔 + labelSmall 字号的中文名
 *
 * @param preset     预设数据
 * @param isSelected 是否被选中
 * @param onClick    点击回调
 */
@Composable
private fun PresetAvatarItem(
    preset: CorgiPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 复用项目已有 FrameAnimation（com.corgimemo.app.animation）
            // 该重载基于 AnimationType 枚举获取帧 ID，编译期类型安全
            FrameAnimation(
                animationType = preset.animationType,
                fps = preset.fps,
                isLooping = true,
                isPlaying = true,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
