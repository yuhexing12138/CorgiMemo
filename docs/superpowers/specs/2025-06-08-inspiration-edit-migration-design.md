# 待办编辑页 → 灵感编辑页 功能迁移设计文档

> 日期: 2025-06-08
> 方案: **方案 A — 完整复制 + 适配**
> 状态: **已批准**

## 1. 目标

将 `TodoEditScreen` 的全部功能（1840 行 / 20+ 模块）完整移植到 `InspirationEditScreen`，
同时**保留灵感编辑页独有的 TagInputField 标签功能**。后续将独立重新设计待办编辑页。

## 2. 迁移范围

### 2.1 从 TodoEditScreen 迁移的全部功能

| #  | 功能模块   | 说明                                                  |
| -- | ------ | --------------------------------------------------- |
| 1  | 标题编辑   | 单行大标题输入框，22sp 粗体                                    |
| 2  | 分类管理   | CategoryPickerSheet + AI 智能推荐分类                     |
| 3  | 优先级设置  | 低/中/高 三级优先级切换                                       |
| 4  | 富文本编辑  | RichTextEditor（粗体/斜体/下划线/删除线/有序/无序列表）               |
| 5  | 内容块系统  | Text/Image/Voice 混合 ContentBlock 流                  |
| 6  | 图片管理   | 相机拍照 + 相册多选 + 压缩 + InlineImagePreview 懒加载           |
| 7  | 语音备注   | VoiceRecordBottomSheet 录制 + VoicePlayerComponent 播放 |
| 8  | 时间管理   | 开始日期/截止日期/提醒时间/重复类型/预计时长                            |
| 9  | 地理围栏   | LocationPicker 位置提醒（经纬度/半径/地址/类型）                   |
| 10 | 子任务系统  | SubTaskManager 完整增删改查 + 完成切换                        |
| 11 | 撤销/重做  | 扩展版双栈（文本变更+内容块插入/删除/排序）                             |
| 12 | 两步删除   | 光标位置感知的 Backspace/Delete 删除逻辑                       |
| 13 | 拖拽排序   | ReorderableColumn 长按拖拽内容块                           |
| 14 | 关联功能   | @ MentionTriggerPopup 关联其他卡片                        |
| 15 | 关键词提取  | KeywordSelectionDialog                              |
| 16 | 背景色自定义 | ColorPickerBottomSheet 12 色预设                       |
| 17 | 锁定模式   | Lock/LockOpen 编辑锁定开关                                |
| 18 | 语音识别   | SpeechViewModel 语音转文字输入标题                           |
| 19 | 编辑历史恢复 | NavResult API + AnnotatedString 格式化                 |
| 20 | 格式化工具栏 | TextFormatToolbar 条件显示                              |
| 21 | 底部工具栏  | EditToolbar 8 图标按钮横排                                |
| 22 | 可见性优化  | onVisibilityChanged 懒加载                             |

### 2.2 从 InspirationEditScreen 保留的功能

| # | 功能模块                   | 说明                              |
| - | ---------------------- | ------------------------------- |
| ★ | **TagInputField 标签系统** | 横向滚动标签列表 + 内联添加输入框，集成在分类/优先级行之后 |

## 3. 数据模型变更

### 3.1 Inspiration 实体扩展

**当前字段 (11 个)**: id, title, content, tags, imagePaths, imageUrls, createdAt, updatedAt, isPinned, isArchived

**新增字段 (20 个)**:

| 字段名                      | 类型      | 默认值   | 来源                                |
| ------------------------ | ------- | ----- | --------------------------------- |
| categoryId               | Long    | 0     | TodoItem.categoryId               |
| priority                 | Int     | 0     | TodoItem.priority                 |
| status                   | Int     | 0     | TodoItem.status                   |
| startDate                | Long?   | null  | TodoItem.startDate                |
| dueDate                  | Long?   | null  | TodoItem.dueDate                  |
| estimatedDurationMinutes | Int?    | null  | TodoItem.estimatedDurationMinutes |
| reminderTime             | Long?   | null  | TodoItem.reminderTime             |
| repeatType               | Int     | 0     | TodoItem.repeatType               |
| completedAt              | Long?   | null  | TodoItem.completedAt              |
| geofenceLat              | Double? | null  | TodoItem.geofenceLat              |
| geofenceLng              | Double? | null  | TodoItem.geofenceLng              |
| geofenceRadius           | Float?  | null  | TodoItem.geofenceRadius           |
| geofenceType             | Int?    | null  | TodoItem.geofenceType             |
| geofenceEnabled          | Boolean | false | TodoItem.geofenceEnabled          |
| geofenceAddress          | String? | null  | TodoItem.geofenceAddress          |
| hasSubTasks              | Boolean | false | TodoItem.hasSubTasks              |
| voiceNotePath            | String? | null  | TodoItem.voiceNotePath            |
| voiceDuration            | Int?    | null  | TodoItem.voiceDuration            |
| backgroundColor          | Int     | -1    | TodoItem.backgroundColor          |
| position                 | Int     | 0     | TodoItem.position                 |
| contentFormat            | String  | ""    | TodoItem.contentFormat            |

**扩展后总计: 31 个字段**

### 3.2 数据库迁移

* **版本**: 26 → 27

* **迁移名称**: MIGRATION\_26\_27

* **操作**: ALTER TABLE inspirations ADD COLUMN 为每个新字段添加列

* **ContentBlock**: 现有 content\_blocks 表通过 todoId 外键关联，需增加 inspirationId 支持或新增 sourceType 列区分来源

