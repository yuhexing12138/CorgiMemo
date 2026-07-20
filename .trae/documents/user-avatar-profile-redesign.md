# CorgiMemo 头像一致性 + Profile 页用户化改造

## Context（背景）

当前 AppDrawer 顶部用 `FrameAnimation(LIE)` 柯基趴卧动画 + 柯基名字表示"用户头"，Profile 头卡又用 🐕 emoji + 柯基名字/等级/经验条渲染另一个"柯基头"。两处视觉上都是柯基形象，没有"用户"概念，drawer 头部也无法点击跳到"我的"页。本次改造目标：

1. **抽离"用户"概念** — 引入统一 `UserAvatar` 组件，用"首字母 + 主色背景"占位（明显区别于柯基）
2. **drawer 顶部可点击跳 Profile** — 头像点击触发 tab 切换 + 关 drawer
3. **Profile 页改造** — 头卡从"柯基展示头卡"改为"用户信息头卡"，柯基互动功能交给已存在的 `Screen.CorgiDetail`
4. **接通柯基互动入口** — 顶部柯基爪子图标 + 柯基悬浮球原本弹"开发中" snackbar，本期直接跳 `Screen.CorgiDetail`
5. **数据层预留** — `CorgiData.avatarPath` 本期就加字段（Room 升级 v39→v40），上传功能后续实现

依据 `.trae/rules/编译验证.md`，**本任务不在工作流中自动执行 `./gradlew`，所有阶段由用户手动编译验证**。

---

## 关键文件（按修改顺序）

| # | 路径 | 性质 |
|---|---|---|
| 1 | `app/src/main/java/com/corgimemo/app/data/model/CorgiData.kt` | 改 |
| 2 | `app/src/main/java/com/corgimemo/app/data/local/db/CorgiDao.kt` | 改 |
| 3 | `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt` | 改 |
| 4 | `app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt` | 改 |
| 5 | `app/src/main/java/com/corgimemo/app/ui/components/UserAvatar.kt` | **新建** |
| 6 | `app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt` | 改 |
| 7 | `app/src/main/java/com/corgimemo/app/ui/screens/profile/components/ProfileHeroCard.kt` | 改（重写） |
| 8 | `app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt` | 改 |
| 9 | `app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt` | 改 |

不修改但需确认存在的：
- `ui/screens/corgi/CorgiDetailScreen.kt`（柯基详情页已实现）
- `ui/navigation/Screen.kt`（`Screen.CorgiDetail = "corgi_detail"` 已存在）
- `ui/navigation/AppNavHost.kt:107-109`（CorgiDetail 路由已注册）

---

## 实施步骤

### Step 1 — 数据层（4 文件改动 + 1 Room Migration）

**CorgiData.kt:40-60** 末尾追加字段：
```kotlin
/** 用户头像文件路径（null = 使用首字母占位） */
@ColumnInfo(defaultValue = "NULL")
val avatarPath: String? = null
```

**CorgiDao.kt** 末尾追加：
```kotlin
@Query("UPDATE corgi_data SET avatarPath = :path WHERE id = (SELECT id FROM corgi_data LIMIT 1)")
suspend fun updateAvatarPath(path: String?)
```

**CorgiMemoDatabase.kt**：
- `version = 40`
- `addMigrations(...)` 末尾追加 `MIGRATION_39_40`
- 文件底部追加：
```kotlin
internal val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE corgi_data ADD COLUMN avatarPath TEXT DEFAULT NULL")
    }
}
```

**CorgiRepository.kt** 末尾追加：
```kotlin
suspend fun updateAvatarPath(path: String?) = withContext(ioDispatcher) {
    corgiDao.updateAvatarPath(path)
}
```

> 字段 `String?` + SQL `DEFAULT NULL` + `@ColumnInfo(defaultValue = "NULL")` 三者严格一致，符合 `entity与 migration同步检查.md` 规则。
> 不使用 `fallbackToDestructiveMigration` — 柯基数据需保留。

### Step 2 — UserAvatar 通用组件（1 新建）

**`ui/components/UserAvatar.kt`**（新建，约 80 行）：

```kotlin
@Composable
fun UserAvatar(
    nickname: String,
    avatarPath: String?,
    size: Dp,
    onClick: (() -> Unit)? = null,
    onAvatarLongClick: (() -> Unit)? = null
)
```

