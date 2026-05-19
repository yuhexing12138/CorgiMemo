# 任务完成动画差异化实现计划

## 一、代码库现状分析

通过代码审查，发现以下组件**已存在**：

| 组件 | 文件路径 | 状态 |
|------|----------|------|
| CelebrationLevel 枚举 | [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L47-L61) | ✓ LOW(0), MEDIUM(1), HIGH(2) |
| CelebrationState 数据类 | [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L70-L74) | ✓ isShowing, level, message |
| handleTaskCompleted 方法 | [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L669-L711) | ✓ 基于 priority 计算 level |
| getEncouragementMessage | [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L649-L655) | ✓ 三个级别的鼓励语 |
| CelebrationOverlay UI | [HomeScreen.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L1042-L1086) | ✓ 不同级别显示不同样式 |
| GlowOverlay UI | [HomeScreen.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L1124-L1143) | ✓ 中/高级别光晕效果 |
| TodoItem.dueDate 字段 | [TodoItem.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/TodoItem.kt#L23) | ✓ Long? 类型，表示截止日期时间戳 |

## 二、需求分析与设计

### 2.1 用户需求与现有实现对比

| 场景 | 用户需求 | 当前实现 | 需要调整 |
|------|----------|----------|----------|
| 低优先级/普通任务 | 微笑 + 摇尾巴 + 少量星星 | LOW: 😊 + 透明度 0.25 | ✓ 基本一致 |
| 高优先级任务 | 跳跃 + 撒花 + 彩虹特效 | HIGH: 🎉 + 彩虹光晕 | ✓ 基本一致 |
| 截止日期当天任务 | 超级庆祝 + 弹窗鼓励语 | ❌ 未实现 | ✗ 需新增 |

### 2.2 鼓励语对比

| 场景 | 用户需求 | 当前实现 | 调整建议 |
|------|----------|----------|----------|
| 普通任务 | "太棒了！又完成一个！" | "太棒了！" | 需要修改 |
| 高优先级任务 | "这么重要的任务都完成了！太厉害了！" | "这么重要的任务都完成了！柯基为你骄傲！" | 需要修改 |
| 当日截止任务 | "抢在截止前完成了！柯基为你骄傲！" | ❌ 未实现 | 需新增 |

### 2.3 新增级别的设计

需要新增一个 **SUPER** 级别用于截止日期当天完成的任务：

- 新增 `CelebrationLevel.SUPER(3)` 枚举值
- 新增对应的鼓励语和 UI 特效
- 超级庆祝可以包含弹窗（Dialog）形式的额外鼓励

## 三、实施步骤

### 步骤 1：修改 CelebrationLevel 枚举

在 [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) 中：
- 添加 `SUPER(3)` 到 `CelebrationLevel` 枚举
- 修改 `fromPriority()` 方法支持新值

### 步骤 2：修改任务完成判断逻辑

修改 `toggleTodoStatus()` 和 `handleTaskCompleted()` 方法：
- `toggleTodoStatus()` 需要传递整个 `TodoItem` 对象（目前只传了 `priority`）
- 新增方法 `calculateCelebrationLevel(todo: TodoItem)` 综合判断：
  - **优先级**（priority）
  - **是否截止日期当天完成**（dueDate 与当前日期比较）
  - **级别优先级规则**：截止日期当天 > 高优先级 > 中优先级 > 低优先级

### 步骤 3：修改鼓励语库

更新 `getEncouragementMessage()` 方法：
- LOW: "太棒了！又完成一个！"
- MEDIUM: "完成得不错哦！"（保持不变或微调）
- HIGH: "这么重要的任务都完成了！太厉害了！"
- SUPER: "抢在截止前完成了！柯基为你骄傲！"

### 步骤 4：修改 CelebrationOverlay UI

在 [HomeScreen.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) 中：
- 为 SUPER 级别设计更炫酷的 UI
- 可以考虑：更大的字体、更亮的背景、更多的装饰元素

### 步骤 5：新增超级庆祝弹窗

为 SUPER 级别添加额外的弹窗鼓励：
- 新增一个 Dialog 组件显示额外的鼓励信息
- 可以添加柯基表情和更多装饰

### 步骤 6：代码审查和优化

- 添加必要的中文注释
- 确保函数级注释完整
- 验证边界条件（dueDate 为 null 的情况）

## 四、涉及文件清单

| 文件名 | 操作类型 | 说明 |
|--------|----------|------|
| [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) | 修改 | 新增 SUPER 级别、修改判断逻辑和鼓励语 |
| [HomeScreen.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) | 修改 | 新增 SUPER 级别 UI 和弹窗 |

## 五、详细技术实现方案

### 5.1 CelebrationLevel 扩展

```kotlin
enum class CelebrationLevel(val priority: Int) {
    LOW(0),      // 低优先级/普通任务
    MEDIUM(1),   // 中优先级任务
    HIGH(2),     // 高优先级任务
    SUPER(3);    // 截止日期当天完成（最高级）

    companion object {
        fun fromPriority(priority: Int): CelebrationLevel {
            return when (priority) {
                3 -> SUPER
                2 -> HIGH
                1 -> MEDIUM
                else -> LOW
            }
        }
    }
}
```

### 5.2 判断逻辑设计

```kotlin
/**
 * 综合判断庆祝级别
 * 截止日期当天完成的任务优先级最高
 */
private fun calculateCelebrationLevel(todo: TodoItem): CelebrationLevel {
    val currentTime = System.currentTimeMillis()
    
    // 检查是否截止日期当天完成
    if (todo.dueDate != null && isSameDay(todo.dueDate, currentTime)) {
        return CelebrationLevel.SUPER
    }
    
    // 根据优先级返回
    return CelebrationLevel.fromPriority(todo.priority)
}

/**
 * 判断两个时间戳是否同一天
 */
private fun isSameDay(time1: Long, time2: Long): Boolean {
    // 使用 Calendar 或 LocalDate 比较
    // 注意时区处理
}
```

### 5.3 级别优先级规则

| 判断条件 | 结果级别 |
|----------|----------|
| 截止日期当天完成 | SUPER（最高） |
| 优先级 = 2（高）且非截止日期当天 | HIGH |
| 优先级 = 1（中）且非截止日期当天 | MEDIUM |
| 优先级 = 0（低）且非截止日期当天 | LOW（最低） |

## 六、风险和注意事项

1. **时间戳比较**：需要正确处理日期比较，考虑时区问题
2. **dueDate 为 null**：没有截止日期的任务不能升级为 SUPER 级别
3. **兼容性**：新增枚举值需要确保所有使用处都能处理
4. **UI 性能**：动画效果不要过于复杂，避免影响性能

## 七、验证方法

1. 创建一个低优先级任务，完成后验证 LOW 级别动画
2. 创建一个高优先级任务，完成后验证 HIGH 级别动画
3. 创建一个截止日期为当天的任务，完成后验证 SUPER 级别动画和弹窗
4. 创建一个没有截止日期的任务，完成后验证不会触发 SUPER 级别
5. 修改设置关闭触觉反馈，验证动画正常但无震动
