package com.corgimemo.app.util

/**
 * 标签编解码工具函数
 *
 * 提供标签 JSON 字符串与 List<String> 之间的双向转换，
 * 统一替代各 ViewModel 中重复的 decodeTags 实现。
 */
object TagUtils {

    /**
     * 解码标签 JSON 字符串为列表
     *
     * 支持格式：["标签1","标签2"] 或空字符串
     *
     * @param tagsJson JSON 字符串
     * @return 标签列表，解析失败返回空列表
     */
    fun decodeTags(tagsJson: String): List<String> {
        if (tagsJson.isBlank()) return emptyList()
        return try {
            tagsJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 编码标签列表为 JSON 字符串
     *
     * 输出格式：["标签1","标签2"]
     *
     * @param tags 标签列表
     * @return JSON 字符串
     */
    fun encodeTags(tags: List<String>): String {
        if (tags.isEmpty()) return "[]"
        return buildString {
            append("[")
            tags.forEachIndexed { index, tag ->
                if (index > 0) append(",")
                append("\"$tag\"")
            }
            append("]")
        }
    }

    /**
     * 解码图片路径 JSON 字符串为列表
     *
     * @param pathsJson JSON 字符串
     * @return 路径列表，解析失败返回空列表
     */
    fun decodePaths(pathsJson: String): List<String> {
        return decodeTags(pathsJson) // 格式与标签相同，复用逻辑
    }

    /**
     * 编码图片路径列表为 JSON 字符串
     *
     * @param paths 路径列表
     * @return JSON 字符串
     */
    fun encodePaths(paths: List<String>): String {
        return encodeTags(paths) // 格式与标签相同，复用逻辑
    }
}
