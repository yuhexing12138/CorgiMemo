# divider 边界 stale 修复设计

## 1. 背景

### 1.1 用户报告的场景

> 已知有十条待办，名称为 1-10，从上到下按序排列。1，2，3，4 是置顶状态，其他都是待完成状态。当用户将 6 拖拽到 "待完成" 按钮和 5 之间释放时，6 发生了位置跳跃，到了 4 和 "待完成" 按钮之间，且显示置顶。正确的应该是在待完成区域的 "待完成" 按钮和 5 之间。

### 1.2 复现条件

- 初始 `pinnedCount = 4`（Case A：displayItems 含 PinnedDivider + PendingDivider）
- displayItems 结构：
  ```
  [PinnedDivider, P1, P2, P3, P4, PendingDivider, N5, N6, N7, N8, N9, N10]
   0             1   2   3   4   5                6   7   8   9   10  11
  ```
- 用户拖 N6（索引 7）到 PendingDivider（索引 5）和 N5（索引 6）之间，即 toIndex=6
- 期望：N6 落在 PendingDivider 后、N5 前，仍为非置顶
- 实际：N6 跳到 P4 和 PendingDivider 之间，且变为置顶

## 2. 根因分析

### 2.1 算法当前实现

[ReorderableLazyColumn.kt:128-145](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L128-L145)：

```kotlin
// 优先向前找第一个非 divider 邻居
var neighborIdx = -1
for (i in draggedCurrentIndex - 1 downTo 0) {
    if (!isDivider(displayItems[i])) { neighborIdx = i; break }
}
// 前面没有则向后找
if (neighborIdx < 0) {
    for (i in draggedCurrentIndex + 1 until displayItems.size) {
        if (!isDivider(displayItems[i])) { neighborIdx = i; break }
    }
}
```

**算法把 divider 当作"可跳过的项"而非"区域边界"**。当 N6 紧贴 PendingDivider 之后放置时，算法跳过 PendingDivider 找到 P4（不同区域的项），误判为跨区。

### 2.2 Bug 触发链

| 步骤 | 算法行为 | 结果 |
|---|---|---|
| 1 | 向前找邻居：`displayItems[5]` = PendingDivider，`isDivider=true` | **跳过**（当前实现：skip divider） |
| 2 | 继续向前：`displayItems[4]` = P4，`isDivider=false` | neighborIdx = 4 ❌ |
| 3 | `isPinned(P4)` = `true` | - |
| 4 | `draggedOriginalIsPinned` = `false`（N6 非置顶） | - |
| 5 | `crossedPinnedZone = (false != true)` | **= true** ❌ |
| 6 | ViewModel 收到 `crossedPinnedZone=true`，else 分支翻转 N6.isPinned=true | N6 变置顶 |
| 7 | 插入逻辑：N6 现在 isPinned=true，放入 pendingList 的 pinned 子区 | N6 落在 P4 和 PendingDivider 之间 ✗ |

### 2.3 历史背景：上一次修复的回归

上一次 bug（"4 拖到 1 上方后变非置顶"）的根因是"算法把 divider 当作邻居"（divider 的 isPinned=false 被误用），修复方式是"跳过 divider"。

但"跳过"语义错误——divider 是区域边界，不应被跨越。这引入了本次 bug：当被拖项紧贴 divider 之后/之前放置时，算法跳过 divider 找到对岸的项，误判跨区。

## 3. 修复方案

### 3.1 选定方案：将 divider 视为区域边界，遇到即停止（方案 A）

#### 修改位置
[ReorderableLazyColumn.kt:128-145](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L128-L145) 的 `checkPinnedZoneCrossed`

#### 变更前
```kotlin
if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false

// 优先向前找第一个非 divider 邻居
var neighborIdx = -1
for (i in draggedCurrentIndex - 1 downTo 0) {
    if (!isDivider(displayItems[i])) { neighborIdx = i; break }
}
// 前面没有则向后找
if (neighborIdx < 0) {
    for (i in draggedCurrentIndex + 1 until displayItems.size) {
        if (!isDivider(displayItems[i])) { neighborIdx = i; break }
    }
}
// 整个列表都是 divider（理论上不可能）→ 不跨区
if (neighborIdx < 0) return false

return draggedOriginalIsPinned != isPinned(displayItems[neighborIdx])
```

#### 变更后
```kotlin
if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false

// 向前找邻居：divider 是区域边界，遇到立即停止（不能跨越边界找邻居）
var neighborIdx = -1
val prevIdx = draggedCurrentIndex - 1
if (prevIdx >= 0 && !isDivider(displayItems[prevIdx])) {
    neighborIdx = prevIdx
}
// 前面被 divider 阻断或到列表头 → 向后找
if (neighborIdx < 0) {
    val nextIdx = draggedCurrentIndex + 1
    if (nextIdx < displayItems.size && !isDivider(displayItems[nextIdx])) {
        neighborIdx = nextIdx
    }
}
// 两边都被 divider 阻断或到列表边界 → 无法判定，不跨区
if (neighborIdx < 0) return false

return draggedOriginalIsPinned != isPinned(displayItems[neighborIdx])
```

