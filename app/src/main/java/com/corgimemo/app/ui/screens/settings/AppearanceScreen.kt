package com.corgimemo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.screens.profile.components.ThemePresets
import com.corgimemo.app.ui.theme.FontCatalog
import com.corgimemo.app.ui.theme.FontEntry
import com.corgimemo.app.viewmodel.SettingsViewModel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat

/**
 * 外观设置页面
 *
 * 从 SettingsScreen 拆出独立页，承载两类外观设置：
 * 1. 深色模式：跟随系统 / 亮色 / 深色（三选一，立即生效，写入 DataStore）
 * 2. 主题色：6 种 UI 设计规范 12.1.3 配色（暖阳橙/樱花粉/薄荷绿/天空蓝/薰衣紫/奶茶棕）
 *
 * 入口路径：
 * - 「我的」页 → 主题配色卡 → 整卡点击 → `Screen.Appearance.route`
 *
 * 与 ThemeQuickSwitch 的关系：
 * - ThemeQuickSwitch（Profile 页）只读展示当前主题，不在入口处切换
 * - 本页负责完整切换（深色模式 + 主题色）
 *
 * 数据来源：复用 `SettingsViewModel.themeMode` / `themeColor`（共享同一 DataStore key），
 * 任何一处修改全 App 立即生效。
 *
 * @param navController 导航控制器
 * @param viewModel 设置页 ViewModel（Hilt 注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // ========== 状态收集 ==========
    val themeMode by viewModel.themeMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val fontId by viewModel.fontId.collectAsState()
    val latinFontId by viewModel.latinFontId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "外观",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ========== 分组 1：深色模式 ==========
            item {
                Column {
                    AppearanceSectionTitle("深色模式")
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppearanceSegmentOption(
                                text = "🌓 跟随系统",
                                selected = themeMode == "system",
                                onClick = { viewModel.setThemeMode("system") },
                                modifier = Modifier.weight(1f)
                            )
                            AppearanceSegmentOption(
                                text = "☀️ 亮色",
                                selected = themeMode == "light",
                                onClick = { viewModel.setThemeMode("light") },
                                modifier = Modifier.weight(1f)
                            )
                            AppearanceSegmentOption(
                                text = "🌙 深色",
                                selected = themeMode == "dark",
                                onClick = { viewModel.setThemeMode("dark") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ========== 分组 2：主题色（6 色）==========
            item {
                Column {
                    AppearanceSectionTitle("主题色")
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
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 2 行 × 3 列：6 个主题色块，紧凑展示
                            val rows = ThemePresets.chunked(3)
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { preset ->
                                        AppearanceColorOption(
                                            color = preset.color,
                                            name = preset.name,
                                            selected = themeColor == preset.key,
                                            onClick = { viewModel.setThemeColor(preset.key) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // 补齐空位（最后一行可能 < 3 个）
                                    repeat(3 - rowItems.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ========== 分组 3：正文字体（内置可商用中文多字重，OFL 1.1）==========
            item {
                Column {
                    AppearanceSectionTitle("正文字体")
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
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FontCatalog.entries.forEach { entry ->
                                val isSelected = fontId == entry.id
                                AppearanceFontOption(
                                    title = entry.displayName,
                                    // 真实预览：选中行走全局 Typography 已加载字体（零额外内存）；
                                    // 未选中行用有界 Bitmap 缓存渲染真实字形（绕过 Compose 永久
                                    // Typeface 缓存，见 FontBodyPreview）。全部保留完整字形集。
                                    preview = { color ->
                                        FontBodyPreview(entry = entry, selected = isSelected, color = color)
                                    },
                                    licenseLabel = "${entry.licenseName} · ${entry.boldTiers.size} 档加粗",
                                    selected = isSelected,
                                    onClick = { viewModel.setFontId(entry.id) }
                                )
                            }
                        }
                    }
                }
            }

            // ========== 分组 4：英文/数字字体（拉丁回退层，OFL 1.1）==========
            item {
                Column {
                    AppearanceSectionTitle("英文/数字字体")
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
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 默认（系统字体）：不叠加拉丁回退层，英文/数字走正文字体自带拉丁字形
                            AppearanceFontOption(
                                title = "默认（系统字体）",
                                preview = { color ->
                                    Text(
                                        text = "Ag 0123 Gg 4567",
                                        fontSize = 18.sp,
                                        fontFamily = null,
                                        color = color
                                    )
                                },
                                licenseLabel = "英文/数字走正文字体自带拉丁字形",
                                selected = latinFontId == "",
                                onClick = { viewModel.setLatinFontId("") }
                            )
                            FontCatalog.latinEntries.forEach { entry ->
                                AppearanceFontOption(
                                    title = entry.displayName,
                                    preview = { color ->
                                        Text(
                                            text = "Ag 0123 Gg 4567",
                                            fontSize = 18.sp,
                                            fontFamily = entry.family,
                                            color = color
                                        )
                                    },
                                    licenseLabel = "${entry.licenseName} · ${entry.boldTiers.size} 档加粗",
                                    selected = latinFontId == entry.id,
                                    onClick = { viewModel.setLatinFontId(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 外观设置分组标题
 * 与 SettingsScreen 的 SettingSectionTitle 视觉一致（13sp Medium + onSurfaceVariant）
 *
 * @param title 标题文案
 */
@Composable
private fun AppearanceSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/**
 * 深色模式分段按钮
 * 三等分胶囊形按钮，选中态主色填充，未选中 surfaceVariant
 *
 * @param text 显示文字（含 emoji）
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param modifier 外部 Modifier（用于 weight）
 */