实现要点：
- 圆形 + `MaterialTheme.colorScheme.primary` 主色背景
- 2dp `surface` 色描边
- 优先用 `avatarPath` 渲染 `coil3.compose.AsyncImage`（本期永远为 null，分支代码先写好）
- 兜底渲染首字母 Text（`onPrimary` 色，字号 `size * 0.42f` sp，FontWeight.Bold）
- `Modifier.clickable(enabled = onClick != null)`
- 私有 `pickInitial(nickname: String): String`：
  - 空串/纯空白 → `"?"`
  - 第一个 Unicode 码点
  - emoji 段（0x1F000-0x1FFFF 或 0x2600-0x27BF）→ `"🎉"`
  - 汉字（0x4E00-0x9FFF）→ 原字
  - 其它 → `uppercase()`

### Step 3 — AppDrawer 顶部改造

**AppDrawer.kt**：
- `AppDrawerContent` 新增参数 `onProfileClick: () -> Unit = {}`
- `UserProfileSection` 改名为 `DrawerUserHeader`，接收 `onClick: () -> Unit`
- 把 `FrameAnimation(LIE)` 替换为 `UserAvatar(corgiData.name, corgiData?.avatarPath, size = 48.dp, onClick = onClick)`
- 副标题保留 `"Lv.X 柯基少年"`（drawer 信息密度要求）
- Row 外层加 `Modifier.clickable(onClick = onClick)`，整行可点

### Step 4 — ProfileHeroCard 重构

**`ui/screens/profile/components/ProfileHeroCard.kt`**（重写）：
- 保留 Card 外壳 + 渐变背景（视觉锚点）
- 删除：🐕 emoji Box、等级徽章、经验条、`HeroStat` 三栏
- 删除参数：`hapticEnabled`、`soundEnabled`、`levelStage`、`levelProgress`、`progressText`（这些原签名已 `@Suppress("UNUSED_PARAMETER")`，可安全删除）
- 新签名：
  ```kotlin
  @Composable
  fun ProfileHeroCard(
      corgiData: CorgiData?,
      consecutiveDays: Int,
      onNameClick: () -> Unit
  )
  ```
- 新内容（Row）：
  - 左侧：72dp `UserAvatar(corgiData.name, corgiData?.avatarPath, size = 72.dp, onClick = onNameClick)`
  - 右侧 Column：用户昵称（20sp Bold，`corgiData?.name ?: "小柯基"`）+ 副标题 `"Lv.X · 柯基陪伴 N 天"`（11sp Medium，`MaterialTheme.colorScheme.primary`）

### Step 5 — ProfileScreen 调整

**`ProfileScreen.kt`**：
- 删除 ProfileHeroCard 调用中的 `levelStage` / `levelProgress` / `progressText` / `hapticEnabled` / `soundEnabled`，改为 `consecutiveDays = corgiData?.consecutiveDays ?: 0`
- 改名弹窗文案调整：
  - `CorgiRenameDialog` 标题 `"修改柯基名字"` → `"修改昵称"`
  - 确认弹窗 `"确定要将柯基的名字改为「$pendingNewName」吗？"` → `"确定要将昵称改为「$pendingNewName」吗？"`
  - 弹窗内的 🐕 emoji 保留（暗示"你的柯基 = 你的昵称"）
- 其余 5 模块（用户信息头卡 / 主题配色 / 装扮入口 / 成就统计 / 7 天情绪图表）顺序与内容不变

### Step 6 — MainScreen 集成

**`ui/screens/main/MainScreen.kt`**：

**(a) 抽屉响应式宽度**（:465）：
```kotlin
val screenWidth = LocalConfiguration.current.screenWidthDp.dp
ModalDrawerSheet(
    modifier = Modifier.width(screenWidth * 0.8f).coerceIn(280.dp, 360.dp)
) { ... }
```

**(b) 抽屉头像点击回调**（:466）：
- AppDrawerContent 调 `onProfileClick = { selectedTab = TabItem.PROFILE; coroutineScope.launch { drawerState.close() } }`
- 先改 selectedTab 再关 drawer（与现状一致）

**(c) Tab 切换淡入淡出**（:855-881）：
- 整段 `when (selectedTab)` 外面包 `AnimatedContent(targetState = selectedTab, keepCurrentState = true, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) }, label = "tab-switch") { ... }`
- `keepCurrentState = true` 保护 HomeScreen 滚动位置

**(d) 顶部柯基图标接通**（:601-604）：
```kotlin
onCorgiClick = { navController.navigate(Screen.CorgiDetail.route) }
```
移除 "柯基互动页开发中" snackbar。

