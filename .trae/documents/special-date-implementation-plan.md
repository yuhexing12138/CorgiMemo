# 特殊日期记录功能 — 实施计划

> 设计文档: [2026-05-27-special-date-design.md](../specs/2026-05-27-special-date-design.md)
> 总计 20 个步骤，分 5 个阶段

---

## Phase 1: 数据层（7 步）

### Step 1: 创建 SpecialDate.kt Entity
**文件**: `app/src/main/java/com/corgimemo/app/data/model/SpecialDate.kt`
- Room Entity 注解，表名 `special_dates`
- 16 个字段：id, title, targetDate, category, countMode, repeatType, reminderDays, content, tags, imagePaths, imageUrls, isPinned, createdAt, updatedAt
- 所有可选字段加 `@ColumnInfo(defaultValue = "...")`
- 索引：targetDate, isPinned, category

### Step 2: 创建 SpecialDateRelation.kt Entity
**文件**: `app/src/main/java/com/corgimemo/app/data/model/SpecialDateRelation.kt`
- Room Entity 注解，表名 `special_date_relations`
- 字段：id, specialDateId(FK CASCADE), targetType, targetId, createdAt
- ForeignKey → SpecialDate.id ON DELETE CASCADE

### Step 3: 创建 SpecialDateDao.kt 接口
**文件**: `app/src/main/java/com/corgimemo/app/data/local/db/SpecialDateDao.kt`
- 12 个方法：CRUD + 查询（getAllFlow, getById, search, getCount, togglePin）

### Step 4: 创建 SpecialDateRelationDao.kt 接口
**文件**: `app/src/main/java/com/corgimemo/app/data/local/db/SpecialDateRelationDao.kt`
- 9 个方法：CRUD + 关联查询（getRelations Flow+blocking, isRelationExist, deleteRelation）

### Step 5: 创建 SpecialDateRepository.kt
**文件**: `app/src/main/java/com/corgimemo/app/data/repository/SpecialDateRepository.kt`
- 封装两个 DAO
- 提供 allDates Flow、CRUD、搜索、关联操作
- addRelation 去重逻辑

### Step 6: 修改 CorgiMemoDatabase.kt
**文件**: `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt`
- entities 数组添加 SpecialDate, SpecialDateRelation
- version: 17 → 18
- 添加 abstract fun specialDateDao(), specialDateRelationDao()
- 添加 MIGRATION_17_18（创建两张表 + 索引）
- addMigrations 列表添加 MIGRATION_17_18

### Step 7: 修改 DatabaseModule.kt
**文件**: `app/src/main/java/com/corgimemo/app/di/DatabaseModule.kt`
- 添加 import: SpecialDateDao, SpecialDateRelationDao
- 添加 @Provides provideSpecialDateDao()
- 添加 @Provides provideSpecialDateRelationDao()

---

## Phase 2: ViewModel（1 步）

### Step 8: 创建 SpecialDateViewModel.kt
**文件**: `app/src/main/java/com/corgimemo/app/viewmodel/SpecialDateViewModel.kt`
- 内部数据类 DisplayDate（id, title, daysRemaining, dayColor, groupType, displayText...）
- 枚举：GroupType(UPCOMING/CELEBRATING/EXPIRED), DayColor(RED/ORANGE/GRAY/GREEN), DateCategory
- StateFlow：specialDates, groupedDates, searchQuery, editingDate, relations, isLoading
- **核心算法**: computeEffectiveDate() 处理年度/月度重复
- **核心算法**: 三组分组（UPCOMING/CELEBRATING/EXPIRED）
- **核心算法**: 天数颜色规则（≤3红 / 4-30橙 / >30灰 / 正计绿）
- 公开方法：encodeTags, decodeTags, encodePaths, decodePaths, saveDate, deleteDate, togglePin

---

## Phase 3: 列表页 UI（4 步）

### Step 9: 创建 SpecialDateCard.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/SpecialDateCard.kt`
- 左侧圆形数字区（64dp，颜色随 dayColor 变化）
- 中间信息区（标题15sp、日期12sp、分类标签pill、关联提示）
- 右侧缩略图（40dp，仅当有图时显示）
- combinedClickable(onClick→编辑, onLongClick→菜单)

### Step 10: 创建 DateGroupHeader.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/DateGroupHeader.kt`
- 颜色圆点(4×14dp) + 文字 "即将到来·倒计时" / "正在纪念·正计时" / "已过期"
- 圆点颜色匹配组类型

### Step 11: 创建 SpecialDateEmptyState.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/SpecialDateEmptyState.kt`
- 📅 图标（100dp渐变背景圆角容器 + pulse动画）
- 主文案 "还没有特殊日期~"
- 副文案引导文字
- CTA按钮 "📅 添加日期"（渐变橙粉背景）

