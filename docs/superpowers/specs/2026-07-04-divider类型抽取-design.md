# divider 类型抽取设计

## 1. 背景

### 1.1 用户需求

> 抽取 divider 类型，当前 `isDivider: (T) -> Boolean` 无法区分 PinnedDivider/PendingDivider/CompletedDivider，未来若需更精细的区域判定可考虑泛型化。

### 1.2 当前实现的局限

[HomeScreen.kt:804-806](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L804-L806) 传入的是一个合并的三类型判断：

```kotlin
isDivider = { it is DisplayItem.PinnedDivider ||
              it is DisplayItem.PendingDivider ||
              it is DisplayItem.CompletedDivider }
```

**局限**：

1. **区域边界语义模糊**：`checkPinnedZoneCrossed` 算法只能知道"是不是 divider"，无法知道是哪种类型的 divider（PinnedDivider 后是置顶区结束，PendingDivider 后是非置顶区结束）
2. **未来扩展受限**：若需要"只在跨越 PendingDivider 时翻转 isPinned"或"区分置顶区/已完成区跨区"，当前签名无法支持
3. **测试需构造 mock divider**：测试中只能用 `isDivider=true` 的 TestItem，无法区分类型
4. **算法间接性**：通过"找邻居 + 比较 isPinned"间接判定跨区，而非"基于 divider 类型直接判定区域"

### 1.3 确认的范围

用户选择"重构 + 算法优化"：
- 抽取 `DividerKind` 枚举，将 `isDivider` 升级为 `dividerKind`
- 同时优化 `checkPinnedZoneCrossed` 算法为"基于 divider 位置直接判定区域"

## 2. 修复方案：引入 DividerKind 枚举 + 基于 divider 位置的区域判定（方案 A）

### 2.1 新增 DividerKind 枚举

在 `ReorderableLazyColumn.kt` 顶部新增：

```kotlin
/**
 * 分隔按钮类型枚举
 *
 * 用于区分 displayItems 中三种不同的区域分隔按钮，
 * 让算法能基于 divider 类型直接判定区域，而非间接"找邻居"。
 *
 * - [PINNED]: 置顶区与待完成区之间的分隔按钮（PinnedDivider）
 * - [PENDING]: 待完成区与已完成区之间的分隔按钮（PendingDivider）
 * - [COMPLETED]: 已完成区之后的分隔按钮（CompletedDivider）
 */
enum class DividerKind {
    PINNED,
    PENDING,
    COMPLETED
}
```

### 2.2 算法签名变更

[ReorderableLazyColumn.kt:121-128](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L121-L128)

#### 变更前
```kotlin
fun <T> checkPinnedZoneCrossed(
    displayItems: List<T>,
    isPinned: (T) -> Boolean,
    isDivider: (T) -> Boolean,
    draggedOriginalIsPinned: Boolean,
    draggedCurrentIndex: Int
): Boolean
```

#### 变更后
```kotlin
fun <T> checkPinnedZoneCrossed(
    displayItems: List<T>,
    isPinned: (T) -> Boolean,
    dividerKind: (T) -> DividerKind?,
    draggedOriginalIsPinned: Boolean,
    draggedCurrentIndex: Int
): Boolean
```

**变更点**：`isDivider: (T) -> Boolean` → `dividerKind: (T) -> DividerKind?`

- 返回 `null` = 非 divider（Todo 项）
- 返回 `DividerKind.PINNED` = PinnedDivider
- 返回 `DividerKind.PENDING` = PendingDivider
- 返回 `DividerKind.COMPLETED` = CompletedDivider

### 2.3 优化后的算法实现（混合算法）

#### 核心优化思路

**旧算法**：找前后邻居 → 比较邻居 isPinned → 判断跨区
**新算法（混合）**：
- 有 divider 时：扫描前面最近的 divider → 根据 divider 类型直接判定当前区域 → 与原始区域比较
- 无 divider 时（pinnedCount=0 的纯待办场景）：回退到"找邻居 + 比较 isPinned"，保持向后兼容

#### 混合算法实现（与 [ReorderableLazyColumn.kt:119-178](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L119-L178) 实际实现对齐）

