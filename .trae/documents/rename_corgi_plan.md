# 柯基名字修改功能实现计划

## 1. 仓库调研结论

### 现有代码结构

| 文件 | 说明 |
|------|------|
| [ProfileScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt) | "我的"页面，显示柯基信息、成就、装扮等 |
| [ProfileViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/ProfileViewModel.kt) | 个人中心 ViewModel，管理柯基数据、装扮等 |
| [CorgiNamerDialog.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/components/CorgiNamerDialog.kt) | 现有命名对话框，支持 1-8 字符验证 |
| [CorgiPreferences.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt#L107-L111) | 已有 `saveCorgiName()` 方法，可直接用于覆盖更新 |
| [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L295-L312) | 有 `saveCorgiName()` 方法，但逻辑是首次创建而非修改 |

### 现有功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 命名对话框组件 | ✅ 已有 | [CorgiNamerDialog.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/components/CorgiNamerDialog.kt) |
| 1-8 字符验证 | ✅ 已有 | 对话框内置验证逻辑 |
| DataStore 保存名字 | ✅ 已有 | `saveCorgiName()` 方法（覆盖式写入） |
| Room 数据库名字字段 | ✅ 已有 | `CorgiData.name` |
| 修改名字入口 | ❌ 缺失 | 需要在 ProfileScreen 添加 |
| 确认修改提示 | ❌ 缺失 | 需要实现 |
| 敏感词汇过滤 | ❌ 缺失 | 需要实现 |

---

## 2. 需求分析

### 功能需求

1. **修改名字入口**
   - 在"我的"页面添加"柯基设置"区域
   - 区域内添加"修改名字"选项（可点击的设置项样式）

2. **修改名字对话框**
   - 复用现有 [CorgiNamerDialog.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/components/CorgiNamerDialog.kt)
   - 标题改为"修改柯基名字"
   - 输入框预填充当前名字

3. **输入验证**
   - 长度验证：1-8 个字符（已有）
   - 敏感词汇过滤：新增

4. **确认修改**
   - 弹出确认对话框："确定要将柯基的名字改为 [xxx] 吗？"

5. **数据保存**
   - DataStore：调用 `saveCorgiName()`（已有，覆盖式）
   - Room：更新 `CorgiData.name` 字段

6. **即时更新**
   - 修改后"我的"页面柯基名字立即更新
   - 返回首页时首页柯基名字也更新

---

## 3. 文件变更列表

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt` | 修改 | 添加"柯基设置"区域和"修改名字"入口，集成对话框 |
| `app/src/main/java/com/corgimemo/app/viewmodel/ProfileViewModel.kt` | 修改 | 注入 CorgiPreferences，添加改名逻辑（DataStore + Room）、敏感词过滤 |
| `app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt` | 修改 | 添加 `updateCorgiName()` 方法更新 Room 数据库名字 |

---

## 4. 实现步骤

### 步骤 1：CorgiRepository 添加 updateCorgiName 方法

- 修改 [CorgiRepository.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt)
- 检查 Room DAO 是否已有更新名字的方法
- 如果没有，需要在 DAO 中添加 `updateName(id: Long, name: String)` 方法
- 在 Repository 中封装 `updateCorgiName(name: String)`

### 步骤 2：ProfileViewModel 添加改名逻辑

- 修改 [ProfileViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/ProfileViewModel.kt)
- 注入 `CorgiPreferences`
- 添加敏感词汇列表（如脏话、敏感词等）
- 添加方法：
  - `validateName(name: String): Boolean` - 验证名字（长度 + 敏感词）
  - `updateCorgiName(name: String)` - 更新名字（DataStore + Room）

### 步骤 3：ProfileScreen 添加柯基设置区域和入口

- 修改 [ProfileScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt)
- 参考现有 `SettingSwitchCard` 风格，创建 `SettingItemCard` 组件（文字 + 箭头）
- 在页面中部添加"柯基设置"卡片区域
- 区域内添加"修改名字"设置项
- 添加对话框状态管理：
  - `showRenameDialog` - 显示改名输入对话框
  - `showConfirmDialog` - 显示确认对话框

### 步骤 4：集成改名流程

**流程设计：**

```
点击"修改名字"
    ↓
显示改名输入对话框（预填当前名字）
    ↓
用户输入新名字（1-8 字符限制）
    ↓
点击"确认"
    ↓
敏感词汇过滤
    ├─ 包含敏感词 → 显示错误提示，停留在输入对话框
    └─ 无敏感词 → 显示确认对话框："确定要将柯基的名字改为 [xxx] 吗？"
                      ↓
                 确认 → 保存名字（DataStore + Room）→ 关闭对话框 → UI 刷新
                      ↓
                 取消 → 返回输入对话框
```

- 在 ProfileScreen 中集成现有 `CorgiNamerDialog`
- 修改对话框标题和按钮文字（"确认" → "下一步"，"稍后" → "取消"）
- 或创建新的改名专用对话框组件

---

## 5. 潜在依赖和考虑事项

### 5.1 Room DAO 更新方法

需要检查 [CorgiDao](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo) 是否已有更新名字的方法。如果没有，需要添加：

```kotlin
@Query("UPDATE corgi_data SET name = :name WHERE id = :id")
suspend fun updateName(id: Long, name: String)
```

### 5.2 名字同步问题

当前柯基名字存储在两个地方：
- **DataStore**：`CorgiPreferences.corgiName` - 用于快速读取
- **Room**：`CorgiData.name` - 持久化存储

需要确保修改时**同时更新两个数据源**。

### 5.3 敏感词汇过滤

需要定义敏感词汇列表，建议放在单独的常量文件或直接放在 ViewModel 中。

**示例敏感词列表：**
```kotlin
private val sensitiveWords = listOf("傻逼", "fuck", "nigger", ...)
```

### 5.4 UI 刷新机制

修改名字后需要确保：
- ProfileScreen 的 `corgiData` Flow 自动更新（通过 `_corgiData.value = updatedData`）
- 首页通过 `CorgiRepository.getCorgiData()` 获取最新数据（Room 更新后首页下次读取会拿到新数据）

---

## 6. 风险处理

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| DAO 缺少更新方法 | 编译错误 | 先检查并添加必要的 DAO 方法 |
| DataStore 和 Room 不同步 | 显示旧名字 | 修改时确保同时更新两个数据源 |
| 敏感词列表不完善 | 漏过不良内容 | 维护可扩展的敏感词列表 |
| 对话框复用困难 | 额外工作 | 如现有对话框不适用，创建新的改名专用对话框 |

---

## 7. 实施后的验证点

1. **入口可见性**：ProfileScreen 中能看到"柯基设置"区域和"修改名字"选项
2. **输入限制**：对话框限制 1-8 字符，超出时按钮禁用
3. **敏感词过滤**：输入敏感词时显示错误提示
4. **确认对话框**：输入有效名字后弹出确认对话框
5. **数据保存**：修改后检查：
   - DataStore：`corgiName` 已更新
   - Room：`CorgiData.name` 已更新
6. **UI 刷新**：
   - ProfileScreen 柯基名字立即更新
   - 返回首页后首页柯基名字也更新
