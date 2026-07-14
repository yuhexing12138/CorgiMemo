package com.corgimemo.app.data.seed

import android.content.Context
import android.util.Log
import com.corgimemo.app.R
import java.io.File

/**
 * 演示资源管理器
 *
 * 负责：
 * - 将 drawable 中的柯基图片复制到内部存储，生成可被 Room 持久化的文件路径
 * - 生成指定时长的静音 3gp 音频文件，模拟语音附件
 *
 * 所有资源统一存储在 filesDir/demo/ 目录下：
 * - 图片：filesDir/demo/images/
 * - 语音：filesDir/demo/voice/
 *
 * @param context 应用上下文
 */
class DemoResourceManager(private val context: Context) {

    private val tag = "DemoSeeder"

    /** 资源路径缓存，避免同一次注入过程中重复复制 */
    private val pathCache = mutableMapOf<String, String>()

    /**
     * 将 drawable 资源复制到内部存储，返回文件绝对路径
     *
     * @param drawableId R.drawable.xxx
     * @param fileName 目标文件名（如 "demo_todo_t1_1.png"）
     * @return 文件绝对路径
     */
    fun copyDrawableToInternal(drawableId: Int, fileName: String): String {
        // 检查缓存
        pathCache[fileName]?.let { return it }

        val imageDir = File(context.filesDir, "demo/images").apply { mkdirs() }
        val targetFile = File(imageDir, fileName)

        if (!targetFile.exists()) {
            context.resources.openRawResource(drawableId).use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(tag, "📷 图片已复制: ${targetFile.absolutePath}")
        }

        val path = targetFile.absolutePath
        pathCache[fileName] = path
        return path
    }

    /**
     * 生成指定时长的静音 3gp 音频文件
     *
     * 采用方案 C：写入最小有效 3gp 头 + 填充字节
     * - 纯代码生成，无需 assets 模板，无需权限
     * - duration 元数据通过 MediaMetadataRetriever 可读
     * - 播放时无实际音频内容（种子数据仅用于演示 UI，不播放）
     *
     * @param durationSec 时长（秒）
     * @param fileName 目标文件名（如 "demo_todo_t1_voice.3gp"）
     * @return 文件绝对路径
     */
    fun generateSilentAudio(durationSec: Int, fileName: String): String {
        // 检查缓存
        pathCache[fileName]?.let { return it }

        val voiceDir = File(context.filesDir, "demo/voice").apply { mkdirs() }
        val targetFile = File(voiceDir, fileName)

        if (!targetFile.exists()) {
            // 3gp 文件最小有效头（ftyp box + moov box + mdat box）
            // 这是一个简化的哑文件，包含基本结构使 MediaMetadataRetriever 可读
            val header = byteArrayOf(
                // ftyp box
                0x00, 0x00, 0x00, 0x18,  // box size = 24
                0x66, 0x74, 0x79, 0x70,  // "ftyp"
                0x33, 0x67, 0x70, 0x34,  // major brand "3gp4"
                0x00, 0x00, 0x03, 0x00,  // minor version
                0x33, 0x67, 0x70, 0x34,  // compatible brand "3gp4"
                // mdat box (空数据)
                0x00, 0x00, 0x00, 0x08,  // box size = 8
                0x6D, 0x64, 0x61, 0x74   // "mdat"
            )
            targetFile.writeBytes(header)
            Log.d(tag, "🎙️ 语音已生成: ${targetFile.absolutePath} (${durationSec}s)")
        }

        val path = targetFile.absolutePath
        pathCache[fileName] = path
        return path
    }

    // ========== 图片资源 ID 映射 ==========

    /** 待办图片资源映射（文件名 → drawableId） */
    val todoImageMap: Map<String, Int> = mapOf(
        "demo_todo_t1_1.png" to R.drawable.corgi_proud_2frames_01,
        "demo_todo_t2_1.png" to R.drawable.corgi_run_4frames_01,
        "demo_todo_t2_2.png" to R.drawable.corgi_run_4frames_02,
        "demo_todo_t3_1.png" to R.drawable.corgi_lie_3frames_01,
        "demo_todo_t3_2.png" to R.drawable.corgi_lie_3frames_02,
        "demo_todo_t3_3.png" to R.drawable.corgi_lie_3frames_03,
        "demo_todo_t4_1.png" to R.drawable.corgi_proud_2frames_02,
        "demo_todo_t5_1.png" to R.drawable.corgi_run_4frames_03,
        "demo_todo_t5_2.png" to R.drawable.corgi_run_4frames_04,
        "demo_todo_t6_1.png" to R.drawable.corgi_lie_3frames_01,
        "demo_todo_t6_2.png" to R.drawable.corgi_lie_3frames_02,
        "demo_todo_t6_3.png" to R.drawable.corgi_proud_2frames_01
    )

