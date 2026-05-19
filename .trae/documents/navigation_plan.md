# CorgiMemo 导航配置实现计划

## 当前项目分析

**已有资源：**
- ✅ HomeScreen 待办列表页面已实现
- ✅ TodoEditScreen 待办编辑页面已实现
- ✅ Navigation Compose 依赖已添加

**需要实现的内容：**
1. 创建路由枚举类
2. 配置 NavHost
3. 实现导航跳转逻辑
4. 创建 ProfileScreen 个人中心页面

---

## 实现步骤

### 1. 创建路由枚举类
**文件：** `ui/navigation/Screen.kt`

**功能：**
- 定义所有页面路由常量
- 提供路由路径和参数解析方法

### 2. 创建 NavHost 配置
**文件：** `ui/navigation/AppNavHost.kt`

**功能：**
- 配置所有页面路由
- 设置默认启动页面
- 处理参数传递

### 3. 创建 ProfileScreen 页面
**文件：** `ui/screens/profile/ProfileScreen.kt`

**功能：**
- 显示用户信息
- 显示柯基宠物状态
- 显示统计数据

### 4. 更新 MainActivity
**文件：** `ui/MainActivity.kt`

**功能：**
- 集成 AppNavHost
- 移除旧的导航配置

---

## 路由配置

| 路由名称 | 路径 | 参数 | 说明 |
|----------|------|------|------|
| HOME | `home` | 无 | 待办列表首页 |
| TODO_EDIT | `todo_edit/{todoId}` | todoId (可选) | 待办编辑页面 |
| PROFILE | `profile` | 无 | 个人中心页面 |

---

## 修改的文件

| 文件路径 | 修改内容 |
|----------|----------|
| `ui/navigation/Screen.kt` | 新建 - 路由枚举类 |
| `ui/navigation/AppNavHost.kt` | 新建 - NavHost 配置 |
| `ui/screens/profile/ProfileScreen.kt` | 新建 - 个人中心页面 |
| `ui/MainActivity.kt` | 修改 - 集成导航 |

---

## 设计规范

- 使用 Jetpack Navigation Compose
- 路由名称使用枚举类管理
- 支持参数传递（如 todoId）
- 保持代码结构清晰
