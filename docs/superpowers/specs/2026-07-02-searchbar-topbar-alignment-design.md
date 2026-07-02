# 搜索栏与导航栏精确对齐设计

> **创建日期**：2026-07-02
> **状态**：已批准，待实施
> **范围**："我的待办"、"灵感"、"日期"三个 Tab 页面（HomeScreen、InspirationScreen、SpecialDateScreen）的搜索栏顶部与 EnhancedTopBar 底部对齐

## 1. 背景与目标

### 1.1 当前问题

在三个核心 Tab 页面中：

| 元素 | 顶部坐标（基于状态栏 44dp） | 底部坐标 |
|------|--------------------------|---------|
| 状态栏 | 0dp | 44dp |
| EnhancedTopBar 内容 | 44dp | 92dp（44 + 48） |
| **间隙** | **92dp** | **100dp**（存在 8dp 间隙） |
| SearchBar 容器 | 100dp | 164dp（100 + 64） |

**结论：** 搜索栏顶部（100dp）与导航栏底部（92dp）之间存在 8dp 间隙，未实现"无缝衔接"。

### 1.2 目标

- ✅ 搜索栏顶部边缘 **精确等于** 导航栏底部边缘（零间距）
- ✅ 在所有页面状态下一致保持：
  - 默认状态
  - 滚动收缩/展开动画过程中
  - 批量模式下
  - 异形屏、刘海屏、挖孔屏、折叠屏、横屏
- ✅ 不破坏现有的 `searchRevealProgress` 滚动隐藏动画机制

## 2. 设计方案

### 2.1 方案选择

| 方案 | 改动范围 | 风险 | 推荐度 |
|------|---------|------|-------|
| **A：调整 padding** | 1-2 行代码 + 1 个 dimens 常量 | 极低 | ⭐⭐⭐⭐⭐ |
| B：ConstraintLayout 锚定 | 引入依赖 + 重构两个屏幕 | 高 | ⭐⭐ |
| C：移入 Scaffold topBar | 重构 MainScreen + 跨 Tab 影响 | 高 | ⭐ |

**选定方案 A**，理由：
- 改动最小，意图明确
- 保留现有 `Box` + `layout { }` + `searchRevealProgress` 机制
- 不影响其他 Tab（灵感/日期/我的）
- 不影响安全区域处理

### 2.2 详细变更

#### 变更点 1：HomeScreen.kt 搜索栏 padding 调整

**文件：** [HomeScreen.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)
**位置：** 第 644 行附近（SearchBar 组件调用处）

**当前代码：**
```kotlin
SearchBar(
    query = searchQuery,
    onQueryChange = { newQuery ->
        viewModel.updateSearchQuery(newQuery)
    },
    onClear = {
        viewModel.clearSearch()
    },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),  // ← 8dp 上下对称
    trailingIcon = { ... }
)
```

**变更后：**
```kotlin
SearchBar(
    query = searchQuery,
    onQueryChange = { newQuery ->
        viewModel.updateSearchQuery(newQuery)
    },
    onClear = {
        viewModel.clearSearch()
    },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin)),
    trailingIcon = { ... }
)
```

**变更要点：**
- 移除 `vertical = 8.dp`（即移除顶部 8dp padding）
- 显式添加 `bottom = dimensionResource(...)` 保留底部 8dp 间距

#### 变更点 2：dimens.xml 新增常量

**文件：** [dimens.xml](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/res/values/dimens.xml)
**位置：** 搜索栏相关常量区（第 38 行附近）

**新增内容：**
```xml
<!-- ========== 搜索栏间距 ========== -->
<dimen name="ui_search_bar_height">48dp</dimen>          <!-- 已存在 -->
<dimen name="ui_search_bar_corner_radius">24dp</dimen>  <!-- 已存在 -->
<dimen name="ui_search_bar_bottom_margin">8dp</dimen>    <!-- 新增 -->
```

**常量值说明：**
- 8dp = 视觉上的"轻量间距"，与设计规范中"元素间距 8dp"一致
- 仅作用于搜索栏底部（顶部 = 0dp，紧贴导航栏）
- 同时提供修改入口（未来如需调整间距，改一个值即可）

#### 变更点 3：HomeScreen.kt 添加 import

**新增 import：**
```kotlin
import androidx.compose.ui.res.dimensionResource
```

**文件位置：** [HomeScreen.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) import 区域

## 3. 状态行为矩阵

