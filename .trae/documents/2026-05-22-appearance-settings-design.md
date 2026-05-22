# CorgiMemo 外观设置功能设计文档

> 日期: 2026-05-22
> 状态: 已批准
> 方案: ViewModel + StateFlow 状态提升

## 1. 功能概述

为 CorgiMemo 添加 App 内深色模式切换和主题色自定义功能，允许用户在「设置」页面中个性化应用外观。

### 1.1 功能范围

| 功能                   | 描述                                        | 优先级 |
| ---------------------- | ------------------------------------------- | ------ |
| **深色模式切换** | 支持跟随系统 / 亮色模式 / 深色模式 三种选项 | P0     |
| **主题色自定义** | 提供 5 种预设主题色供用户选择               | P1     |

### 1.2 用户决策记录

- 设置入口位置：**Settings 页面内**（添加外观设置区块）
- UI 交互形式：**单选列表/分段控件**
- 实现方案：**ViewModel + StateFlow 状态提升**

---

## 2. 架构设计

### 2.1 整体架构

采用 MVVM + DataStore 架构，通过 StateFlow 实现响应式主题切换：

```
┌──────────────────────────────────────────────────────────────┐
│                        MainActivity                           │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              SettingsViewModel                        │     │
│  │  themeMode: StateFlow<String>   ("system"/"light"/"dark")│    │
│  │  themeColor: StateFlow<String>  (5种颜色标识)          │     │
│  └──────────────────────┬──────────────────────────────┘     │
│                         │ collectAsState()                    │
│                         ▼                                     │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              CorgiMemoTheme(                          │     │
│  │                darkTheme = viewModel.isDarkTheme,     │     │
│  │                colorScheme = getColorScheme(...)      │     │
│  │              )                                       │     │
│  └─────────────────────────────────────────────────────┘     │
│                         │                                     │
│                         ▼                                     │
│  ┌─────────────────────────────────────────────────────┐     │
│  │                   SettingsScreen                     │     │
│  │  外观设置 → setThemeMode() / setThemeColor()         │     │
│  └─────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

1. **读取流程**: `DataStore` → `CorgiPreferences.Flow` → `SettingsViewModel.StateFlow` → `Compose UI.collectAsState()` → `CorgiMemoTheme()`
2. **写入流程**: `UI 点击` → `SettingsViewModel.set*()` → `CorgiPreferences.save*()` → `DataStore 持久化` → `StateFlow 更新` → `Compose 重组`

---

## 3. 详细设计

### 3.1 数据层 - CorgiPreferences 扩展

**文件**: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt`

#### 新增存储键

```kotlin
private object Keys {
    // ... 现有 keys ...
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_COLOR = stringPreferencesKey("theme_color")
}
```

#### 新增 Flow 属性

```kotlin
/**
 * 获取主题模式的Flow
 * @return "system" | "light" | "dark"，默认 "system"
 */
val themeMode: Flow<String> = dataStore.data
    .map { preferences: Preferences ->
        preferences[Keys.THEME_MODE] ?: "system"
    }

/**
 * 获取主题色的Flow
 * @return 颜色标识符，默认 "orange"
 */
val themeColor: Flow<String> = dataStore.data
    .map { preferences: Preferences ->
        preferences[Keys.THEME_COLOR] ?: "orange"
    }
```

#### 新增保存方法

```kotlin
/**
 * 保存主题模式偏好
 * @param mode "system" | "light" | "dark"
 */
suspend fun saveThemeMode(mode: String) {
    dataStore.edit { preferences: MutablePreferences ->
        preferences[Keys.THEME_MODE] = mode
    }
}

/**
 * 保存主题色偏好
 * @param color 颜色标识符
 */
suspend fun saveThemeColor(color: String) {
    dataStore.edit { preferences: MutablePreferences ->
        preferences[Keys.THEME_COLOR] = color
    }
}
```

---

### 3.2 ViewModel 层 - SettingsViewModel 扩展

**文件**: `app/src/main/java/com/corgimemo/app/viewmodel/SettingsViewModel.kt`

