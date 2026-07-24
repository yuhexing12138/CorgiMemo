# 阶段2：删除灵感模块 reminderTime + repeatType 字段

## Context（背景）

用户原话：**"灵感页应该是不需要提醒时间以及所属的时间循环逻辑的，请完整删除。"**

**问题**：`Inspiration` 实体从 v27 借鉴了 `TodoItem` 的 `reminderTime` + `repeatType` 字段，但灵感页（轻量想法记录入口）从未有过 UI 入口设置/展示这两个值，导致：

- 实体字段冗余（每条灵感多 16 字节无效数据）
- ViewModel 死代码（`setReminderTime` / `setRepeatType` / `acceptReminderRecommendation` / `updateReminderRecommendation` 从未被 UI 调用）
- `ReminderRecommender` 在灵感模块空转
- `setDueDate` 中有"自动同步 reminderTime"联动逻辑（无意义）

**预期结果**：从数据库 schema、实体、ViewModel、UI 全链路**完整删除**这两个字段及相关代码。

---

## 已确认决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 数据库迁移 | **12-step 重建表** | `minSdk=26` 内置 SQLite 3.18 不支持 `ALTER TABLE DROP COLUMN`（需 API 31+/SQLite 3.35+） |
| 提醒推荐功能 | 随 reminder 一起删 | `_recommendedReminderTime` / `_showReminderRecommendation` / `updateReminderRecommendation()` 全部删除 |
| `ReminderRecommender` 文件 | 保留 | `TodoEditViewModel` 仍在用（L23, L94），仅删 `InspirationEditViewModel` 中的实例和 import |
| `setDueDate` 改动 | 只删联动 | dueDate 本身保留，删除"自动同步 reminderTime"那段（L469-472）和 KDoc 中相关描述 |

---

## 执行步骤

### 阶段 A：实体字段删除

**A1. `app/src/main/java/com/corgimemo/app/data/model/Inspiration.kt`**
- L88-89：删除 `val reminderTime: Long?` 字段（含 KDoc `/** 提醒时间（时间戳毫秒） */`）
- L91-93：删除 `val repeatType: Int = 0` 字段（含 `@ColumnInfo(defaultValue = "0")` 和 KDoc）

**A2. `app/src/main/java/com/corgimemo/app/data/model/DeletedInspiration.kt`**
- L29：删除 `val reminderTime: Long? = null,`
- L30：删除 `val repeatType: Int = 0,`
- L66-67：删除 `fromInspiration` 中两行
- L104-105：删除 `toInspiration` 中两行

---

### 阶段 B：Room Migration 编写

**B1. `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt`**

| 改动 | 位置 |
|------|------|
| `version = 45` → `version = 46` | L34 |
| `addMigrations(...)` 列表末尾追加 `, MIGRATION_45_TO_46` | L102 |
| 新增 `internal val MIGRATION_45_TO_46 = object : Migration(45, 46) { ... }` | 文件末尾（~L1294 之后） |

**MIGRATION_45_TO_46 完整 SQL（12-step 重建表）**：

1. `PRAGMA foreign_keys = OFF`（事务外）
2. `database.beginTransaction()`
3. `CREATE TABLE IF NOT EXISTS inspirations_new (...)` —— 完整 schema（不含 reminderTime/repeatType 两列）
4. `INSERT INTO inspirations_new (列清单) SELECT 列清单 FROM inspirations` —— 排除两列
5. `DROP TABLE IF EXISTS inspirations`
6. `ALTER TABLE inspirations_new RENAME TO inspirations`
7. 重建 7 个索引（createdAt/isPinned/categoryId/priority/status_createdAt/dueDate_status/title）
8-11. 重复 3-7 处理 `deleted_inspirations`（无索引，跳过步骤 7）
12. `database.endTransaction()` + `PRAGMA foreign_keys = ON`

**关键设计点**：
- 用 `try-finally + setTransactionSuccessful + endTransaction` 手动控制事务
- PRAGMA 在事务外（事务内不允许修改）
- 复制的列清单按新表 schema 顺序，**必须**排除 reminderTime 和 repeatType

**索引清单（重建顺序）**：
```
index_inspirations_createdAt
index_inspirations_isPinned
index_inspirations_categoryId
index_inspirations_priority
index_inspirations_status_createdAt  -- (status, createdAt) 联合索引
index_inspirations_dueDate_status    -- (dueDate, status) 联合索引
index_inspirations_title
```

**deleted_inspirations 无索引**，无需重建。

---

### 阶段 C：ViewModel 死代码清理

**C1. `app/src/main/java/com/corgimemo/app/viewmodel/InspirationEditViewModel.kt`**

| 类别 | 行号 | 删除内容 |
|------|------|---------|
| import | L20 | `import com.corgimemo.app.domain.ReminderRecommender` |
| 实例 | L53 | `private val reminderRecommender = ReminderRecommender()` |
| StateFlow | L96-97 | `_repeatType` + `repeatType` |
| StateFlow | L130-131 | `_reminderTime` + `reminderTime` |
| StateFlow | L133-134 | `_recommendedReminderTime` + `recommendedReminderTime` |
| StateFlow | L136-137 | `_showReminderRecommendation` + `showReminderRecommendation` |
| section header | L129 | `// 提醒时间相关状态` |
| setCategoryId | L437 | `updateReminderRecommendation()` 调用 |
| setStartDate | L456 | `updateReminderRecommendation()` 调用 |
| setDueDate KDoc | L463 | "同时自动将提醒时间关联为截止时间..." 这句 |
| setDueDate 联动 | L469-472 | 整个 if 块（联动 reminderTime） |
| setEstimatedDurationMinutes | L482 | `updateReminderRecommendation()` 调用 |
| setRepeatType | L486-493 | 整个方法 |
| loadInspiration | L650 | `_repeatType.value = inspiration.repeatType` |
| loadInspiration | L658 | `_reminderTime.value = inspiration.reminderTime` |
| performSave (update) | L834-835 | `reminderTime = _reminderTime.value,` + `repeatType = _repeatType.value,` |
| performSave (insert) | L868-869 | 同上两行 |
| section header | L989 | `// ==================== 提醒时间推荐相关方法 ====================` |
| setReminderTime | L991-1000 | 整个方法 |
| acceptReminderRecommendation | L1002-1010 | 整个方法 |
| updateReminderRecommendation | L1012-1031 | 整个方法 |

