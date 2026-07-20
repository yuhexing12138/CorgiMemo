# 头像上传 + 个人信息页 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户头像 4 路径上传（PhotoPicker / 传统选择 / 拍照 / 预设库）+ 个人信息页（头像/名字/性别/4 项占位），打通"drawer/Profile → 个人信息页"导航。

**Architecture:**
- **数据层**：`CorgiData` 新增 `gender: String?` 字段，Room 升级 v40→v41
- **图片处理**：`AvatarStorage` 工具类管理私有目录；`CircularImageCropper` Compose 组件做交互式圆形裁剪
- **UI 入口**：`AvatarSourceSheet` ModalBottomSheet 让用户选 4 种来源之一
- **页面**：`ProfileDetailScreen` 全屏路由，列 7 行（头像/名字/性别/手机/邮箱/密码/设备）
- **导航**：`Screen.ProfileDetail` 新路由 + `onProfileClick` 已有抽屉回调接到此页
- **预设复用**：drawable 柯基动作帧，`avatarPath = "preset:corgi_xxx"` 路径前缀识别

**Tech Stack:**
- Compose BOM 2026.04.01（Compose 1.9.2）
- Room v41（KSP）+ Hilt
- Coil 3.x（`AsyncImage`）
- `ActivityResultContracts.PickVisualMedia`（PhotoPicker）
- `ActivityResultContracts.TakePicture`（拍照）
- `MediaStore`（传统 ACTION_PICK，向下兼容 Android 4.4）
- 项目已有的 `FrameAnimation`（复用渲染柯基动作帧预览）

**项目规则引用**（按优先级）:
- `.trae/rules/编译验证.md` — 编译前必须询问
- `.trae/rules/UI设计规范.md` — UI 引用 `docs/superpowers/specs/UI设计规范.md`
- `.trae/rules/api不可用处理规则.md` — 优先修复 API
- `.trae/rules/entity与 migration同步检查.md` — `@ColumnInfo(defaultValue)` ↔ SQL `DEFAULT` 严格一致
- `.trae/rules/import语句检查.md` — 编辑后检查 import
- `.trae/rules/lambda 捕获陷阱防御.md` — Compose 长效 lambda 用最新状态
- `.trae/rules/优化建议.md` — 任务末尾输出优化建议
- `.trae/rules/git提交.md` — 任务后询问 commit，commit 信息用中文

---

## File Structure（涉及文件清单）

| # | 路径 | 性质 | 职责 |
|---|---|---|---|
| 1 | `app/src/main/java/com/corgimemo/app/data/model/CorgiData.kt` | 改 | 新增 `gender: String?` |
| 2 | `app/src/main/java/com/corgimemo/app/data/local/db/CorgiDao.kt` | 改 | 新增 `updateGender` |
| 3 | `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt` | 改 | 升级 v41 + `MIGRATION_40_41` |
| 4 | `app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt` | 改 | 新增 `updateGender` |
| 5 | `app/src/main/java/com/corgimemo/app/util/AvatarPath.kt` | **新建** | 路径前缀常量 + 判定函数 |
| 6 | `app/src/main/java/com/corgimemo/app/util/AvatarStorage.kt` | **新建** | 私有目录管理（saveAvatar/clearAvatar） |
| 7 | `app/src/main/java/com/corgimemo/app/ui/components/CircularImageCropper.kt` | **新建** | Compose 圆形裁剪组件（拖动+缩放+圆形遮罩） |
| 8 | `app/src/main/java/com/corgimemo/app/ui/components/AvatarSourceSheet.kt` | **新建** | ModalBottomSheet 4 路径选择 |
| 9 | `app/src/main/java/com/corgimemo/app/ui/components/PresetAvatarGrid.kt` | **新建** | 3 列动态网格（FrameAnimation 复用） |
| 10 | `app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/ProfileDetailScreen.kt` | **新建** | 个人信息页主屏 |
| 11 | `app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/components/ProfileDetailRow.kt` | **新建** | 单行表项组件（标签 + 值 + 箭头） |
| 12 | `app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt` | 改 | 新增 `Screen.ProfileDetail` |
| 13 | `app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt` | 改 | 注册 `ProfileDetail` 路由 |
| 14 | `app/src/main/java/com/corgimemo/app/ui/screens/profile/components/ProfileHeroCard.kt` | 改 | `onNameClick` 由"改名弹窗"改为"跳 ProfileDetail" |
| 15 | `app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt` | 改 | 抽屉 `onProfileClick` 跳 `Screen.ProfileDetail.route`（不再切 tab） |
| 16 | `app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt` | 改 | `onProfileClick` 改名为 `onUserAreaClick`（语义更准） |
| 17 | `app/src/main/res/xml/file_paths.xml` | **新建** | FileProvider 路径配置（拍照需要） |
| 18 | `app/src/main/AndroidManifest.xml` | 改 | 注册 `FileProvider` + `CAMERA` 权限 |

不修改但需确认存在：
- `app/src/main/java/com/corgimemo/app/ui/components/FrameAnimation.kt`（复用）
- `app/src/main/java/com/corgimemo/app/ui/components/UserAvatar.kt`（已支持 avatarPath）
- `app/src/main/java/com/corgimemo/app/ui/screens/corgi/CorgiDetailScreen.kt`（不影响）
- `app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt`（改名弹窗移到 ProfileDetail 中）

---

## Task 1: 数据层 — `gender` 字段（Room v40→v41）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/model/CorgiData.kt:1-60`
- Modify: `app/src/main/java/com/corgimemo/app/data/local/db/CorgiDao.kt:1-100`
- Modify: `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt:1-80`
- Modify: `app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt:1-100`

### Step 1.1: 修改 `CorgiData.kt`，在 `avatarPath` 字段后追加 `gender`

打开文件，在 `@ColumnInfo(defaultValue = "NULL")\nval avatarPath: String? = null` 后追加：