#### 新增状态

```kotlin
/** 主题模式：system/light/dark */
private val _themeMode = MutableStateFlow("system")
val themeMode: StateFlow<String> = _themeMode.asStateFlow()

/** 主题色标识符 */
private val _themeColor = MutableStateFlow("orange")
val themeColor: StateFlow<String> = _themeColor.asStateFlow()
```

#### 计算属性

```kotlin
/**
 * 根据当前模式计算是否使用深色主题
 * - "dark" → true
 * - "light" → false
 * - "system" → 跟随系统设置
 */
val isDarkTheme: Boolean
    get() = when (_themeMode.value) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
```

#### 新增方法

```kotlin
/**
 * 设置主题模式
 * @param mode "system" | "light" | "dark"
 */
fun setThemeMode(mode: String) {
    viewModelScope.launch {
        _themeMode.value = mode
        corgiPreferences.saveThemeMode(mode)
    }
}

/**
 * 设置主题色
 * @param color 颜色标识符（如 "orange", "mint" 等）
 */
fun setThemeColor(color: String) {
    viewModelScope.launch {
        _themeColor.value = color
        corgiPreferences.saveThemeColor(color)
    }
}
```

#### 初始化加载

在现有 `loadSettings()` 方法中追加：

```kotlin
private fun loadSettings() {
    viewModelScope.launch {
        // ... 现有加载逻辑 ...

        // 加载主题设置
        _themeMode.value = corgiPreferences.themeMode.first()
        _themeColor.value = corgiPreferences.themeColor.first()
    }
}
```

---

### 3.3 主题系统 - Color.kt 扩展

**文件**: `app/src/main/java/com/corgimemo/app/ui/theme/Color.kt`

#### 主题色彩定义

| 标识符       | 名称     | 主色调      | 色彩感觉         |
| ------------ | -------- | ----------- | ---------------- |
| `orange`   | 暖橙色   | `#FF9A5C` | 温暖活力（默认） |
| `mint`     | 薄荷绿   | `#4ECDC4` | 清新自然         |
| `sky`      | 天空蓝   | `#5BA8E0` | 宁静专业         |
| `sakura`   | 樱花粉   | `#FF9AA2` | 温柔浪漫         |
| `lavender` | 薰衣草紫 | `#B39DDB` | 优雅神秘         |

#### 工厂函数

新增 `getColorScheme()` 函数：

```kotlin
/**
 * 根据颜色标识和是否深色模式返回对应的 ColorScheme
 * @param colorName 颜色标识符
 * @param darkTheme 是否深色模式
 * @return Material3 ColorScheme
 */
fun getColorScheme(colorName: String, darkTheme: Boolean): ColorScheme {
    return when (colorName) {
        "mint" -> if (darkTheme) MintDarkColorScheme else MintLightColorScheme
        "sky" -> if (darkTheme) SkyDarkColorScheme else SkyLightColorScheme
        "sakura" -> if (darkTheme) SakuraDarkColorScheme else SakuraLightColorScheme
        "lavender" -> if (darkTheme) LavenderDarkColorScheme else LavenderLightColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme // orange 默认
    }
}
```

每套颜色需要定义完整的 Light/Dark ColorScheme，包含：

- primary, onPrimary, primaryContainer, onPrimaryContainer
- secondary, onSecondary, secondaryContainer, onSecondaryContainer
- background, onBackground
- surface, onSurface, surfaceVariant, onSurfaceVariant
- error, onError, errorContainer, onErrorContainer
- outline, outlineVariant, scrim

---

### 3.4 Theme.kt 修改

**文件**: `app/src/main/java/com/corgimemo/app/ui/theme/Theme.kt`

#### 变更内容

```kotlin
@Composable
fun CorgiMemoTheme(
    darkTheme: Boolean,           // 移除默认值，必须外部传入
    themeColor: String = "orange",// 新增：主题色参数
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(themeColor, darkTheme) // 使用工厂函数
    }

    // ... 其余不变 ...
}
```

