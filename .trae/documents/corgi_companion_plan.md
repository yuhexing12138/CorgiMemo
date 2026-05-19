# 柯基陪伴系统实现计划

## 一、需求分析

根据业务/产品描述，需要实现以下功能：

| 功能点 | 需求描述 | 优先级 |
| :--- | :--- | :--- |
| 柯基命名功能 | 首次打开APP时显示命名对话框 | M |
| 输入验证 | 输入框限制1-8个字符 | M |
| 数据存储 | 保存到DataStore | M |
| 名字展示 | 在首页显示柯基名字 | M |
| 命名对话框 | 独立的Composable组件 | M |
| 首页柯基UI | 显示柯基的UI组件 | M |

## 二、现有代码分析

### 2.1 项目架构

项目采用MVVM四层结构：
- **UI层**：Jetpack Compose（已存在）
- **ViewModel层**：StateFlow管理状态（已存在）
- **Repository层**：数据协调（已存在`CorgiRepository`）
- **Data层**：Room数据库（已存在`CorgiData`实体）

### 2.2 相关文件

| 文件路径 | 说明 |
| :--- | :--- |
| `data/model/CorgiData.kt` | 柯基数据实体，包含name字段 |
| `data/repository/CorgiRepository.kt` | 柯基数据仓库 |
| `ui/screens/home/HomeScreen.kt` | 首页界面 |
| `viewmodel/HomeViewModel.kt` | 首页视图模型 |

### 2.3 现有能力

- `CorgiData`实体已包含`name`字段
- `CorgiRepository`已实现数据读写方法
- 数据库已配置完成

## 三、实现方案

### 3.1 技术选型

| 分类 | 技术 | 说明 |
| :--- | :--- | :--- |
| 本地存储 | DataStore | 存储首次启动标志和柯基名字 |
| UI组件 | Jetpack Compose | 命名对话框、柯基展示组件 |
| 状态管理 | StateFlow | 管理命名状态和柯基数据 |

### 3.2 新增文件

| 文件路径 | 用途 |
| :--- | :--- |
| `data/local/datastore/CorgiPreferences.kt` | DataStore存储管理类 |
| `ui/components/CorgiNamerDialog.kt` | 命名对话框组件 |
| `ui/components/CorgiCompanion.kt` | 首页柯基展示组件 |

### 3.3 修改文件

| 文件路径 | 修改内容 |
| :--- | :--- |
| `viewmodel/HomeViewModel.kt` | 新增柯基数据获取和命名逻辑 |
| `ui/screens/home/HomeScreen.kt` | 添加柯基展示组件和命名对话框 |

## 四、实现步骤

### 步骤1：创建DataStore存储管理类

**目标**：实现DataStore存储逻辑，保存柯基名字和首次启动标志

**文件**：`data/local/datastore/CorgiPreferences.kt`

**实现要点**：
- 使用`PreferencesDataStore`存储键值对
- 定义`corgiName`和`isFirstLaunch`两个key
- 提供读取和写入方法

### 步骤2：创建命名对话框组件

**目标**：创建柯基命名对话框Composable

**文件**：`ui/components/CorgiNamerDialog.kt`

**实现要点**：
- 使用`AlertDialog`组件
- 输入框限制1-8个字符
- 确认按钮在输入有效时可点击
- 包含可爱的柯基图标或插画

### 步骤3：创建柯基展示组件

**目标**：创建首页显示柯基的UI组件

**文件**：`ui/components/CorgiCompanion.kt`

**实现要点**：
- 显示柯基形象（使用emoji或矢量图）
- 显示柯基名字
- 显示等级和经验值
- 添加交互动画效果

### 步骤4：修改HomeViewModel

**目标**：集成柯基数据获取和命名逻辑

**文件**：`viewmodel/HomeViewModel.kt`

**实现要点**：
- 注入`CorgiRepository`和`CorgiPreferences`
- 添加`corgiData` StateFlow
- 添加`showNamerDialog` StateFlow
- 实现初始化检查逻辑
- 实现保存名字方法

### 步骤5：修改HomeScreen

**目标**：整合柯基组件到首页

**文件**：`ui/screens/home/HomeScreen.kt`

**实现要点**：
- 添加柯基展示组件在顶部
- 显示命名对话框（首次启动时）
- 响应式布局适配不同屏幕

## 五、测试验证

| 测试场景 | 验证内容 |
| :--- | :--- |
| 首次启动 | 显示命名对话框 |
| 输入验证 | 空输入和超过8字符时按钮禁用 |
| 名字保存 | 保存后下次启动不再显示对话框 |
| 名字显示 | 首页正确显示柯基名字 |
| 深色模式 | 组件适配深色模式 |

## 六、依赖与配置

**新增依赖**：DataStore已在AndroidX中提供，无需额外依赖

**权限**：无需额外权限

---

**计划确认**：请确认此实现计划，确认后开始执行。