@Composable
private fun AppearanceSegmentOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 主题色选项块
 * 48dp 圆形色点 + 主色环（选中）+ 名称
 *
 * 视觉规范：
 * - 圆点 48dp，色值取自 ThemePresets
 * - 选中态：圆点外 3dp 主色环 + 圆点内白色 "✓"
 * - 名称 12sp Medium，选中态主色，未选中 onSurfaceVariant
 * - 整块可点击，点击调用 onClick
 *
 * @param color 色点主色
 * @param name 显示名（暖阳橙/樱花粉/...）
 * @param selected 是否当前主题色
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
private fun AppearanceColorOption(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp)
        ) {
            // 选中态外环
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
            // 色点
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.06f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 正文字体预览有界缓存。
 *
 * 设置页需同时展示全部 CJK 正文字体（单文件 14~19MB）的真实预览。若直接让 Compose 用各自
 * 的 [FontFamily] 渲染，[androidx.compose.ui.text.font.FontFamilyResolver] 会把每个字体
 * 永久缓存进进程，10 款累计可撑爆低内存设备堆而 OOM（见 app崩溃信息收集）。
 *
 * 解法：正文预览改为「按需把字体画进一张小 Bitmap（ARGB_8888，单行约数十 KB），画完即释放
 * Typeface」——通过 [android.graphics.Typeface.Builder] 从资源流即时构建 Typeface（API≥26
 * 走 InputStream 分支，框架不缓存，渲染后随局部引用离开作用域被 GC 回收），仅常驻极小位图。
 * [LruCache] 容量 12，避免每次重组重绘；位图按 `entry.id` 缓存，主题切换（onSurface 变化）
 * 时经 remember 依赖重渲染。配合 AndroidManifest 的 `android:largeHeap="true"` 作安全网。
 */
private val fontPreviewCache = object : LruCache<String, Bitmap>(12) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

/** sp → px（用于预览位图文字尺寸） */
private fun spToPx(sp: Float, context: Context): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)

/**
 * 取预览用 Typeface：系统默认条目用 [Typeface.DEFAULT]；内置字体从 `res/font` 资源流即时
 * 构建（API≥26 走 [Typeface.Builder] 的 InputStream 分支，框架不缓存，渲染后释放；低版本
 * 回退 [ResourcesCompat.getFont]）。失败一律回退系统字体，绝不抛异常。
 */
private fun loadPreviewTypeface(context: Context, entry: FontEntry): Typeface {
    if (entry.isSystemDefault || entry.resByWeight.isEmpty()) return Typeface.DEFAULT
    val resId = entry.resByWeight.values.first()
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.resources.openRawResource(resId).use { stream ->
                Typeface.Builder(stream).build()
            }
        } else {
            ResourcesCompat.getFont(context, resId)
        }
    }.getOrDefault(Typeface.DEFAULT)
}

/**
 * 把单款字体的预览文字渲染成 Bitmap（颜色取 [colorArgb]，与未选中行文字色一致）。字体文件
 * 仅在本次调用期间以 ~17MB 暂存，函数返回后局部 Typeface/Paint 引用离开作用域，由 GC 回收，
 * 预览常驻内存仅这张小位图。
 */
private fun renderFontPreviewBitmap(
    context: Context,
    entry: FontEntry,
    text: String,
    colorArgb: Int
): Bitmap {
    val textSizePx = spToPx(18f, context)
    val typeface = loadPreviewTypeface(context, entry)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.color = colorArgb
        textSize = textSizePx
    }
    val width = (paint.measureText(text) + 4f).toInt().coerceAtLeast(1)
    val fm = paint.fontMetrics
    val height = (fm.descent - fm.ascent + 4f).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawText(text, 2f, -fm.ascent + 2f, paint)
    return bitmap
}

/**
 * 正文（CJK）字体预览：未选中项用有界缓存 Bitmap 渲染真实字形（绕过 Compose 永久 Typeface
 * 缓存）；选中项直接走 [FontFamily] 实时文本（选中字体本就是全局 Typography 已加载字体，
 * 零额外内存，且颜色随选中态精确匹配）。[color] 为当前行文字色（未选中恒为 onSurface，与
 * 位图烘焙色一致）。
 */
@Composable
private fun FontBodyPreview(
    entry: FontEntry,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Text(
            text = entry.displayName,
            fontSize = 18.sp,
            fontFamily = entry.family,
            color = color
        )
    } else {
        val context = LocalContext.current
        val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
        val bitmap = remember(entry.id, onSurfaceArgb) {
            fontPreviewCache.get(entry.id) ?: renderFontPreviewBitmap(
                context, entry, entry.displayName, onSurfaceArgb
            ).also { fontPreviewCache.put(entry.id, it) }
        }
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
    }
}

/**
 * 字体单选行（正文字体 / 英文·数字字体 通用）
 *
 * 一行展示一款字体：以调用方注入的 [preview]（`(color: Color) -> Unit` 可组合 lambda）渲染
 * 预览，下方标注「名称 · 授权/档位」；选中态用主色容器 + "✓" 标记。
 * 预览的内容与颜色由调用方决定——正文字体经有界 Bitmap 缓存渲染真实字形（见 [FontBodyPreview]），
 * 拉丁字体用实时文本。点击调用 [onClick]，由调用方决定写入正文字体还是拉丁回退层偏好。
 *
 * @param title 字体名称（显示在副标题）
 * @param preview 预览可组合 lambda，参数为当前行文字色（随选中态切换）
 * @param licenseLabel 副标题授权/档位说明
 * @param selected 是否当前选中
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
private fun AppearanceFontOption(
    title: String,
    preview: @Composable (color: Color) -> Unit,
    licenseLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 预览由调用方注入：正文字体经有界 Bitmap 缓存渲染真实字形；拉丁字体实时文本
            preview(titleColor)
            Text(
                text = "$title · $licenseLabel",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Text(
                text = "✓",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