### 3.2 核心语义变化

| 修改前 | 修改后 |
|---|---|
| `if (!isDivider) { 用它 }` 跳过 divider 继续找 | `if (isDivider) break; else 用它` 遇 divider 即停 |
| divider 是"可跳过的项" | divider 是"区域边界" |
| 会跨越边界找邻居 | 不会跨越边界 |

### 3.3 简化说明

原代码用 for 循环是为"跳过多个 divider"设计，但实际只需检查**紧邻的一项**（前或后）。若紧邻是 divider，说明被边界阻断，应停止该方向搜索。简化为单次 if 判断更清晰。

### 3.4 关键场景验证

| 场景 | draggedCurrentIndex | 前邻居 | 后邻居 | 选中 | crossed | 正确？ |
|---|---|---|---|---|---|---|
| **本次 bug**：N6 拖到 PendingDivider 后（idx=6） | 6 | PendingDivider（边界）→ 停 | N5（isPinned=false） | N5 | (false!=false)=false | ✓ |
| **上次 bug**：P4 拖到 P1 上方（idx=1） | 1 | PinnedDivider（边界）→ 停 | P1（isPinned=true） | P1 | (true!=true)=false | ✓ |
| **正常跨区**：N5 拖到 P1/P2 之间（idx=2） | 2 | P1（isPinned=true） | P2（isPinned=true） | P1 | (false!=true)=true | ✓ |
| **反向跨区**：P4 拖到 N5/N6 之间 | 6 | N5（isPinned=false） | N6（isPinned=false） | N5 | (true!=false)=true | ✓ |

### 3.5 排除方案

| 方案 | 否决理由 |
|---|---|
| 方案 B：基于 divider 位置直接判定当前区域 | 需区分 PinnedDivider/PendingDivider/CompletedDivider 三种 divider，API 变更影响大 |
| 方案 C：在调用方限制 toIndex 不能跨 divider | 治标不治本，限制用户拖拽自由度，破坏 UX |

## 4. 测试策略

### 4.1 新增算法层测试（追加到 ReorderAlgorithmsTest.kt）

| 测试名 | 场景 | 输入 | 期望 |
|---|---|---|---|
| `divider 后第一项不应跨区` | N6 紧贴 PendingDivider 之后 | displayItems=[Divider, P4, Divider, N6, N5], draggedOriginalIsPinned=false, draggedCurrentIndex=3 | crossed=false |
| `divider 前第一项不应跨区` | P4 紧贴 PendingDivider 之前（pinned 区末尾） | displayItems=[Divider, P4, Divider, N5, N6], draggedOriginalIsPinned=true, draggedCurrentIndex=1 | crossed=false |

### 4.2 回归保护：现有测试不变

| 现有测试 | 修改后期望 |
|---|---|
| `draggedOriginalIsPinned 与邻居同为 pinned 不应跨区` | 仍 PASSED ✓ |
| `已置顶项与置顶区邻居同区不应跨区` | 仍 PASSED ✓ |
| 其他 11 个算法测试 | 仍 PASSED ✓ |

## 5. 实施约束

1. **不动 ViewModel**：`reorderOnDisplayList` 逻辑保留（`crossedPinnedZone` 正确时不会误翻转）
2. **不动调用方**：`onDragStopped` 中调用 `checkPinnedZoneCrossed` 的方式不变
3. **不动 merge drag 路径**：合并拖拽使用同一算法，自动受益
4. **遵循 workspace rule**：编辑后检查 import 语句完整性

## 6. 验收标准

- [ ] `checkPinnedZoneCrossed` 将 divider 视为边界，遇 divider 即停止搜索
- [ ] 新增 2 个算法层测试覆盖 divider 边界场景
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] `./gradlew test` 全部测试通过（含原有 13 个算法测试 + 新增 2 个）
- [ ] 真机验证：N6 拖到 PendingDivider 和 N5 之间释放，N6 落在 N5 前、PendingDivider 后，仍为非置顶

## 7. 后续可优化方向

1. **算法语义文档化**：在 `checkPinnedZoneCrossed` 顶部补充 KDoc，明确"divider 是区域边界，遇之即停"的契约
2. **属性测试**：考虑引入 Kotlin Property Testing 框架，自动生成边界场景，覆盖更多 divider 位置组合
3. **抽取 divider 类型**：当前 `isDivider: (T) -> Boolean` 无法区分 PinnedDivider/PendingDivider/CompletedDivider，未来若需更精细的区域判定，可考虑泛型化 divider 类型