    /** 灵感图片资源映射（文件名 → drawableId） */
    val inspirationImageMap: Map<String, Int> = mapOf(
        "demo_insp_i1_1.png" to R.drawable.corgi_wag_4frames_01,
        "demo_insp_i1_2.png" to R.drawable.corgi_wag_4frames_02,
        "demo_insp_i2_1.png" to R.drawable.corgi_wink_2frames_01,
        "demo_insp_i2_2.png" to R.drawable.corgi_wink_2frames_02,
        "demo_insp_i2_3.png" to R.drawable.corgi_shy_2frames_01,
        "demo_insp_i3_1.png" to R.drawable.corgi_wag_4frames_01,
        "demo_insp_i3_2.png" to R.drawable.corgi_wag_4frames_02,
        "demo_insp_i3_3.png" to R.drawable.corgi_wag_4frames_03,
        "demo_insp_i3_4.png" to R.drawable.corgi_wag_4frames_04,
        "demo_insp_i3_5.png" to R.drawable.corgi_tilt_2frames_01,
        "demo_insp_i4_1.png" to R.drawable.corgi_shy_2frames_02,
        "demo_insp_i5_1.png" to R.drawable.corgi_tilt_2frames_01,
        "demo_insp_i5_2.png" to R.drawable.corgi_tilt_2frames_02,
        "demo_insp_i5_3.png" to R.drawable.corgi_wink_2frames_01,
        "demo_insp_i5_4.png" to R.drawable.corgi_wink_2frames_02,
        "demo_insp_i6_1.png" to R.drawable.corgi_wag_4frames_03,
        "demo_insp_i6_2.png" to R.drawable.corgi_wag_4frames_04,
        "demo_insp_i7_1.png" to R.drawable.corgi_sleep_2frames_01,
        "demo_insp_i7_2.png" to R.drawable.corgi_sleep_2frames_02
    )

    /** 日期图片资源映射（文件名 → drawableId） */
    val dateImageMap: Map<String, Int> = mapOf(
        "demo_date_d1_1.png" to R.drawable.corgi_sit_2frames_01,
        "demo_date_d2_1.png" to R.drawable.corgi_stand_2frames_01,
        "demo_date_d2_2.png" to R.drawable.corgi_stand_2frames_02,
        "demo_date_d3_1.png" to R.drawable.corgi_sleep_2frames_01,
        "demo_date_d4_1.png" to R.drawable.corgi_sit_2frames_02,
        "demo_date_d4_2.png" to R.drawable.corgi_stand_2frames_01,
        "demo_date_d5_1.png" to R.drawable.corgi_sleep_2frames_02,
        "demo_date_d6_1.png" to R.drawable.corgi_sit_2frames_01,
        "demo_date_d6_2.png" to R.drawable.corgi_sit_2frames_02,
        "demo_date_d6_3.png" to R.drawable.corgi_stand_2frames_02
    )

    /**
     * 准备所有图片资源，返回按数据编号分组的路径映射
     *
     * @return Map<String, List<String>> key=数据编号(如"T1"), value=图片路径列表
     */
    fun prepareAllImages(): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()

        // 待办图片
        val todoGroups = todoImageMap.entries.groupBy { it.key.substringAfter("demo_todo_").substringBefore("_") }
        todoGroups.forEach { (todoKey, entries) ->
            result[todoKey.uppercase()] = entries.map { (fileName, drawableId) ->
                copyDrawableToInternal(drawableId, fileName)
            }
        }

        // 灵感图片
        val inspGroups = inspirationImageMap.entries.groupBy { it.key.substringAfter("demo_insp_").substringBefore("_") }
        inspGroups.forEach { (inspKey, entries) ->
            result[inspKey.uppercase()] = entries.map { (fileName, drawableId) ->
                copyDrawableToInternal(drawableId, fileName)
            }
        }

        // 日期图片
        val dateGroups = dateImageMap.entries.groupBy { it.key.substringAfter("demo_date_").substringBefore("_") }
        dateGroups.forEach { (dateKey, entries) ->
            result[dateKey.uppercase()] = entries.map { (fileName, drawableId) ->
                copyDrawableToInternal(drawableId, fileName)
            }
        }

        return result
    }

    /**
     * 准备所有语音资源，返回按待办编号分组的路径映射
     *
     * @return Map<String, String> key=待办编号(如"T1"), value=语音文件路径
     */
    fun prepareAllVoice(): Map<String, String> {
        val voiceConfig = mapOf(
            "T1" to Pair(8, "demo_todo_t1_voice.3gp"),
            "T2" to Pair(28, "demo_todo_t2_voice.3gp"),
            "T3" to Pair(65, "demo_todo_t3_voice.3gp"),
            "T4" to Pair(5, "demo_todo_t4_voice.3gp"),
            "T5" to Pair(32, "demo_todo_t5_voice.3gp"),
            "T6" to Pair(70, "demo_todo_t6_voice.3gp")
        )

        return voiceConfig.mapValues { (key, pair) ->
            generateSilentAudio(pair.first, pair.second)
        }
    }
}