**`setDueDate` 修改后**（仅删联动，保留 dueDate）：

```kotlin
fun setDueDate(dueDate: Long?) {
    _dueDate.value = dueDate
    _isDirty.value = true
}
```

**验证命令**：
```bash
rg "reminderTime|repeatType|ReminderRecommender|recommendedReminderTime|showReminderRecommendation|setReminderTime|setRepeatType|acceptReminderRecommendation|updateReminderRecommendation" app/src/main/java/com/corgimemo/app/viewmodel/InspirationEditViewModel.kt
# 预期：0 行匹配
```

---

### 阶段 D：UI 过期注释清理

**D1. `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt`**
- L412：`// 关闭长按面板，打开日期时间选择器（保留 longPressedInspiration 供 ReminderPickerBottomSheet 使用）`
  → `// 关闭长按面板，打开日期时间选择器`

**D2. `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationDateTimePickerDialog.kt`**
- L58：删除整行 `* 以及 ReminderPickerBottomSheet 的 TimeWheelView。`

---

### 阶段 E：询问用户编译验证 + 提交

按 `.trae/rules/编译验证.md`（最高优先级）**不擅自执行编译**，按 `.trae/rules/git提交.md` 询问 commit。

**预期编译结果**：
- 实体删除字段后，Room codegen 重新生成
- ViewModel 死代码删除后，无任何调用方报错
- Migration 注册正确，无 schema 不匹配

**单元测试检查**：
- `MigrationTest.kt`：只测 todo_items，不涉及 inspirations/deleted_inspirations，**不需修改**
- `InspirationDisplayItemsTest.kt` / `InspirationTextUtilsTest.kt` / `DailyWordCountComputeTest.kt`：构造的 `Inspiration(...)` 未传 reminderTime/repeatType，**不需修改**

---

## 关键文件清单

| 文件 | 改动摘要 | 行数变化 |
|------|---------|---------|
| [Inspiration.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/Inspiration.kt) | 删除 2 个字段 | 139 → 133 |
| [DeletedInspiration.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/DeletedInspiration.kt) | 删除 2 个字段 + 2 个映射方法中各 2 行 | 122 → 116 |
| [CorgiMemoDatabase.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt) | version 45→46 + MIGRATION_45_TO_46（约 135 行） + addMigrations 注册 | 1296 → ~1430 |
| [InspirationEditViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/InspirationEditViewModel.kt) | 1 import + 1 实例 + 4 StateFlow + 3 方法 + 联动逻辑 + 4 处 setter 推荐调用 | 2268 → ~2208 |
| [InspirationScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) | L412 注释精简 | -1 行 |
| [InspirationDateTimePickerDialog.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationDateTimePickerDialog.kt) | L58 删除整行 | -1 行 |

---

## 风险与回退

| 风险 | 等级 | 缓解 |
|------|------|------|
| 12-step 重建表耗时 | 低 | 灵感表通常 <1000 条，<100ms |
| Migration 事务失败 | 低 | try-finally 自动回滚 |
| 旧数据 reminderTime/repeatType 丢失 | 中 | 字段从来是默认值（无 UI 入口），丢的全是 null/0 |
| 用户从 v46 回退到 v45 | 极低 | 生产环境不会发生，git revert 可回退代码 |

**回退方案**：`git revert <commit>` 一键回退。

---

## 验证清单

### 编译验证（阶段 E 询问）
- `./gradlew assembleDebug` 期望 0 error 0 warning
- Room 运行时校验 identity hash 一致

### 数据迁移端到端
- 准备 v45 数据库（带 reminderTime/repeatType 旧值）→ 启动 App → 旧值被丢弃，无崩溃
- 查询 `sqlite_master` 验证 7 个索引已重建
- 行数对比 v45 vs v46 一致

### 功能验证
- 新建/编辑/删除/恢复灵感：流程不受影响
- 长按面板：仍是 4 个选项（置顶/标签/改日期/删除）
- 改截止日期：不再联动设置 reminderTime

---

## 一致性检查

| 规则 | 状态 |
|------|------|
| `entity与 migration同步检查.md` | ✅ 删字段后 Entity 与 SQL schema 一致 |
| `编译验证.md` | ✅ 不擅自编译，询问用户 |
| `调用AskUserQuestion 工具询问.md` | ✅ 阶段 E 询问编译 + commit |
| `import语句检查.md` | ✅ 删除 import 由编译器检查 |
| `文档命名语言要求.md` | ✅ Migration KDoc 用中文 |

---

## 后续可优化点

1. **InspirationEditViewModel 巨石组件拆分**（删除后仍有 2208 行，远超 800 行阈值）
2. **MIGRATION_45_TO_46 反向 Migration**（46→45）—— 生产环境不需要
3. **ReminderRecommender 重构**（下沉到 domain/reminder/ 作为通用服务）
4. **数据库 version 与 Entity 同步自动化**（Room schema export + CI 校验）