```kotlin
fun <T> checkPinnedZoneCrossed(
    displayItems: List<T>,
    isPinned: (T) -> Boolean,
    dividerKind: (T) -> DividerKind?,
    draggedOriginalIsPinned: Boolean,
    draggedCurrentIndex: Int
): Boolean {
    if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false

    // 1. 扫描被拖项前面最近的 divider，确定其当前所在区域
    var currentZone: DividerKind? = null
    for (i in draggedCurrentIndex - 1 downTo 0) {
        val kind = dividerKind(displayItems[i])
        if (kind != null) {
            currentZone = kind
            break
        }
    }

    // 2. 若找到 divider，基于 divider 类型直接判定
    if (currentZone != null) {
        val originalZone = if (draggedOriginalIsPinned) DividerKind.PINNED else DividerKind.PENDING
        return currentZone != originalZone
    }

    // 3. 无 divider → 回退到"找邻居 + 比较 isPinned"（保持向后兼容）
    //    覆盖 pinnedCount=0 的纯待办场景（列表无 PinnedDivider）
    var neighborIdx = -1
    val prevIdx = draggedCurrentIndex - 1
    if (prevIdx >= 0 && dividerKind(displayItems[prevIdx]) == null) {
        neighborIdx = prevIdx
    }
    if (neighborIdx < 0) {
        val nextIdx = draggedCurrentIndex + 1
        if (nextIdx < displayItems.size && dividerKind(displayItems[nextIdx]) == null) {
            neighborIdx = nextIdx
        }
    }
    if (neighborIdx < 0) return false

    return draggedOriginalIsPinned != isPinned(displayItems[neighborIdx])
}
```

#### 为何采用混合算法而非纯 divider 算法

**纯 divider 算法的缺陷**：当 `pinnedCount=0` 时列表无 PinnedDivider，扫描前面找不到 divider，`effectiveZone = currentZone ?: DividerKind.PINNED` 会默认 PINNED，导致非置顶项被误判为跨区（`PENDING != PINNED → crossed=true`，错误翻转 isPinned）。

**混合算法的兜底**：无 divider 时回退到原始"找邻居"逻辑，与旧算法行为等价，保证向后兼容。

### 2.4 算法语义对比

| 维度 | 旧算法 | 新算法（混合） |
|---|---|---|
| 判定方式 | 找邻居 → 比较 isPinned | 有 divider：基于 divider 类型判定；无 divider：回退找邻居 |
| 边界处理 | 遇 divider 停止找邻居（间接） | 有 divider 直接根据类型判定；无 divider 找邻居 |
| 语义 | "邻居的 isPinned 与原始不同" | "当前所在区域与原始区域不同" |
| 复杂度 | O(n) 找邻居 | O(n)（两路径均为 O(n)） |
| 向后兼容 | ✅ | ✅（无 divider 路径与旧算法等价） |

### 2.5 关键场景验证

#### 有 divider 路径（pinnedCount >= 1）

| 场景 | 前面最近 divider | currentZone | originalZone | crossed | 正确？ |
|---|---|---|---|---|---|
| N6 拖到 PendingDivider 后 | PendingDivider | PENDING | PENDING | false | ✓ |
| P4 拖到 P1 上方 | PinnedDivider | PINNED | PINNED | false | ✓ |
| N5 拖到 P1/P2 之间 | PinnedDivider | PINNED | PENDING | true | ✓ |
| P4 拖到 N5/N6 之间 | PendingDivider | PENDING | PINNED | true | ✓ |

#### 无 divider 回退路径（pinnedCount = 0）

| 场景 | currentZone | 回退行为 | 邻居 isPinned | originalIsPinned | crossed | 正确？ |
|---|---|---|---|---|---|---|
| 纯待办列表内移动 | null | 找前/后邻居 | false | false | false | ✓ |
| 纯待办列表拖到顶部 | null | 向后找邻居 | false | false | false | ✓ |

**关键不变式**：pinnedCount=0 时所有项 isPinned=false，回退路径与旧算法等价，crossed 恒为 false。

### 2.6 排除方案

