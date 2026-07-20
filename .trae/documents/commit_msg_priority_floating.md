feat(ui): 待办卡片悬浮效果(默认 4dp / 长按 8dp) + 编辑页优先级弹窗圆点浅绿统一

为待办卡片引入"悬浮效果"——默认静态 4dp 阴影(v2026-07-20 从 2dp 提升),长按(>= 500ms)抬升至 8dp 阴影 + 颜色 alpha 0.3→0.5,营造"卡片被长按抬升"的物理感。同时修正编辑页优先级选择弹窗无优先级圆点颜色。

主要改动:

1. PressFeedback.kt(扩展 API)
   - 新增 isLongPressed: MutableState<Boolean> 参数(默认 remember 一个)
   - 内部在 >= 500ms 时 set true,抬起/移动/cancel/拖拽让位/异常退出时 set false
   - 加入 pointerInput 的 key 列表,确保状态订阅正确
   - 禁用态时也 set false 防止阴影卡在长按态

2. TodoListItem.kt(首页卡片悬浮效果)
   - 新增 isLongPressed 状态与 shadowElevation(animateDpAsState)动画
   - 默认 4dp / 长按 8dp,duration 200ms 与 pressFeedback 回弹同步
   - 阴影 alpha 默认 0.3f / 长按 0.5f,加深视觉对比
   - **关键修正**:Modifier 顺序调整为 .pressFeedback() → .border() → .shadow()
     原顺序 .border() → .shadow() → .pressFeedback() 会导致"内容缩小但边框/阴影不缩"
     修正后边框/阴影都在 graphicsLayer 内部,跟着 scale 一起缩放
   - 新增 import: FastOutSlowInEasing, animateDpAsState, mutableStateOf

3. TodoEditScreen.kt(编辑页优先级弹窗圆点修正)
   - 优先级选择弹窗(Triple options)中"无优先级"由 Color.Gray 透明圆环
     改为 PriorityColors.None 浅绿实心圆点
   - 去掉 value==0 的"透明 + 灰边"特殊分支,统一用 [color] 填充
   - 与首页/回收站/编辑页边框"无优先级浅绿"视觉统一

4. UI设计规范.md §12.1.10
   - 视觉三联表更新:阴影改为 4dp(默认)/ 8dp(长按) + alpha 0.3f/0.5f
   - 新增 §12.1.10.4.1 首页 Card 悬浮效果子章节(状态机/Modifier 顺序/PressFeedback 状态参数)
   - 新增 §12.1.10.4.2 编辑页优先级选择弹窗圆点修正子章节
   - §12.1.10.5 关键技术决策增加 4 行:首页阴影动态化/Modifier 顺序/PressFeedback isLongPressed/优先级选择弹窗圆点统一
   - §12.1.10.6 排版变更记录新增 v1.1 行

附带修复(与本任务无关,作为额外 commit 内容):

5. MainScreen.kt(sidebar 宽度计算 bug 修复)
   - 原代码: Modifier.width(screenWidth * 0.8f).coerceIn(280.dp, 360.dp)
     ❌ 错诶:.coerceIn() 应在 Dp 上调用,不是 Modifier 上
   - 修正为: 先计算 val drawerWidth = (screenWidth * 0.8f).coerceIn(280.dp, 360.dp),
     再用 Modifier.width(drawerWidth)
   - 加注释说明 "必须放在 Modifier.width 之前对 Dp 调用,否则会被解析成 Modifier 的方法,不存在"

设计依据:
- 默认 4dp + 长按 8dp 阴影形成"静态浮起 + 动态抬升"两层视觉,既保持卡片精致感又给长按拖拽提供视觉预告
- alpha 0.3→0.5 加深,与 elevation 抬升同步,强化"被选中/被长按"的感觉
- Modifier 顺序调整是 bug 修复,影响所有首页卡片的按压视觉一致性
- 优先级选择弹窗圆点统一避免"无优先级=空=不重要"的误读,与其他优先级视觉对齐

测试:
- 编译验证由用户执行(按项目规则不擅自编译)
- UI 验收: 首页短按/长按看阴影抬升 + 边框随缩放, 优先级选择弹窗看 4 个圆点统一为实心
- 触觉验证: 长按待办卡片 ≥ 500ms 应触发震动 + 阴影抬升至 8dp
