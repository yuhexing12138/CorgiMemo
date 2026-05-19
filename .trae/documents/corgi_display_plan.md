# 柯基基础展示组件实现计划

## 一、需求分析

根据业务/产品描述，需要实现以下功能：

| 功能点 | 需求描述 | 优先级 |
| :--- | :--- | :--- |
| 柯基图片展示 | 显示柯基静态图片（占位图） | M |
| 名字显示 | 显示柯基名字 | M |
| 问候语显示 | 显示友好问候语 | M |
| 点击反馈 | 点击柯基显示Toast提示 | M |
| 圆角卡片布局 | 圆角卡片设计 | M |
| 暖橙色背景渐变 | 使用暖橙色渐变背景 | M |
| 居中显示 | 内容居中对齐 | M |

## 二、现有代码分析

现有 `CorgiCompanion.kt` 组件功能较复杂，包含等级、经验值、心情值等。用户需要一个简化版本：
- 仅显示图片、名字、问候语
- 点击显示Toast反馈
- 暖橙色渐变背景

## 三、实现方案

### 3.1 技术选型

| 分类 | 技术 | 说明 |
| :--- | :--- | :--- |
| 图片展示 | Image组件 | 使用占位图片URL |
| Toast提示 | Toast.makeText | Android原生Toast |
| 渐变背景 | Brush.linearGradient | Compose渐变效果 |
| 状态管理 | remember | 简单状态管理 |

### 3.2 修改文件

| 文件路径 | 修改内容 |
| :--- | :--- |
| `ui/components/CorgiCompanion.kt` | 重构为简化版，添加渐变背景和Toast反馈 |

### 3.3 设计规范

- **圆角**：16dp（遵循项目规范）
- **主色调**：暖橙色 FF9A5C
- **渐变方向**：从上到下
- **布局**：垂直居中，内容居中对齐

## 四、实现步骤

### 步骤1：重构CorgiCompanion组件

**目标**：创建简化版柯基展示组件

**实现要点**：
- 使用 `Brush.linearGradient` 创建暖橙色渐变背景
- 使用网络图片作为柯基占位图
- 显示柯基名字和问候语
- 点击时显示Toast提示

### 步骤2：添加必要的导入

**需要导入**：
- `android.widget.Toast`
- `android.content.Context`
- `androidx.compose.ui.platform.LocalContext`
- `androidx.compose.foundation.Image`
- `androidx.compose.ui.graphics.Brush`
- `coil.compose.rememberAsyncImagePainter`

### 步骤3：集成到HomeScreen

当前已集成，无需额外修改

## 五、测试验证

| 测试场景 | 验证内容 |
| :--- | :--- |
| 图片显示 | 柯基图片正确显示 |
| 名字显示 | 正确显示柯基名字 |
| 问候语显示 | 显示友好问候语 |
| 点击反馈 | 点击显示Toast提示 |
| 布局样式 | 圆角卡片、渐变背景、居中显示 |
| 深色模式 | 组件适配深色模式 |

---

**计划确认**：请确认此实现计划，确认后开始执行。