| 方案 | 否决理由 |
|---|---|
| 方案 B：保留 isDivider，新增 dividerType 函数 | 两个函数职责重叠，调用方需传两个函数 |
| 方案 C：完全重构为 zoneOf: (T) -> Zone | Todo 项也需返回 Zone，违反 SRP，改动过大 |

## 3. ReorderableLazyColumn 组件签名变更

### 3.1 变更前
[ReorderableLazyColumn.kt:199-210](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L199-L210)

```kotlin
@Composable
fun <T> ReorderableLazyColumn(
    ...
    isPinned: (T) -> Boolean,
    isDivider: (T) -> Boolean,
    ...
)
```

### 3.2 变更后

```kotlin
@Composable
fun <T> ReorderableLazyColumn(
    ...
    isPinned: (T) -> Boolean,
    dividerKind: (T) -> DividerKind?,
    ...
)
```

**说明**：组件内部所有使用 `isDivider` 的地方改为使用 `dividerKind`：
- `dividerKind(it) != null` 替代 `isDivider(it)`
- 传给 `checkPinnedZoneCrossed` 的参数名改为 `dividerKind`

### 3.3 组件内部使用点变更

| 位置 | 变更前 | 变更后 |
|---|---|---|
| 单拖 onDragStopped | `isDivider = isDivider` | `dividerKind = dividerKind` |
| 合并拖 onMergeReorder | `isDivider = isDivider` | `dividerKind = dividerKind` |
| 其他 isDivider(it) 调用 | `isDivider(it)` | `dividerKind(it) != null` |

## 4. HomeScreen 调用方变更

### 4.1 变更前
[HomeScreen.kt:804-806](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L804-L806)

```kotlin
isDivider = { it is DisplayItem.PinnedDivider ||
              it is DisplayItem.PendingDivider ||
              it is DisplayItem.CompletedDivider },
```

### 4.2 变更后

```kotlin
dividerKind = { when (it) {
    is DisplayItem.PinnedDivider -> DividerKind.PINNED
    is DisplayItem.PendingDivider -> DividerKind.PENDING
    is DisplayItem.CompletedDivider -> DividerKind.COMPLETED
    else -> null
}},
```

**变更点**：从返回 `Boolean` 改为返回 `DividerKind?`，明确每种 divider 的类型。

## 5. 测试策略

### 5.1 算法层测试调整（ReorderAlgorithmsTest.kt）

#### 现有 15 个测试适配新签名

| 现有测试 | 修改内容 |
|---|---|
| `TestItem(isDivider: Boolean)` 字段 | 改为 `dividerKind: DividerKind?` 字段（null = 非 divider） |
| `checkCrossed(...)` 辅助方法 | `isDivider` 参数改为 `dividerKind` |
| 15 个测试的 `TestItem` 构造 | `isDivider = true` → `dividerKind = DividerKind.PENDING`（或其他，根据场景） |

#### 新增 2 个基于 divider 类型的精细化测试

| 测试名 | 场景 | 验证点 |
|---|---|---|
| `PinnedDivider 后的项判定为 PINNED 区` | 被拖项前面最近是 PinnedDivider | currentZone=PINNED |
| `PendingDivider 后的项判定为 PENDING 区` | 被拖项前面最近是 PendingDivider | currentZone=PENDING |

### 5.2 ViewModel 层测试

| 现有测试 | 修改内容 |
|---|---|
| HomeViewModelReorderTest 全部 13 个 | 无需修改（通过 `onReorder` 间接调用算法，不直接传 dividerKind） |
| HomeViewModelPinnedButtonTest 3 个 | 无需修改 |

### 5.3 真机验证场景

| 场景 | 操作 | 期望 |
|---|---|---|
| N6 拖到 PendingDivider 后 | 长按 N6，拖到 PendingDivider 和 N5 之间 | N6 落在 N5 前，仍为非置顶 |
| P4 拖到 P1 上方 | 长按 P4，拖到 P1 上方 | P4 落在 P1 前，仍为置顶 |
| N5 拖到 P1/P2 之间 | 长按 N5，拖到 P1/P2 之间 | N5 变为置顶，落入置顶区 |
| P4 拖到 N5/N6 之间 | 长按 P4，拖到 N5/N6 之间 | P4 变为非置顶，落入待完成区 |