### 3.3 新增索引建议

```kotlin
indices = [
    Index(value = ["categoryId"]),           // 新增
    Index(value = ["priority"]),             // 新增
    Index(value = ["status", "createdAt"]),   // 新增
    Index(value = ["dueDate", "status"]),     // 新增
    Index(value = ["createdAt"]),            // 已有
    Index(value = ["isPinned"])              // 已有
]
```

## 4. 文件变更清单

| 序号 | 文件路径                                              | 操作       | 说明                                   |
| -- | ------------------------------------------------- | -------- | ------------------------------------ |
| 1  | `data/model/Inspiration.kt`                       | **修改**   | 新增 20 个字段 + 更新 @Entity 索引            |
| 2  | `data/local/db/InspirationDao.kt`                 | **修改**   | 按需新增查询方法                             |
| 3  | `data/local/db/CorgiMemoDatabase.kt`              | **修改**   | version=27 + MIGRATION\_26\_27       |
| 4  | `viewmodel/InspirationEditViewModel.kt`           | **新建**   | 基于 TodoEditViewModel 适配 Inspiration  |
| 5  | `ui/screens/inspiration/InspirationEditScreen.kt` | **重写**   | 基于 TodoEditScreen + TagInputField 集成 |
| 6  | `di/DatabaseModule.kt`                            | **可能修改** | 如有新增 DAO 则注册                         |

## 5. UI 布局结构

```
Scaffold (containerColor = 暖米色背景)
├── topBar (自定义 Row 工具栏)
│   ├── [返回] ArrowBack
│   ├── [撤销] Undo + [重做] Redo
│   ├── [弹性空间]
│   ├── [通知图标] DropdownMenu (开始/截止/提醒/重复时间)
│   ├── [锁定] Lock/LockOpen
│   └── [完成按钮]
├── snackbarHost
├── bottomBar (EditToolbar)
│   └── 文本/列表/照片/语音/子任务/背景/分享/删除 + 字数统计
└── content (Column + verticalScroll)
    ├── 标题区 OutlinedTextField (22sp Bold)
    ├── 分类 + 优先级行 (Row)
    │   ├── 分类 Chip (CategoryPickerSheet)
    │   └── 低/中/高 优先级文字
    ├── ★ TagInputField (标签系统) ← 灵感独有保留
    │   ├── LazyRow 横向标签列表 (TagChip #FFF3E0)
    │   └── [+ 添加] 内联输入框
    ├── 动态内容流 (ReorderableColumn)
    │   ├── InlineImagePreview (懒加载)
    │   └── VoicePlayerComponent (懒加载)
    ├── RichTextEditor (富文本, min 200dp)
    ├── TextFormatToolbar (条件显示)
    ├── MentionTriggerPopup (@关联)
    ├── LocationPickerDialog (#位置)
    ├── AddSubtaskDialog (子任务)
    ├── RecommendationChip (AI推荐)
    ├── ColorPickerBottomSheet (背景色)
    └── 辅助UI (警告/时长计算等)
```

## 6. ViewModel 设计要点

### 6.1 InspirationEditViewModel (基于 TodoEditViewModel 适配)

核心差异点:

* 数据实体: `TodoItem` → `Inspiration`

* DAO 操作: `todoDao` → `inspirationDao`

* 内容块外键: `todoId` → `inspirationId`

* 关联类型常量: `TARGET_TODO` → `TARGET_INSPIRATION`

* 保留 tags 的 StateFlow 和编解码方法（来自原 InspirationViewModel）

* 所有 34 个 StateFlow 字段保持一致

### 6.2 状态字段清单 (34 个 StateFlow)

title, content, categoryId, priority, startDate, dueDate,
estimatedDurationMinutes, repeatType, geofenceLat, geofenceLng,
geofenceRadius, geofenceType, geofenceEnabled, geofenceAddress,
subTasks, categories, recommendedCategory, hasManuallySelectedCategory,
showKeywordSelection, extractedKeywords, isCategoriesLoaded,
reminderTime, recommendedReminderTime, showReminderRecommendation,
voiceNotePath, voiceDuration, imagePaths, currentContentBlocks,
backgroundColor, contentFormat, canUndo, canRedo, relations,
tags (灵感独有)

## 7. 复用组件清单

以下 `ui/components/` 下的组件直接复用，无需修改:

* RichTextEditor / RichTextEditorState

* TextFormatToolbar

* EditToolbar

* InlineImagePreview

* VoicePlayerComponent

* VoiceRecordBottomSheet

* ReorderableColumn

* ImagePickerDialog

* ColorPickerBottomSheet

* MentionTriggerPopup

* LocationPicker

* KeywordSelectionDialog

* RecommendationChip

* CategoryPickerSheet

* RecordAudioPermissionChecker

灵感专属组件保留:

* TagInputField (含 TagChip) — `ui/screens/inspiration/components/`

* ImagePicker — 可考虑替换为 Todo 版本的图片管理

* RelationSelector — 保留增强版关联管理

## 8. 实施顺序

1. **数据层**: 扩展 Inspiration 实体 + MIGRATION\_26\_27 + InspirationDao 更新
2. **ViewModel 层**: 创建 InspirationEditViewModel（从 TodoEditViewModel 复制适配）
3. **UI 层**: 重写 InspirationEditScreen（从 TodoEditScreen 复制 + 集成 TagInputField）
4. **验证**: 编译通过 + 运行时新建/编辑灵感功能正常