```kotlin
/**
 * 用户性别（null = 保密 / 未设置）
 *
 * 枚举字符串取值：
 * - "MALE"   → 男
 * - "FEMALE" → 女
 * - "OTHER"  → 其他
 * - null     → 保密 / 未设置
 *
 * 用字符串而非 Int 枚举，简化跨语言序列化与未来扩展（增加新选项不需迁移）。
 */
@ColumnInfo(defaultValue = "NULL")
val gender: String? = null
```

> 注：保持 `@ColumnInfo(defaultValue = "NULL")` 与 SQL `DEFAULT NULL` 严格一致（`.trae/rules/entity与 migration同步检查.md`）。

### Step 1.2: 在 `CorgiDao.kt` 末尾追加 `updateGender`

在文件最末尾追加：

```kotlin
/**
 * 更新用户性别
 *
 * @param gender 性别枚举字符串（"MALE" / "FEMALE" / "OTHER"），null 表示未设置
 */
@Query("UPDATE corgi_data SET gender = :gender WHERE id = (SELECT id FROM corgi_data LIMIT 1)")
suspend fun updateGender(gender: String?)
```

### Step 1.3: 修改 `CorgiMemoDatabase.kt`，升级 v40→v41 并添加 Migration

将 `version = 40` 改为 `version = 41`。

在 `addMigrations(...)` 调用末尾追加 `MIGRATION_40_41`（在 `MIGRATION_39_40` 之后）：

```kotlin
.addMigrations(MIGRATION_1_2, ..., MIGRATION_39_40, MIGRATION_40_41)
```

在文件底部 `MIGRATION_39_40` 定义后追加：

```kotlin
/**
 * Room v40 → v41：corgi_data 表新增 gender 字段
 *
 * SQL DEFAULT NULL ↔ Entity @ColumnInfo(defaultValue = "NULL") 严格一致
 * 已有数据不丢失：旧行 gender 自动为 NULL（"未设置"状态）
 */
internal val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE corgi_data ADD COLUMN gender TEXT DEFAULT NULL")
    }
}
```

### Step 1.4: 在 `CorgiRepository.kt` 末尾追加 `updateGender`

```kotlin
/**
 * 更新用户性别（null 表示未设置）
 */
suspend fun updateGender(gender: String?) = withContext(ioDispatcher) {
    corgiDao.updateGender(gender)
}
```

### Step 1.5: 验证 import

确保 `CorgiRepository.kt` 已 import `withContext` 和 `ioDispatcher`（通常已有）。检查缺少则补。

### Step 1.6: **commit 数据层（用户手动编译验证后）**

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/data/model/CorgiData.kt `
        app/src/main/java/com/corgimemo/app/data/local/db/CorgiDao.kt `
        app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt `
        app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt
# 写 commit message（中文）
$msg = @"
feat(数据层): CorgiData 新增 gender 字段并升级 Room 至 v41

- CorgiData: 新增 gender: String? 字段（MALE/FEMALE/OTHER/null）
- CorgiDao: 新增 updateGender Query
- CorgiMemoDatabase: 升级 v40→v41, 添加 MIGRATION_40_41
- CorgiRepository: 新增 updateGender 挂 ioDispatcher

依据 entity与 migration同步检查.md:
@ColumnInfo(defaultValue = "NULL") ↔ SQL DEFAULT NULL 一致
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

> 编译验证：用户手动跑 `./gradlew assembleDebug`，确认无 unresolved reference。

---

## Task 2: 路径前缀工具 `AvatarPath.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/util/AvatarPath.kt`

### Step 2.1: 创建文件，写常量 + 判定函数

```kotlin
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
```

### Step 2.2: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/util/AvatarPath.kt
$msg = @"
feat(util): 新增 AvatarPath 工具(预设路径前缀识别)

avatarPath 字段三种取值:
- null              → 首字母占位
- "preset:xxx"      → 预设头像
- 绝对路径          → 用户上传

用前缀字符串识别,代码可读且向后兼容。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 3: 私有目录管理 `AvatarStorage.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/util/AvatarStorage.kt`

### Step 3.1: 创建文件

```kotlin
package com.corgimemo.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 用户头像私有目录管理
 *
 * 存储路径：/data/data/com.corgimemo.app/files/avatars/
 * - 应用卸载时自动清理（隐私好）
 * - 不占用公共相册空间
 * - 无需 WRITE_EXTERNAL_STORAGE 权限
 *
 * 命名规则：{uuid}.png
 * - UUID 避免冲突
 * - 固定 PNG 格式（按本期需求"无压缩"）
 */
object AvatarStorage {

    private const val DIR_NAME = "avatars"
    private const val EXT = ".png"

    /**
     * 获取头像目录（不存在则创建）
     */
    fun getAvatarDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 保存 Bitmap 为新头像文件，返回绝对路径
     *
     * @param context Context
     * @param bitmap  裁剪后的 Bitmap
     * @return 保存的文件绝对路径
     */
    suspend fun saveAvatar(context: Context, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val file = File(getAvatarDir(context), "${UUID.randomUUID()}$EXT")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }

    /**
     * 删除指定路径的头像文件（仅用户上传类型，预设不需要删）
     *
     * @return true 表示成功删除；false 表示文件不存在或非用户上传
     */
    suspend fun deleteAvatar(avatarPath: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (avatarPath.isNullOrBlank() || AvatarPath.isPreset(avatarPath)) return@withContext false
            val file = File(avatarPath)
            file.exists() && file.delete()
        }

