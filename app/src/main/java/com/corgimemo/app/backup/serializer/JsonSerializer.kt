package com.corgimemo.app.backup.serializer

import com.corgimemo.app.backup.data.BackupContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * JSON 序列化器
 * 用于备份数据的序列化和反序列化
 */
object JsonSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 序列化备份数据为 JSON 字符串
     *
     * @param container 备份数据容器
     * @return JSON 字符串
     */
    fun serialize(container: BackupContainer): String {
        return json.encodeToString(container)
    }

    /**
     * 反序列化 JSON 字符串为备份数据
     *
     * @param jsonString JSON 字符串
     * @return 备份数据容器
     * @throws Exception 如果反序列化失败
     */
    fun deserialize(jsonString: String): BackupContainer {
        return json.decodeFromString<BackupContainer>(jsonString)
    }

    /**
     * 序列化为 ByteArray
     *
     * @param container 备份数据容器
     * @return ByteArray
     */
    fun serializeToBytes(container: BackupContainer): ByteArray {
        return serialize(container).toByteArray(Charsets.UTF_8)
    }

    /**
     * 从 ByteArray 反序列化
     *
     * @param bytes ByteArray
     * @return 备份数据容器
     * @throws Exception 如果反序列化失败
     */
    fun deserializeFromBytes(bytes: ByteArray): BackupContainer {
        return deserialize(bytes.toString(Charsets.UTF_8))
    }
}