---

### 3.5 UI 层 - Settings 页面修改

**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt`

#### 外观设置区块 UI 设计

```
┌─────────────────────────────────────────────────────────┐
│ 🎨 外观设置                                              │
│ ─────────────────────────────────────────────────────── │
│                                                         │
│ 深色模式                                                │
│ ┌─────────────┬─────────────┬─────────────┐             │
│ │  🌓 跟随系统 │  ☀️ 亮色模式  │  🌙 深色模式  │             │
│ └─────────────┴─────────────┴─────────────┘             │
│                                                         │
│ 主题色                                                  │
│ ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐       │
│ │  🟠  │  │  🟢  │  │  🔵  │  │  🌸  │  │  💜  │       │
│ │ 橙色 │  │ 薄荷 │  │ 天空 │  │ 樱花 │  │ 薰衣草 │       │
│ └──────┘  └──────┘  └──────┘  └──────┘  └──────┘       │
│          ✓选中                                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### 组件实现要点

1. **深色模式选择器**: 使用 `SegmentedButton` 或 `Row + 可点击项` 实现三选一
2. **主题色选择器**: 使用 `LazyRow` 或 `Row` 展示 5 个圆形色块，选中项带边框+对勾标记
3. **即时生效**: 选择后调用 ViewModel 方法，Compose 自动重组更新主题

---

### 3.6 MainActivity 连接

**文件**: `app/src/main/java/com/corgimemo/app/MainActivity.kt`

#### 关键变更

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val themeColor by settingsViewModel.themeColor.collectAsState()

            CorgiMemoTheme(
                darkTheme = settingsViewModel.isDarkTheme,
                themeColor = themeColor
            ) {
                // ... NavHost ...
            }
        }
    }
}
```

---

## 4. 文件变更清单

| 序号 | 文件路径                                         | 变更类型 | 说明                                          |
| ---- | ------------------------------------------------ | -------- | --------------------------------------------- |
| 1    | `.../data/local/datastore/CorgiPreferences.kt` | 修改     | 新增 THEME_MODE/THEME_COLOR 键及读写方法      |
| 2    | `.../viewmodel/SettingsViewModel.kt`           | 修改     | 新增主题相关 StateFlow、计算属性和方法        |
| 3    | `.../ui/theme/Color.kt`                        | 修改     | 新增 4 套色彩方案 + getColorScheme() 工厂函数 |
| 4    | `.../ui/theme/Theme.kt`                        | 修改     | 移除系统默认值依赖，接收外部参数              |
| 5    | `.../ui/screens/settings/SettingsScreen.kt`    | 修改     | 添加「外观设置」UI 区块                       |
| 6    | `.../MainActivity.kt`                          | 修改     | 连接 ViewModel 与 Theme，实现动态切换         |

---

## 5. 验收标准

### 5.1 功能验收

- [ ] 「设置」页面可看到「外观设置」区块
- [ ] 深色模式支持三种选项切换：跟随系统 / 亮色 / 深色
- [ ] 主题色支持 5 种预设颜色切换
- [ ] 切换后立即生效，无需重启 App
- [ ] 重启 App 后保持用户上次的选择

### 5.2 技术验收

- [ ] 使用 DataStore 持久化存储（key: "theme_mode", "theme_color"）
- [ ] ViewModel 正确暴露 StateFlow 供 Compose 观察
- [ ] 深色模式下所有组件正确显示
- [ ] 主题色切换后主色调、按钮、强调色同步变化

---

## 6. 风险与注意事项

| 风险                           | 影响       | 缓解措施                                 |
| ------------------------------ | ---------- | ---------------------------------------- |
| 深色模式下某些硬编码颜色不适配 | 视觉不一致 | 全面检查代码中的硬编码颜色引用           |
| 主题色切换时闪烁               | 用户体验差 | 确保 StateFlow 更新与 Compose 重组同步   |
| ColorScheme 配色不当           | 对比度不足 | 遵循 WCAG 2.1 AA 标准（对比度 ≥ 4.5:1） |