### Step 12: 创建 SpecialDateScreen.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt`
- TopAppBar("📅 特殊日期") + 搜索图标
- LazyColumn: 空→EmptyState; 有数据→分组渲染(DateGroupHeader + SpecialDateCard × N)
- FAB 导航到 date_edit
- 观察 viewModel.groupedDates

---

## Phase 4: 编辑页 UI（5 步）

### Step 13: 创建 DateCategoryPicker.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/DateCategoryPicker.kt`
- 4 个 FilterChip 横排：🎂生日 / 💕纪念日 / 🎉节日 / 📅其他
- 单选互斥

### Step 14: 创建 CountModeSwitch.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/CountModeSwitch.kt`
- SegmentedButton 或 Row(ChoiceChip)：⏳倒计时 / ⏱️正计时
- 默认选中倒计时

### Step 15: 创建 RepeatTypePicker.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/RepeatTypePicker.kt`
- ChoiceChip 行：不重复 / 按年重复 / 按月重复
- 默认不重复

### Step 16: 创建 ReminderSetting.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/components/ReminderSetting.kt`
- 条件显示组件（repeatType ≠ 0 时显示）
- 选择提前天数：1天 / 3天 / 7天 / 自定义输入

### Step 17: 创建 SpecialDateEditScreen.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateEditScreen.kt`
- TopAppBar: 取消 / 标题 / 保存
- 可滚动 Column 包含 10 个字段：
  1. 标题 OutlinedTextField
  2. 目标日期 DatePicker Dialog
  3. 分类 DateCategoryPicker
  4. 计时模式 CountModeSwitch
  5. 重复类型 RepeatTypePicker
  6. 提醒设置 ReminderSetting
  7. 备注 OutlinedTextArea
  8. 标签 TagInputField（复用灵感模块 import 路径）
  9. 图片 ImagePicker（复用灵感模块 import 路径）
  10. 关联 RelationSelector（复用灵感模块，targetType 加 "date"）
- 保存逻辑调用 viewModel.encodeTags()/encodePaths()

---

## Phase 5: 导航集成（3 步）

### Step 18: 修改 Screen.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt`
- 添加: `object SpecialDateEdit : Screen("date_edit")`
- 添加: `object SpecialDateEditWithId : Screen("date_edit/{specialDateId}")`

### Step 19: 修改 AppNavHost.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt`
- 添加 import: SpecialDateScreen, SpecialDateEditScreen
- 替换 Screen.Date.route composable: DateScreenPlaceholder → SpecialDateScreen
- 添加 SpecialDateEdit route composable
- 添加 SpecialDateEditWithId route composable（解析 specialDateId 参数）

### Step 20: 修改 MainScreen.kt
**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt`
- import 改为 SpecialDateScreen（移除 DateScreenPlaceholder import）
- TabItem.DATE 分支改为 `SpecialDateScreen(navController)`
- 确认 BubbleType.SPECIAL_DATE 路由为 `"date_edit"`（已一致则无需改）

---

## 文件变更清单

| 操作 | 文件路径 |
|------|---------|
| 新建 | `data/model/SpecialDate.kt` |
| 新建 | `data/model/SpecialDateRelation.kt` |
| 新建 | `data/local/db/SpecialDateDao.kt` |
| 新建 | `data/local/db/SpecialDateRelationDao.kt` |
| 新建 | `data/repository/SpecialDateRepository.kt` |
| 新建 | `viewmodel/SpecialDateViewModel.kt` |
| 新建 | `ui/screens/date/SpecialDateScreen.kt` |
| 新建 | `ui/screens/date/SpecialDateEditScreen.kt` |
| 新建 | `ui/screens/date/components/SpecialDateCard.kt` |
| 新建 | `ui/screens/date/components/DateGroupHeader.kt` |
| 新建 | `ui/screens/date/components/SpecialDateEmptyState.kt` |
| 新建 | `ui/screens/date/components/DateCategoryPicker.kt` |
| 新建 | `ui/screens/date/components/CountModeSwitch.kt` |
| 新建 | `ui/screens/date/components/RepeatTypePicker.kt` |
| 新建 | `ui/screens/date/components/ReminderSetting.kt` |
| 修改 | `data/local/db/CorgiMemoDatabase.kt` |
| 修改 | `di/DatabaseModule.kt` |
| 修改 | `ui/navigation/Screen.kt` |
| 修改 | `ui/navigation/AppNavHost.kt` |
| 修改 | `ui/screens/main/MainScreen.kt` |

**总计: 15 个新建文件 + 5 个修改文件 = 20 个操作**