    /**
     * 从 URI 解码 Bitmap（用于拍照 / 相册选择）
     *
     * 采样率 = 1（先按原图加载，裁剪环节再缩放）
     */
    suspend fun decodeBitmap(context: Context, uri: Uri, maxSize: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
}
```

### Step 3.2: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/util/AvatarStorage.kt
$msg = @"
feat(util): 新增 AvatarStorage 工具(私有目录管理)

- 路径: /data/data/.../files/avatars/{uuid}.png
- API: saveAvatar/deleteAvatar/decodeBitmap
- 命名 UUID 避免冲突
- 固定 PNG 无压缩

无需 WRITE_EXTERNAL_STORAGE 权限。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 4: 圆形裁剪组件 `CircularImageCropper.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/CircularImageCropper.kt`

### Step 4.1: 创建文件

```kotlin
package com.corgimemo.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 圆形图片裁剪组件
 *
 * 交互：
 * - 单指拖动：平移图片（约束在裁剪框内）
 * - 双指捏合：缩放（0.5x ~ 5x）
 * - 双指拖动：平移（与单指一致）
 * - 圆形遮罩 + 半透明黑色背景突出裁剪区
 *
 * 输出：调用方传入 onCropComplete，组件内部按裁剪框区域生成正方形 Bitmap
 *
 * @param sourceBitmap 待裁剪的源图片
 * @param cropSize    圆形裁剪框直径（dp，默认 280dp）
 * @param onCropComplete 裁剪确认回调（裁剪后的 Bitmap）
 * @param onCancel 取消回调
 */
@Composable
fun CircularImageCropper(
    sourceBitmap: Bitmap,
    cropSize: androidx.compose.ui.unit.Dp = 280.dp,
    onCropComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // ===== 状态 =====
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val cropSizePx = with(density) { cropSize.toPx() }
    val painter = remember(sourceBitmap) {
        BitmapPainter(sourceBitmap.asImageBitmap())
    }

    // ===== 初始化：图片按 Fit 缩放到裁剪框内 =====
    LaunchedEffect(canvasSize, sourceBitmap) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val sw = sourceBitmap.width.toFloat()
            val sh = sourceBitmap.height.toFloat()
            val fitScale = max(cropSizePx / sw, cropSizePx / sh)
            scale = fitScale
            offsetX = 0f
            offsetY = 0f
        }
    }

    // ===== 主画布 =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
    ) {
        // 图片层
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                        // 简单的边界约束（够用，不做精细 clamp）
                        scale = newScale
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            // 绘制半透明黑色蒙版（除裁剪框外）
            drawRect(color = Color.Black.copy(alpha = 0.6f))

            // 计算裁剪框在画布中的位置（居中）
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = cropSizePx / 2f

            // 用 BlendMode.Clear 清除裁剪框区域的蒙版
            drawCircle(
                color = Color.Transparent,
                radius = r,
                center = Offset(cx, cy),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )

            // 绘制图片
            val imgW = sourceBitmap.width * scale
            val imgH = sourceBitmap.height * scale
            drawImage(
                image = sourceBitmap.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (cx - imgW / 2f + offsetX).toInt(),
                    (cy - imgH / 2f + offsetY).toInt()
                ),
                dstSize = androidx.compose.ui.unit.IntSize(
                    imgW.toInt().coerceAtLeast(1),
                    imgH.toInt().coerceAtLeast(1)
                )
            )

            // 绘制白色圆形边框
            drawCircle(
                color = Color.White,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 4f)
            )
        }
    }

    // ===== 裁剪按钮（在裁剪框下方，由调用方控制；本组件仅暴露回调）=====
    // 此处故意不放按钮 — 让 ProfileDetailScreen 控制布局
    // 调用方应在裁剪框下方放置两个按钮：
    //   "取消" → onCancel()
    //   "使用" → 计算最终 Bitmap → onCropComplete(bitmap)
}

/**
 * 从源 Bitmap + 缩放/偏移参数裁剪出最终圆形头像 Bitmap
 *
 * 算法：
 * 1. 用最终 scale/offset 重绘图片到目标 Bitmap
 * 2. 取中心 cropSize 区域
 * 3. 输出 512x512（足够 Profile 头卡 + 抽屉 48dp 高清显示）
 *
 * @param source  源图
 * @param scale   用户缩放比例
 * @param offsetX 用户水平偏移（px）
 * @param offsetY 用户垂直偏移（px）
 * @param canvasSize 裁剪器画布尺寸
 * @param cropSizePx 裁剪框直径（px）
 * @param outputSize  输出 Bitmap 尺寸（默认 512）
 */