| 场景 | 搜索栏顶部坐标 | 行为验证 |
|------|--------------|---------|
| **默认状态** | = 导航栏底部（92dp） | ✅ 零间距对齐 |
| **输入搜索词** | = 导航栏底部（92dp） | ✅ 无变化 |
| **滚动到中部** | 仍 = 92dp（外层 Box 收缩） | ✅ 顶部锚点固定 |
| **滚动到顶部** | = 92dp（外层 Box 展开至 64dp） | ✅ 顶部锚点固定 |
| **批量模式** | 整体 alpha = 0，不占位 | ✅ 无残留间隙 |
| **Tab 切换** | 导航栏与内容区切换，无残留 | ✅ 跨 Tab 一致 |
| **异形屏（刘海）** | 状态栏高度变化由 `safeAreaForTopBar()` 处理 | ✅ 动态适配 |
| **横屏** | 状态栏高度变化由系统 WindowInsets 处理 | ✅ 动态适配 |

## 4. 数据流

本次调整不涉及数据流变更：
- `searchRevealProgress` 仍由 `LazyListState` 滚动状态驱动
- 批量模式仍由 `isBatchMode` StateFlow 控制
- 搜索词仍由 `searchQuery` StateFlow 控制

## 5. 错误处理

| 风险 | 应对 |
|------|------|
| dimens 资源缺失 | 编译期直接报错（IDE 红色波浪线） |
| 滚动动画异常 | `searchRevealProgress` 作用于外层 `Box` 的 `layout { }`，与 padding 调整正交 |
| 异形屏安全区穿透 | `safeAreaForTopBar()` 已处理状态栏 padding，无需额外处理 |
| 视觉过于紧凑 | 保留底部 8dp 间距，形成"上紧下松"的层次感 |

## 6. 测试方案

### 6.1 单元测试

无（纯 UI 间距调整，不涉及业务逻辑）。

### 6.2 手动测试清单

| # | 测试场景 | 验证方法 | 预期结果 |
|---|---------|---------|---------|
| 1 | 默认状态对齐 | Layout Inspector 测量两个 View 边界 | 差值 = 0px |
| 2 | 输入搜索词 | 观察搜索框上边缘 | 仍与导航栏底部对齐 |
| 3 | 滚动列表 | 滚动中观察搜索框 | 顶部锚点不漂移 |
| 4 | 滚动到顶 | 完全展开后观察 | 顶部精确对齐 |
| 5 | 批量模式 | 长按进入批量模式 | 搜索栏无残留间隙 |
| 6 | Tab 切换 | 切到灵感/日期/我的 | 导航栏与内容区紧贴 |
| 7 | 旋转屏幕 | 旋转到横屏 | 对齐关系保持 |
| 8 | 异形屏模拟 | 开发者选项切换刘海类型 | 对齐关系保持 |

### 6.3 验证工具

- **Layout Inspector**（Android Studio）：查看 View 树边界
- **Compose Layout Inspector**：实时查看 Composable 尺寸
- **可视化检查**：目视确认"零间隙"视觉效果

## 7. 兼容性

- **API Level**：无影响
- **多语言**：无影响
- **深色模式**：无影响（间距与主题无关）
- **平板/折叠屏**：自动适配（基于 `WindowInsets` 相对布局）

## 8. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 搜索栏顶部贴顶影响视觉层次 | 低 | 保留底部 8dp 间距 |
| 异形屏安全区穿透 | 极低 | `safeAreaForTopBar()` 已处理 |
| 滚动动画异常 | 极低 | `searchRevealProgress` 作用于外层 `Box`，与 padding 正交 |
| 跨设备像素精度差异 | 极低 | 使用 dp 单位（非 px），由系统自动按密度换算 |

## 9. 实施步骤

1. **修改 dimens.xml**：新增 `ui_search_bar_bottom_margin = 8dp`
2. **修改 HomeScreen.kt**：
   - 添加 `import androidx.compose.ui.res.dimensionResource`
   - 调整 SearchBar 的 modifier，去掉 `vertical = 8.dp`，改为 `bottom = dimensionResource(...)`
3. **手动验证**：按照第 6.2 节测试清单逐项验证
4. **提交代码**：按项目 git 提交规范，使用中文 commit message

## 10. 未来优化方向

- 将对齐关系抽象为 `AlignedToolbar` 通用组件，供未来页面复用
- 使用 `WindowInsets` API 进一步精确控制安全区域
- 添加 `onGloballyPositioned` 自动化测试，断言像素级对齐