## 6. 实施约束

1. **不动 ViewModel 的 `reorderOnDisplayList`**：算法变更对 ViewModel 透明，`crossedPinnedZone` 仍为 Boolean
2. **不动 DisplayItem sealed interface**：`PinnedDivider`/`PendingDivider`/`CompletedDivider` 数据类保持不变
3. **保持向后兼容**：所有现有测试场景的 `crossedPinnedZone` 期望值不变
4. **遵循 workspace rule**：编辑后检查 import 语句完整性
5. **真机验证**：覆盖 N6/P4 跨区拖拽场景

## 7. 验收标准

- [x] 新增 `DividerKind` 枚举（PINNED, PENDING, COMPLETED）  ← 已完成
- [x] `checkPinnedZoneCrossed` 算法签名从 `isDivider` 改为 `dividerKind: (T) -> DividerKind?`  ← 已完成
- [x] 算法实现改为"混合算法"（有 divider 直接判定 / 无 divider 回退找邻居）  ← 已完成
- [x] `ReorderableLazyColumn` 组件签名同步变更  ← 已完成
- [x] HomeScreen 调用方传入 `dividerKind` 返回具体类型  ← 已完成
- [ ] 算法层 15 个测试适配新签名  ← Task 6 待执行
- [ ] 新增 2 个基于 divider 类型的精细化测试  ← Task 6 待执行
- [ ] `./gradlew assembleDebug` 编译通过  ← Task 5 待执行
- [ ] `./gradlew test` 全部测试通过  ← Task 7 待执行
- [ ] 真机验证 4 个跨区拖拽场景  ← Task 8 待执行

## 8. 后续可优化方向

### 8.1 关于 DividerKind 泛型化的评估结论（2026-07-05 评估）

**结论：当前不推荐泛型化，保持 `enum class` 现状。**

**评估理由**：

1. **YAGNI 原则**：当前无具体扩展场景（如归档区/标签 divider 等明确需求），属于预防性扩展
2. **算法内部硬编码**：
   ```kotlin
   val originalZone = if (draggedOriginalIsPinned) DividerKind.PINNED else DividerKind.PENDING
   ```
   这行代码硬编码了"原始区域只能是 PINNED 或 PENDING"，与"CompletedDivider 后的区域"无关
3. **"divider 类型泛型化"≠"算法泛型化"**：
   - 即使将 `DividerKind` 改为 `sealed interface`，算法仍针对"置顶区跨越"这一具体语义设计
   - 新增 divider 类型后算法不会自动支持，仍需改算法内部逻辑
4. **Kotlin 技术约束**：`enum class` 不可继承，所谓"T : DividerKind"必须先改为 `sealed interface`/`sealed class`，丢失 `values()`/`valueOf()` 便捷性

**真泛型化需要的架构级重构**（仅作记录，不在本次范围）：

- 引入 `originalZoneOf: (T) -> D` 函数，让调用方自定义原始区域推断
- 重新定义"跨区"语义（跨任意两区域 vs 仅跨置顶区）
- 改变 `crossedPinnedZone: Boolean` 返回值为 `ZoneTransition?` 或类似结构

**何时重新评估**：

- 出现第 4 种 divider 类型的明确需求（如归档区 Divider）
- 需要检测 PINNED↔COMPLETED、PENDING↔COMPLETED 等非置顶区跨越
- 算法需要被多个不同区域语义的场景复用

### 8.2 其他优化方向（保留）

1. **算法性能优化**：当前扫描前面 divider 为 O(n)，若列表很长可考虑缓存 divider 位置索引
2. **区域判定 API**：抽取 `determineZone(displayItems, index, dividerKind)` 为独立公开函数，供其他算法复用（不引入泛型复杂度）
3. **Divider 类型与 Zone 类型统一**：当前 `DividerKind` 同时表示"divider 类型"和"divider 之后的区域"，未来可拆分为 `DividerType` 和 `Zone` 两个枚举
