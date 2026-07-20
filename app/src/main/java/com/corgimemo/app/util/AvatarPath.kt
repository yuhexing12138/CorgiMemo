package com.corgimemo.app.util

/**
 * 头像路径工具
 *
 * avatarPath 字段三种取值：
 * 1. null              → 首字母占位（UserAvatar 内部渲染）
 * 2. "preset:xxx"      → 预设头像（drawable 柯基动作帧）
 * 3. 绝对路径（/data/.../files/avatars/xxx.png）→ 用户上传
 *
 * 预设识别使用路径前缀 "preset:"，避免与绝对路径混淆
 * （绝对路径不可能以 "preset:" 开头）
 */
object AvatarPath {

    /** 预设路径前缀 */
    const val PRESET_PREFIX = "preset:"

    /** 预设格式示例："preset:corgi_sit" / "preset:corgi_stand" */
    fun toPresetPath(presetKey: String): String = "$PRESET_PREFIX$presetKey"

    /** 是否为预设头像 */
    fun isPreset(avatarPath: String?): Boolean =
        avatarPath?.startsWith(PRESET_PREFIX) == true

    /** 提取预设 key（如 "preset:corgi_sit" → "corgi_sit"）；非预设返回 null */
    fun extractPresetKey(avatarPath: String?): String? {
        if (!isPreset(avatarPath)) return null
        return avatarPath!!.removePrefix(PRESET_PREFIX)
    }

    /** 是否为用户上传头像（绝对路径） */
    fun isUserUploaded(avatarPath: String?): Boolean =
        avatarPath != null && !isPreset(avatarPath) && avatarPath.startsWith("/")
}
