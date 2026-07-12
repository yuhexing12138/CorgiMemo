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
     * **重要**：必须使用 org.json.JSONArray 真正的 JSON 解析器，
     * 因为路径中的 `/` 在 JSON 中会被转义为 `\/`（例如 org.json.JSONArray 默认转义）。
     * 手写 removeSurrounding("\"") 不会反转义这些字符，导致路径变成 `\/data\/...`，
     * File.exists() 返回 false，Coil 加载失败。
     *
     * @param pathsJson JSON 字符串
     * @return 路径列表，解析失败返回空列表
     */
    fun decodePaths(pathsJson: String): List<String> {
        if (pathsJson.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(pathsJson)
            // 防御性反转义：部分 Android 版本的 org.json.JSONArray.getString()
            // 不会将 JSON 中的 \/ 反转为 /，导致路径变为 \/data\/user\/...
            // 文件查找失败，Coil 加载抛出 FileNotFoundException
            (0 until arr.length()).map { arr.getString(it).replace("\\/", "/") }
        } catch (e: Exception) {
            // 兼容旧数据（非标准 JSON）：回退到手写解析
            try {
                pathsJson
                    .removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .map { it.replace("\\/", "/") }  // 手动反转义
                    .filter { it.isNotBlank() }
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 编码图片路径列表为 JSON 字符串
     *
     * 使用 org.json.JSONArray 进行标准 JSON 序列化，
     * 自动处理路径中的特殊字符转义（/, \, " 等）。
     *
     * @param paths 路径列表
     * @return JSON 字符串
     */
    fun encodePaths(paths: List<String>): String {
        if (paths.isEmpty()) return "[]"
        return org.json.JSONArray(paths).toString()
    }
}