**(e) 悬浮球接通**（:886-888）：
```kotlin
onClick = { navController.navigate(Screen.CorgiDetail.route) }
```
移除 "柯基互动页开发中" snackbar。

---

## 复用现有代码

- `coil3.compose.AsyncImage` + `coil3.request.crossfade` — 已在 `ImagePreviewScreen.kt:48-50` 使用
- `MaterialTheme.colorScheme.primary/onPrimary/surface` — 全项目统一
- `AppSnackbarHost` — 改名/大图提示（本期头像点击走改名弹窗，不弹 snackbar）
- `Screen.CorgiDetail.route` — 已有路由
- `CorgiRepository.updateOutfit` 模式 — 模仿写 `updateAvatarPath`

---

## Commit 拆分（4 个 commit）

| # | 提交信息 | 文件 |
|---|---|---|
| 1 | `feat(数据层): CorgiData 新增 avatarPath 字段并升级 Room 至 v40` | CorgiData.kt / CorgiDao.kt / CorgiMemoDatabase.kt / CorgiRepository.kt |
| 2 | `feat(ui): 新增 UserAvatar 通用头像组件(首字母占位)` | UserAvatar.kt（新建） |
| 3 | `refactor(ui): AppDrawer 顶部改用 UserAvatar 并接 onProfileClick 回调` | AppDrawer.kt |
| 4 | `refactor(ui): Profile 头卡改为用户信息卡，接通 CorgiDetail 入口并加 Tab 切换动画` | ProfileHeroCard.kt / ProfileScreen.kt / MainScreen.kt |

每个 commit 后**不自动 git push**，不自动跑编译；按用户偏好用 AskUserQuestion 询问下一步。

---

## 验证方式（用户手动）

按 `.trae/rules/编译验证.md`，每阶段由用户手动 `./gradlew compileDebugKotlin` 验证：

| 阶段 | 验证项 |
|---|---|
| Commit 1 | Room v39→v40 升级不崩、已有柯基数据保留、avatarPath 默认 null |
| Commit 2 | UserAvatar 单独编译通过（无调用方） |
| Commit 3 | drawer 顶部渲染 48dp 首字母徽章、整行点击切到 Profile tab |
| Commit 4 | Profile 头卡 5 模块布局正确、改名弹窗文案变更、Tab 切换 180ms 淡入淡出、柯基图标/悬浮球跳 CorgiDetail |

端到端验证（commit 4 后）：
1. 启动 app → drawer 头像显示首字母徽章（用 `name` 字段）
2. 点击 drawer 头像 → drawer 平滑关闭，Profile tab 淡入显示
3. Profile 头卡显示用户头像 + 昵称 + "Lv.X · 柯基陪伴 N 天"
4. 点击头卡头像 → 弹"修改昵称"对话框
5. 点击顶部柯基爪子图标 → 跳 CorgiDetail（200dp 动画 + 互动按钮可见）
6. 点击柯基悬浮球 → 跳 CorgiDetail

---

## 风险与缓解

| 风险 | 严重度 | 缓解 |
|---|---|---|
| Room Migration_39_40 字段 DEFAULT 不匹配 | **高** | Entity `@ColumnInfo(defaultValue = "NULL")` ↔ SQL `DEFAULT NULL` 严格一致；卸载重装冷启动 + v39→v40 热升级都手动验证一次 |
| Profile 头卡移除三栏统计后信息密度降低 | 中 | 装扮入口 / 成就统计 / 7 天情绪图表均在下方滚动区，重要信息不丢；副标题"陪伴 N 天"做情感连接 |
| AnimatedContent 重建导致 ViewModel 重订阅闪烁 | 中 | `keepCurrentState = true`；`hiltViewModel()` 限定 ViewModel 生命周期到 NavBackStackEntry |
| `updateAvatarPath` Query 在 `id` 缺失时崩溃 | 低 | 用 `WHERE id = (SELECT id FROM corgi_data LIMIT 1)` 防御性写法；同时 CorgiData 必存在主键为 1 的行（项目初始化即插入） |
| Drawer 关闭与 selectedTab 切换时序 | 低 | 沿用现状"先 selectedTab 再 close" — 抽屉关闭动画期内 tab 已切换 |

---

## 暂不实现（后续任务）

- 头像上传（相册选择 + 内部存储 + 缩略图压缩）
- `Screen.AvatarPreview` 大图预览路由
- `UserAvatar` 的 `onAvatarLongClick`（长按更换头像）
- 平板 `WindowSizeClass` 自适应布局
- UserAvatar 单元测试