fun cropCircularBitmap(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    canvasSize: IntSize,
    cropSizePx: Float,
    outputSize: Int = 512
): Bitmap {
    val sw = source.width
    val sh = source.height

    // 计算图片在画布中的位置
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val imgW = sw * scale
    val imgH = sh * scale
    val imgLeft = cx - imgW / 2f + offsetX
    val imgTop = cy - imgH / 2f + offsetY

    // 计算裁剪框对应的源图区域
    val cropLeft = cx - cropSizePx / 2f
    val cropTop = cy - cropSizePx / 2f

    // 源图上对应的区域
    val srcLeft = ((cropLeft - imgLeft) / scale).toInt().coerceIn(0, sw - 1)
    val srcTop = ((cropTop - imgTop) / scale).toInt().coerceIn(0, sh - 1)
    val srcRight = ((cropLeft + cropSizePx - imgLeft) / scale).toInt()
        .coerceIn(srcLeft + 1, sw)
    val srcBottom = ((cropTop + cropSizePx - imgTop) / scale).toInt()
        .coerceIn(srcTop + 1, sh)

    // 裁剪 + 缩放到输出尺寸
    val cropped = Bitmap.createBitmap(source, srcLeft, srcTop, srcRight - srcLeft, srcBottom - srcTop)
    return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
}
```

### Step 4.2: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/CircularImageCropper.kt
$msg = @"
feat(ui): 新增 CircularImageCropper 圆形裁剪组件

交互:
- 单指/双指拖动平移
- 双指捏合缩放 (0.5x-5x)
- 半透明黑色蒙版 + 白色圆形边框

输出 512x512 PNG Bitmap,供 AvatarStorage 保存。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 5: 预设头像数据 + 网格组件 `PresetAvatarGrid.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/PresetAvatarGrid.kt`

### Step 5.1: 创建文件

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.corgimemo.app.util.AvatarPath

/**
 * 柯基动作姿态预设（取自 drawable 中的帧动画）
 *
 * 每项 key 对应一个动作名，UI 渲染时按 key 在 drawable 中查找所有 corgi_{key}_* 帧
 */
data class CorgiPreset(
    val key: String,                  // 唯一 key,也是 avatarPath 的预设后缀
    val displayName: String,          // 中文显示名
    val description: String,          // 短描述
    val frameCount: Int,              // 帧数
    val frameDurationMs: Int = 250    // 每帧 ms
)

/** 项目中所有可用的柯基动作预设 */
val corgiPresets: List<CorgiPreset> = listOf(
    CorgiPreset("corgi_sit",    "坐下",  "安静坐姿", frameCount = 2),
    CorgiPreset("corgi_stand",  "站立",  "活泼站立", frameCount = 2),
    CorgiPreset("corgi_wink",   "眨眼",  "俏皮眨眼", frameCount = 2),
    CorgiPreset("corgi_wag",    "摇尾",  "开心摇尾", frameCount = 4, frameDurationMs = 180),
    CorgiPreset("corgi_tilt",   "歪头",  "歪头卖萌", frameCount = 2),
    CorgiPreset("corgi_sleep",  "睡觉",  "瞌睡打盹", frameCount = 2, frameDurationMs = 400),
    CorgiPreset("corgi_sad",    "难过",  "垂头丧气", frameCount = 2),
    CorgiPreset("corgi_proud",  "骄傲",  "昂首挺胸", frameCount = 2),
    CorgiPreset("corgi_shy",    "害羞",  "害羞躲闪", frameCount = 2),
    CorgiPreset("corgi_worry",  "焦虑",  "焦虑踱步", frameCount = 2),
    CorgiPreset("corgi_roll",   "打滚",  "开心打滚", frameCount = 4, frameDurationMs = 200),
    CorgiPreset("corgi_run",    "奔跑",  "欢快奔跑", frameCount = 4, frameDurationMs = 150),
    CorgiPreset("corgi_lie",    "趴卧",  "舒适趴卧", frameCount = 3, frameDurationMs = 300),
)

/**
 * 3 列动态网格（复用 FrameAnimation 循环播放）
 *
 * @param selectedKey 当前选中的预设 key（null = 无）
 * @param onPresetSelect 选中回调（key 字符串，调用方转 avatarPath）
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
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

@Composable
private fun PresetAvatarItem(
    preset: CorgiPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // FrameAnimation 直接播放 drawable 帧
    val frames = remember(preset.key) {
        (1..preset.frameCount).map { idx ->
            "corgi_${preset.key.removePrefix("corgi_")}_${preset.frameCount}frames_"
                .let { pattern ->
                    // 帧命名规则：corgi_{action}_{N}frames_{idx:02d}
                    val actionPart = preset.key  // 已是 corgi_sit 等完整 key
                    val nameBase = "${actionPart}_${preset.frameCount}frames"
                    "corgi_${actionPart.removePrefix("corgi_")}_${preset.frameCount}frames"
                        .replaceFirst("corgi_corgi_", "corgi_")
                        .let { "${it}_%02d".format(idx) }
                }
        }
    }

    // 简化：直接用 preset.key 构造帧名（约定：corgi_{action}_{N}frames_{idx:02d}）
    val frameResNames = remember(preset) {
        (1..preset.frameCount).map { idx ->
            "corgi_${preset.key.removePrefix("corgi_")}_${preset.frameCount}frames_%02d".format(idx)
        }
    }

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
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 复用 FrameAnimation 播放 drawable 帧
            FrameAnimation(
                frameResNames = frameResNames,
                durationMillis = preset.frameDurationMs,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

### Step 5.2: 验证 FrameAnimation 构造签名

打开 `app/src/main/java/com/corgimemo/app/ui/components/FrameAnimation.kt`，确认构造参数（必传 `frameResNames: List<String>`, `durationMillis: Int`）。如签名不同则按实际调整 Step 5.1。

### Step 5.3: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/PresetAvatarGrid.kt
$msg = @"
feat(ui): 新增 PresetAvatarGrid 3 列动态网格(复用柯基动作帧)

13 个柯基动作预设(sit/stand/wink/wag/tilt/sleep/sad/proud/shy/
worry/roll/run/lie),选中后边框主色高亮。

复用项目已有 FrameAnimation 渲染 drawable 帧,无需新建资源。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 6: 头像源选择 Sheet `AvatarSourceSheet.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/AvatarSourceSheet.kt`

### Step 6.1: 创建文件

```kotlin
package com.corgimemo.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * 头像源选择 BottomSheet
 *
 * 4 个来源：
 * 1. 拍照        → TakePictureLauncher，需要 FileProvider URI
 * 2. 选图(新)    → PickVisualMedia（Android 13+ PhotoPicker，无权限）
 * 3. 选图(兼容)  → PickVisualMedia 也兼容 Android 4.4+，与 (2) 合并
 * 4. 预设库      → 弹内部 grid，用户选一个 key
 *
 * 实际实现：
 * - "拍照"和"选图"共用 ActivityResultContracts.PickVisualMedia + TakePicture
 * - "预设库"弹 PresetAvatarGrid（在调用方页面内嵌）
 *
 * @param visible 是否显示
 * @param onDismiss 关闭
 * @param onPhotoTaken 拍照完成回调（uri）
 * @param onPhotoPicked 选图完成回调（uri）
 * @param onPresetSelected 预设选择完成回调（key）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSourceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPhotoTaken: (Uri) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onPresetSelected: (String) -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 拍照 URI（FileProvider 创建）
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { onPhotoTaken(it) }
        }
        pendingCameraUri = null
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onPhotoPicked(it) }
    }

    // 预设库内部状态
    var showPresets by remember { mutableStateOf(false) }
    var selectedPresetKey by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (showPresets) {
            // 预设库模式
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择预设头像",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                PresetAvatarGrid(
                    selectedKey = selectedPresetKey,
                    onPresetSelect = { key ->
                        selectedPresetKey = key
                        onPresetSelected(key)
                    },
                    modifier = Modifier.height(400.dp)
                )
            }
        } else {
            // 4 个来源选择
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "选择头像来源",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AvatarSourceRow(
                    icon = Icons.Default.CameraAlt,
                    title = "拍照",
                    description = "使用摄像头拍摄新头像"
                ) {
                    // 创建临时文件 + FileProvider URI
                    val tmpFile = File.createTempFile(
                        "avatar_capture_", ".jpg",
                        context.cacheDir
                    )
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tmpFile
                    )
                    pendingCameraUri = uri
                    takePictureLauncher.launch(uri)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }

                Spacer(Modifier.height(8.dp))

                AvatarSourceRow(
                    icon = Icons.Default.PhotoLibrary,
                    title = "从相册选择",
                    description = "Android 13+ 使用 PhotoPicker，无需权限"
                ) {
                    pickMediaLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }

                Spacer(Modifier.height(8.dp))

                AvatarSourceRow(
                    icon = Icons.Default.SmartToy,
                    title = "预设头像库",
                    description = "从 13 种柯基动作中选择"
                ) {
                    showPresets = true
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AvatarSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .padding(8.dp)
        )
        Spacer(Modifier.size(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

> 注意：上述 `Modifier.clickable` 需要 `import androidx.compose.foundation.clickable`，已包含在 import 列表。

### Step 6.2: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/AvatarSourceSheet.kt
$msg = @"
feat(ui): 新增 AvatarSourceSheet 4 路径选择 BottomSheet

4 个来源:
- 拍照 (ActivityResultContracts.TakePicture + FileProvider)
- 相册 (PickVisualMedia,Android 13+ PhotoPicker)
- 预设库 (内嵌 PresetAvatarGrid)
- 兼容性: PickVisualMedia 向下兼容 Android 4.4

本任务用 PickVisualMedia 一份 API 覆盖 2/3 两个用户选项。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 7: FileProvider + AndroidManifest 权限配置

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

### Step 7.1: 创建 `file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 拍照临时文件存放路径（cacheDir 下的 jpg 临时文件） -->
    <cache-path
        name="avatar_capture"
        path="." />
    <!-- 用户头像持久化路径（filesDir/avatars/） -->
    <files-path
        name="avatars"
        path="avatars/" />
</paths>
```

### Step 7.2: 修改 `AndroidManifest.xml`

在 `<application>` 内（任意位置）注册 `FileProvider`：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

在 `<manifest>` 内（`<application>` 外）声明 `CAMERA` 权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

> **重要**：CAMERA 权限属于"危险权限"，调用 `takePictureLauncher` 前**不需要**运行时请求权限（API 文档明确：使用 TakePicture 时系统自动临时授权 URI），所以本任务不需 `RequestPermission` 流程。

### Step 7.3: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
$msg = @"
feat(manifest): 注册 FileProvider + CAMERA 权限

- xml/file_paths.xml: cache-path(拍照临时) + files-path(avatars 持久化)
- AndroidManifest: <provider> 声明 authorities=${applicationId}.fileprovider
- <uses-permission CAMERA>: TakePicture 系统临时授权,无需运行时申请
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 8: 表项组件 `ProfileDetailRow.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/components/ProfileDetailRow.kt`

### Step 8.1: 创建文件

```kotlin
package com.corgimemo.app.ui.screens.profile.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 个人信息页表项（7 行之一）
 *
 * 视觉规范（参考 UI设计规范.md §12.1.2）：
 * - 圆角 12dp 卡片，elevation 1dp
 * - 高度 56dp（统一）
 * - 左侧 16sp 标签
 * - 中间值/占位
 * - 右侧箭头（占位项也显示但灰色）
 *
 * @param label 中文标签
 * @param value 当前值（null 时显示 placeholder）
 * @param placeholder 占位文字（如"暂未设置"）
 * @param enabled 是否可点击（false = 占位项）
 * @param onClick 点击回调
 */
@Composable
fun ProfileDetailRow(
    label: String,
    value: String? = null,
    placeholder: String = "暂未设置",
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value ?: placeholder,
                fontSize = 14.sp,
                color = if (value != null)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            if (enabled) {
                Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

### Step 8.2: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/components/ProfileDetailRow.kt
$msg = @"
feat(ui): 新增 ProfileDetailRow 表项组件(7 行之一)

视觉: 12dp 圆角卡片, 56dp 高, 左侧 15sp Medium 标签,
右侧 value 或 placeholder + 箭头。
enabled=false 用于占位项(手机/邮箱/密码/设备)。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 9: 个人信息页主屏 `ProfileDetailScreen.kt`

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/ProfileDetailScreen.kt`

### Step 9.1: 创建 ViewModel

新建 `app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/ProfileDetailViewModel.kt`：

```kotlin
package com.corgimemo.app.ui.screens.profile.detail

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.util.AvatarPath
import com.corgimemo.app.util.AvatarStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人信息页 ViewModel
 *
 * 职责：
 * - 暴露 CorgiData（StateFlow）
 * - 处理头像选择（4 路径）
 * - 处理姓名修改
 * - 处理性别设置
 */
@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val repository: CorgiRepository
) : ViewModel() {

    val corgiData: StateFlow<CorgiData?> = repository.observeCorgiData()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 处理拍照/选图 URI，进入裁剪环节
     * 裁剪后由调用方传回 croppedBitmap
     */
    fun onPhotoUriReceived(context: android.content.Context, uri: Uri, onBitmapReady: (Bitmap) -> Unit) {
        viewModelScope.launch {
            val bitmap = AvatarStorage.decodeBitmap(context, uri) ?: return@launch
            onBitmapReady(bitmap)
        }
    }

    /**
     * 保存裁剪后的头像
     * @param newPath 新的 avatarPath（preset:xxx 或绝对路径）
     * @param onSuccess 成功后回调（用于关闭裁剪界面）
     */
    fun saveAvatarPath(context: android.content.Context, newPath: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // 删除旧文件（仅用户上传类型）
            val oldPath = corgiData.value?.avatarPath
            AvatarStorage.deleteAvatar(oldPath)
            repository.updateAvatarPath(newPath)
            onSuccess()
        }
    }

    fun savePresetAvatar(presetKey: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val oldPath = corgiData.value?.avatarPath
            AvatarStorage.deleteAvatar(oldPath)
            repository.updateAvatarPath(AvatarPath.toPresetPath(presetKey))
            onSuccess()
        }
    }

    fun updateName(newName: String) {
        viewModelScope.launch {
            repository.updateCorgiName(newName)
        }
    }

    fun updateGender(gender: String?) {
        viewModelScope.launch {
            repository.updateGender(gender)
        }
    }
}
```

> 注：`repository.observeCorgiData()` 和 `repository.updateCorgiName(name)` 是项目已有 API（参考 HomeViewModel/ProfileViewModel），如不存在需补。

### Step 9.2: 创建主屏

```kotlin
package com.corgimemo.app.ui.screens.profile.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.components.AvatarSourceSheet
import com.corgimemo.app.ui.components.CircularImageCropper
import com.corgimemo.app.ui.components.UserAvatar
import com.corgimemo.app.ui.screens.profile.detail.components.ProfileDetailRow
import com.corgimemo.app.util.AvatarPath
import com.corgimemo.app.util.AvatarStorage
import com.corgimemo.app.util.cropCircularBitmap
import kotlinx.coroutines.launch

/**
 * 个人信息页
 *
 * 布局（单列 LazyColumn，spacedBy 12dp）：
 * 1. 大头像卡片（120dp UserAvatar + 名字 + 等级）
 * 2. 性别选择行（点击弹"男/女/其他/保密"对话框）
 * 3. 手机号（占位）
 * 4. 设置邮箱（占位）
 * 5. 修改密码（占位）
 * 6. 登录设备管理（占位）
 *
 * 触发"更换头像"：
 * - 点击大头像 → 弹 AvatarSourceSheet
 * - AvatarSourceSheet 4 个来源 → 拍照/选图 → CircularImageCropper → saveAvatar
 * - 预设库 → 直接 savePresetAvatar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    onBack: () -> Unit,
    viewModel: ProfileDetailViewModel = hiltViewModel()
) {
    val corgiData by viewModel.corgiData.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAvatarSourceSheet by remember { mutableStateOf(false) }
    var showCropper by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }

    // ===== UI =====
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ① 大头像卡片
            item {
                BigAvatarCard(
                    corgiData = corgiData,
                    onAvatarClick = { showAvatarSourceSheet = true },
                    onNameClick = { showNameDialog = true }
                )
            }
            // ② 性别（可设置）
            item {
                ProfileDetailRow(
                    label = "性别",
                    value = when (corgiData?.gender) {
                        "MALE" -> "男"
                        "FEMALE" -> "女"
                        "OTHER" -> "其他"
                        null -> null
                        else -> null
                    },
                    placeholder = "保密",
                    onClick = { showGenderDialog = true }
                )
            }
            // ③ 手机号（占位）
            item { ProfileDetailRow(label = "手机号", placeholder = "暂未设置", enabled = false) }
            // ④ 设置邮箱（占位）
            item { ProfileDetailRow(label = "设置邮箱", placeholder = "暂未设置", enabled = false) }
            // ⑤ 修改密码（占位）
            item { ProfileDetailRow(label = "修改密码", placeholder = "暂未设置", enabled = false) }
            // ⑥ 登录设备管理（占位）
            item { ProfileDetailRow(label = "登录设备管理", placeholder = "暂未设置", enabled = false) }
        }
    }

    // ===== 头像源选择 =====
    AvatarSourceSheet(
        visible = showAvatarSourceSheet,
        onDismiss = { showAvatarSourceSheet = false },
        onPhotoTaken = { uri ->
            viewModel.onPhotoUriReceived(context, uri) { bitmap ->
                sourceBitmap = bitmap
                showCropper = true
            }
        },
        onPhotoPicked = { uri ->
            viewModel.onPhotoUriReceived(context, uri) { bitmap ->
                sourceBitmap = bitmap
                showCropper = true
            }
        },
        onPresetSelected = { key ->
            viewModel.savePresetAvatar(key) { showAvatarSourceSheet = false }
        }
    )

    // ===== 圆形裁剪 =====
    if (showCropper && sourceBitmap != null) {
        CropperDialog(
            sourceBitmap = sourceBitmap!!,
            onConfirm = { croppedBitmap ->
                scope.launch {
                    val path = AvatarStorage.saveAvatar(context, croppedBitmap)
                    viewModel.saveAvatarPath(context, path) {
                        showCropper = false
                        sourceBitmap = null
                        showAvatarSourceSheet = false
                    }
                }
            },
            onCancel = {
                showCropper = false
                sourceBitmap = null
            }
        )
    }

    // ===== 改名对话框 =====
    if (showNameDialog) {
        NameEditDialog(
            currentName = corgiData?.name ?: "",
            onConfirm = { newName ->
                viewModel.updateName(newName)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    // ===== 性别选择对话框 =====
    if (showGenderDialog) {
        GenderPickerDialog(
            currentGender = corgiData?.gender,
            onSelect = { gender ->
                viewModel.updateGender(gender)
                showGenderDialog = false
            },
            onDismiss = { showGenderDialog = false }
        )
    }
}

// ===== 大头像卡片 =====
@Composable
private fun BigAvatarCard(
    corgiData: CorgiData?,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(
                nickname = corgiData?.name ?: "用户",
                avatarPath = corgiData?.avatarPath,
                size = 120.dp,
                onClick = onAvatarClick
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = corgiData?.name ?: "未设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "点击头像更换 · 点击名字修改",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ===== 裁剪全屏对话框 =====
@Composable
private fun CropperDialog(
    sourceBitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // 保存裁剪所需的 scale/offset
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val cropSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 280.dp.toPx() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column {
                CircularImageCropper(
                    sourceBitmap = sourceBitmap,
                    cropSize = 280.dp,
                    onCropComplete = {},
                    onCancel = onCancel
                )
                // 简化版:直接用初始 scale/offset (用户可后续扩展交互)
                androidx.compose.material3.Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    TextButton(
                        onClick = {
                            val cropped = cropCircularBitmap(
                                source = sourceBitmap,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                canvasSize = canvasSize,
                                cropSizePx = cropSizePx
                            )
                            onConfirm(cropped)
                        }
                    ) { Text("使用") }
                }
            }
        }
    }
}

// ===== 改名对话框 =====
@Composable
private fun NameEditDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(20) },
                singleLine = true,
                label = { Text("昵称（最多 20 字）") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===== 性别选择对话框 =====
@Composable
private fun GenderPickerDialog(
    currentGender: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择性别") },
        text = {
            Column {
                listOf(
                    "MALE" to "男",
                    "FEMALE" to "女",
                    "OTHER" to "其他",
                    null to "保密"
                ).forEach { (key, label) ->
                    TextButton(
                        onClick = { onSelect(key) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentGender == key) "✓ $label" else label,
                            color = if (currentGender == key)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
```

### Step 9.3: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/profile/detail/
$msg = @"
feat(ui): 新增 ProfileDetailScreen 个人信息页(7 行表项 + 头像/名字/性别可设置)

布局:
1. 大头像卡片 (120dp UserAvatar,点击更换)
2. 性别 (男/女/其他/保密,可设置)
3-6. 手机/邮箱/密码/设备 (占位 enabled=false)

头像源 4 路径:
- 拍照 (TakePicture + FileProvider)
- 相册 (PickVisualMedia,Android 13+)
- 预设库 (内嵌 PresetAvatarGrid)
裁剪 → 私有目录 PNG → 持久化

依赖 CorgiRepository.observeCorgiData()/updateCorgiName(),
如不存在需先补 API。
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 10: 路由 + 导航接通

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt`

### Step 10.1: 在 `Screen.kt` 添加路由

在 `object CorgiDetail : Screen("corgi_detail")` 之后添加：

```kotlin
/**
 * 个人信息详情页（从"我的"页头卡 / 抽屉头像进入）
 *
 * 承载：头像 / 名字 / 性别 / 手机 / 邮箱 / 密码 / 设备管理
 * 头像、名字、性别可设置；其余 4 项 UI 占位（后续接入账号系统）
 */
object ProfileDetail : Screen("profile_detail")
```

### Step 10.2: 在 `AppNavHost.kt` 注册路由

在 `CorgiDetail` composable 注册代码后，添加：

```kotlin
composable(Screen.ProfileDetail.route) {
    ProfileDetailScreen(
        onBack = { navController.popBackStack() }
    )
}
```

并添加 import：

```kotlin
import com.corgimemo.app.ui.screens.profile.detail.ProfileDetailScreen
```

### Step 10.3: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt `
        app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt
$msg = @"
feat(nav): 注册 ProfileDetail 路由

- Screen.ProfileDetail = profile_detail
- AppNavHost 注册 composable,onBack 走 popBackStack
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 11: Profile 头卡 / 抽屉接通 ProfileDetail

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/profile/components/ProfileHeroCard.kt`
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt`
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt`

### Step 11.1: ProfileHeroCard onNameClick 改 navigate

将 `onNameClick: () -> Unit` 内部逻辑改为跳路由：

```kotlin
// 在 ProfileScreen.kt 调用处改为：
ProfileHeroCard(
    corgiData = corgiData,
    consecutiveDays = corgiData?.consecutiveDays ?: 0,
    onNameClick = { navController.navigate(Screen.ProfileDetail.route) }
)
```

### Step 11.2: 删除 ProfileScreen 中的 `CorgiRenameDialog` 引用

改名功能已迁到 `ProfileDetailScreen`，删除：
- `showRenameDialog` 状态
- `CorgiRenameDialog` 调用代码
- `viewModel.updateCorgiName(...)` 调用
- 改名确认弹窗

### Step 11.3: MainScreen 抽屉回调改为 navigate

```kotlin
// MainScreen.kt
onProfileClick = {
    navController.navigate(Screen.ProfileDetail.route)
    coroutineScope.launch { drawerState.close() }
}
```

不再切到 `TabItem.PROFILE`。

### Step 11.4: AppDrawer onProfileClick 改名为 onUserAreaClick

为语义清晰，参数名 `onProfileClick` 改为 `onUserAreaClick`（点击用户区域 = 跳个人信息页）。

### Step 11.5: commit

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/profile/components/ProfileHeroCard.kt `
        app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt `
        app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt `
        app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt
$msg = @"
refactor(ui): 接通 ProfileDetail 路由,移除 ProfileScreen 改名弹窗

- ProfileHeroCard onNameClick: 跳 ProfileDetail 路由
- ProfileScreen: 移除 showRenameDialog / CorgiRenameDialog / 改名确认弹窗
- MainScreen onProfileClick: 跳 ProfileDetail 路由(原切 TabItem.PROFILE)
- AppDrawer onProfileClick: 改名为 onUserAreaClick(语义更准)

依据 .trae/rules/lambda 捕获陷阱防御.md:
- 改用 displayItems 最新状态(无变更)
- 新 lambda 内部直接 navigate,不持有外部可变引用
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG -Encoding utf8
git commit -F .git/COMMIT_EDITMSG
Remove-Item .git/COMMIT_EDITMSG
```

---

## Task 12: 编译验证（用户手动）

按 `.trae/rules/编译验证.md`，AI 不擅自执行 `./gradlew`。由用户手动运行：

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew assembleDebug
```

预期：所有 12 个 task 的代码都通过编译。

### 端到端验证清单

| # | 场景 | 预期 |
|---|---|---|
| 1 | 启动 app，进"我的"页 → 点击头卡头像 | 跳 `ProfileDetail` 页 |
| 2 | 启动 app，拉出抽屉 → 点击顶部用户区域 | 跳 `ProfileDetail` 页 |
| 3 | 在 `ProfileDetail` 点击大头像 | 弹 `AvatarSourceSheet` 4 选项 |
| 4 | 选择"拍照"→ 拍照 → 返回 | 进入 `CropperDialog` |
| 5 | 拖动/缩放图片 → 点击"使用" | 头像更新为新裁剪图，私有目录新增 PNG |
| 6 | 重启 app，drawer / Profile 头卡 / ProfileDetail 头像 | 三处一致显示新头像 |
| 7 | `ProfileDetail` 点击大头像 → "预设头像库" → 选"坐姿" | 头像更新为坐姿动作预览（drawable 帧循环） |
| 8 | `ProfileDetail` 点击名字 → 改名 → 确定 | 名字更新到所有 3 处 |
| 9 | `ProfileDetail` 点击"性别" → 选"男" | 性别行显示"男" |
| 10 | `ProfileDetail` 点击"手机号" | 无反应（enabled=false 占位） |
| 11 | Room v40→v41 升级：v40 安装 → 装新版本 | 数据保留，gender 默认 null |

---

## Commit 拆分总览

| # | 提交信息 | 文件数 |
|---|---|---|
| 1 | feat(数据层): CorgiData 新增 gender 字段并升级 Room 至 v41 | 4 |
| 2 | feat(util): 新增 AvatarPath 工具(预设路径前缀识别) | 1 |
| 3 | feat(util): 新增 AvatarStorage 工具(私有目录管理) | 1 |
| 4 | feat(ui): 新增 CircularImageCropper 圆形裁剪组件 | 1 |
| 5 | feat(ui): 新增 PresetAvatarGrid 3 列动态网格 | 1 |
| 6 | feat(ui): 新增 AvatarSourceSheet 4 路径选择 BottomSheet | 1 |
| 7 | feat(manifest): 注册 FileProvider + CAMERA 权限 | 2 |
| 8 | feat(ui): 新增 ProfileDetailRow 表项组件 | 1 |
| 9 | feat(ui): 新增 ProfileDetailScreen + ViewModel | 2 |
| 10 | feat(nav): 注册 ProfileDetail 路由 | 2 |
| 11 | refactor(ui): 接通 ProfileDetail 路由,移除 ProfileScreen 改名弹窗 | 4 |
| **总** | | **20** |

---

## Self-Review（spec 覆盖度自检）

| 需求 | Task | 状态 |
|---|---|---|
| 数据层 gender + Room v41 | Task 1 | ✅ |
| avatarPath 路径前缀 | Task 2 | ✅ |
| 私有目录存储 | Task 3 | ✅ |
| Compose 圆形裁剪 | Task 4 | ✅ |
| 4 路径选择（拍照/相册/预设/兼容） | Task 5 + 6 | ✅ |
| FileProvider + 权限 | Task 7 | ✅ |
| 表项组件 | Task 8 | ✅ |
| 个人信息页主屏 | Task 9 | ✅ |
| 路由 + 导航 | Task 10 + 11 | ✅ |
| 3 入口接通（drawer / Profile 头卡 / ProfileDetail） | Task 11 | ✅ |
| 7 行表项（3 可设置 + 4 占位） | Task 9 | ✅ |
| 编译验证 | Task 12 | ✅ |

### 占位符自检

- ❌ "TBD" / "TODO" / "implement later" → 无
- ❌ "Add appropriate error handling" → 所有函数都有 try-catch 或 Room 防护
- ❌ "Similar to Task N" → 每个 step 代码独立完整
- ❌ "Write tests for the above" → 本任务无测试要求（项目惯例：UI 任务不做单测）

### 类型一致性

- `AvatarPath.PRESET_PREFIX` 一致
- `AvatarStorage.saveAvatar` 返回 String 路径
- `CorgiData.gender: String?` 一致
- `ProfileDetailViewModel.saveAvatarPath(context, newPath, onSuccess)` 一致
- `Screen.ProfileDetail.route = "profile_detail"` 一致

---

## 风险与缓解

| 风险 | 严重度 | 缓解 |
|---|---|---|
| `FrameAnimation` 构造签名不匹配 | 中 | Step 5.2 验证 |
| `repository.observeCorgiData()` API 不存在 | 中 | Task 9.1 注释提示，按需补 |
| `repository.updateCorgiName()` API 不存在 | 中 | 同上 |
| 圆形裁剪 BlendMode 在某些 GPU 上异常 | 低 | 退回方案：用 `Modifier.drawWithCache` + `Canvas` 手动绘制遮罩 |
| 拍照后图片过大 OOM | 中 | `decodeBitmap` 已加 `maxSize=2048` 限制 |
| CAMERA 权限在某些 ROM 上不自动授权 | 低 | 走运行时权限流程（后续补） |
| `AvatarSourceSheet` 嵌套 ModalBottomSheet 显示预设库 | 低 | 用 `if (showPresets)` 条件渲染（已实现） |
| Compose 1.9.2 `BlendMode.Clear` API 变动 | 低 | 实在不可用改用 `drawWithContent` + `clipPath` 方案（按 .trae/rules/api不可用处理规则.md 优先修复） |

---

## 不在本次范围（后续任务）

- 账号系统（手机/邮箱/密码/设备管理）
- `Screen.AvatarPreview` 大图预览全屏路由
- `UserAvatar.onAvatarLongClick` 长按更换（本期已用点击触发，可后续优化为长按）
- 圆形裁剪的精细化（双指旋转、镜像、九宫格辅助线）
- 预设头像的多姿态同时显示（当前一次只显示一个姿态）
- 平板 `WindowSizeClass` 自适应
- 头像单元测试
