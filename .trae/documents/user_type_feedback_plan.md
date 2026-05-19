# 身份切换即时反馈功能实现计划

## 1. 仓库调研结论

### 现有代码结构

| 文件                                                                                                                                                         | 说明                                                            |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------- |
| [UserType.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/model/UserType.kt)                                         | 用户类型枚举（WORKER/STUDENT）                                  |
| [CorgiPreferences.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt#L99-L102) | 已有 `userType` Flow 和 `saveUserType()` 方法               |
| [SettingsScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt)               | 设置页面（目前只有音效/触觉反馈开关）                           |
| [SettingsViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/SettingsViewModel.kt)                   | 设置页面 ViewModel（目前只有音效/触觉反馈状态）                 |
| [MoodManager.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/animation/MoodManager.kt#L260-L297)                     | `GreetingManager` 对象在其中，提供 `getGreeting()` 方法     |
| [CategoryRepository.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt#L28-L38)   | 初始化默认分类（学习、工作、生活）                              |
| [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L287-L289)                 | `updateGreeting()` 方法调用 `GreetingManager.getGreeting()` |

### 现有功能

| 功能             | 状态    | 说明                                                |
| ---------------- | ------- | --------------------------------------------------- |
| UserType 枚举    | ✅ 已有 | WORKER（上班族）和 STUDENT（学生）                  |
| DataStore 存储   | ✅ 已有 | `userType` Flow 和 `saveUserType()` 方法        |
| 设置页面入口     | ❌ 缺失 | SettingsScreen 目前没有身份切换入口                 |
| 身份切换确认弹窗 | ❌ 缺失 | 需要实现                                            |
| 问候语预览       | ❌ 缺失 | 需要实现                                            |
| 身份切换即时更新 | ❌ 缺失 | 切换后首页问候语和分类不会自动更新                  |
| 分类过滤         | ❌ 缺失 | 首页目前没有按分类过滤的功能（只有待办/已完成过滤） |

### 关于"分类列表自动切换"的说明

当前首页（[HomeScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)）：

- 有"全部/待办/已完成"三个标签切换待办状态
- **没有**分类过滤功能（如学习/工作/生活）
- 默认分类在首次启动时已创建（学习、工作、生活）

**决策**：

- 由于没有分类过滤功能，"分类列表自动切换"暂时无法实现
- 可以在切换身份时，在确认弹窗中预览对应的问候语

---

## 2. 需求分析

### 功能需求

1. **设置页面身份切换入口**

   - 在设置页面添加"身份设置"区域
   - 显示当前身份（上班族/学生）
   - 点击后弹出身份选择弹窗
2. **身份切换确认弹窗**

   - 标题："切换身份"
   - 两个选项卡片：上班族 💼 / 学生 📚
   - 选中后显示确认弹窗："确定要切换为 [上班族/学生] 吗？"
   - 确认弹窗中预览切换后的问候语示例
3. **问候语个性化**

   - 上班族问候语示例："工作辛苦啦！记得休息一下哦"
   - 学生问候语示例："学习加油！劳逸结合很重要~"
   - 需要扩展 `GreetingManager.getGreeting()` 支持身份参数
4. **即时反馈**

   - 确认切换后更新 DataStore
   - 返回首页时问候语自动更新
   - （分类过滤待后续实现）

---

## 3. 文件变更列表

| 文件                                                                          | 操作 | 说明                                                                    |
| ----------------------------------------------------------------------------- | ---- | ----------------------------------------------------------------------- |
| `app/src/main/java/com/corgimemo/app/animation/MoodManager.kt`              | 修改 | 扩展 `GreetingManager.getGreeting()` 支持身份参数，添加身份专属问候语 |
| `app/src/main/java/com/corgimemo/app/viewmodel/SettingsViewModel.kt`        | 修改 | 添加 `userType` 状态、`setUserType()` 方法                          |
| `app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt` | 修改 | 添加"身份设置"区域、身份选择弹窗、确认弹窗                              |
| `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`            | 修改 | 监听 `userType` 变化，自动刷新问候语                                  |

---

## 4. 实现步骤

### 步骤 1：扩展 GreetingManager 支持身份参数

修改 [MoodManager.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/animation/MoodManager.kt#L269-L281)：

**当前签名：**

```kotlin
fun getGreeting(mood: CorgiMood, name: String? = null): String
```

**新签名：**

```kotlin
fun getGreeting(
    mood: CorgiMood, 
    name: String? = null, 
    userType: UserType = UserType.WORKER
): String
```

**身份专属问候语示例：**

| 身份   | NORMAL 情绪问候语示例           |
| ------ | ------------------------------- |
| 上班族 | "工作辛苦啦！记得休息一下哦 💼" |
| 学生   | "学习加油！劳逸结合很重要~ 📚"  |

### 步骤 2：修改 SettingsViewModel

修改 [SettingsViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/SettingsViewModel.kt)：

1. 注入 `CorgiPreferences`（已注入）
2. 添加 `_userType` MutableStateFlow
3. 在 `init` 中加载当前 userType
4. 添加 `setUserType(userType: UserType)` 方法

### 步骤 3：修改 SettingsScreen 添加身份切换入口

修改 [SettingsScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt)：

1. 添加状态变量：

   - `showUserTypeDialog` - 显示身份选择弹窗
   - `showConfirmDialog` - 显示确认弹窗
   - `pendingUserType` - 待确认的新身份
2. 添加"身份设置"区域（在现有开关之后）：

   - 显示当前身份
   - 点击后弹出身份选择弹窗
3. 身份选择弹窗：

   - 两个卡片：上班族 💼 / 学生 📚
   - 当前身份高亮显示
   - 选择其他身份后弹出确认弹窗
4. 确认弹窗：

   - 标题："切换身份"
   - 消息："确定要切换为 [上班族/学生] 吗？"
   - 预览切换后的问候语示例
   - 确认/取消按钮

### 步骤 4：修改 HomeViewModel 监听身份变化

修改 [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt)：

1. 注入 `CorgiPreferences`（检查是否已注入）
2. 添加 `_userType` MutableStateFlow
3. 监听 `corgiPreferences.userType` 变化
4. 当 userType 变化时，调用 `updateGreeting()` 刷新问候语

---

## 5. 潜在依赖和考虑事项

### 5.1 GreetingManager 扩展方案

**方案对比：**

| 方案                     | 说明                                             | 优缺点                             |
| ------------------------ | ------------------------------------------------ | ---------------------------------- |
| 方案 A：修改现有方法签名 | `getGreeting(mood, name, userType)`            | 调用点需要修改，但更整洁           |
| 方案 B：新增重载方法     | `getGreetingForUserType(mood, name, userType)` | 保持向后兼容，不需要修改所有调用点 |

**建议采用方案 B**：

- 新增 `getGreetingForUserType()` 方法
- 保留现有 `getGreeting()` 方法（默认使用上班族问候语）
- 首页调用新方法，其他地方使用默认方法

### 5.2 分类过滤功能缺失

当前首页没有分类过滤功能，无法实现"分类列表自动切换为对应身份的默认分类"。

**处理方式：**

- 本次实现中跳过此功能
- 在计划中明确说明此功能待后续分类过滤功能上线后再实现

### 5.3 问候语预览生成

在确认弹窗中需要预览切换后的问候语：

- 可以使用固定的问候语示例（不依赖当前情绪）
- 或者使用 `CorgiMood.NORMAL` + 新身份生成预览

---

## 6. 风险处理

| 风险                                  | 影响                   | 缓解措施                                 |
| ------------------------------------- | ---------------------- | ---------------------------------------- |
| GreetingManager 修改影响现有调用      | 编译错误或问候语不显示 | 采用方案 B（新增方法重载），保持向后兼容 |
| HomeViewModel 未注入 CorgiPreferences | 无法监听 userType 变化 | 检查并添加注入                           |
| 确认弹窗预览不准确                    | 用户体验差             | 使用固定的示例问候语，确保准确性         |
| 用户频繁切换身份                      | 多次更新 DataStore     | DataStore 写操作不频繁，风险低           |

---

## 7. 实施后的验证点

1. **设置页面入口**：

   - 进入设置页面能看到"身份设置"区域
   - 显示当前身份
2. **身份选择弹窗**：

   - 点击"身份设置"弹出选择弹窗
   - 显示上班族和学生两个选项
   - 当前身份高亮
3. **确认弹窗**：

   - 选择其他身份后弹出确认弹窗
   - 显示"确定要切换为 [身份] 吗？"
   - 显示切换后的问候语预览
4. **即时反馈**：

   - 确认切换后更新 DataStore
   - 返回首页问候语已更新为新身份对应的问候语

---

## 8. 与原需求的差异说明

| 差异项           | 原需求                 | 实际实现计划 | 说明                                 |
| ---------------- | ---------------------- | ------------ | ------------------------------------ |
| 分类列表自动切换 | 切换后分类列表自动切换 | 跳过         | 首页目前没有分类过滤功能，待后续实现 |
