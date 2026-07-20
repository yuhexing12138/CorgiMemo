# 用户头像统一与「我的」页改造——最终提交计划

> 任务来源：本会话承接的 `user-avatar-profile-redesign` 4 步计划
> 当前状态：Step 1-3 已 commit 推送；Step 4-6 代码已编写完成，未提交
> 本计划只负责收尾：处理无关 import + 提交 commit 4 + 询问下一步

---

## 1. 当前状态盘点

### 1.1 已推送的 3 个 commit（不动）

| Commit | 内容 |
|---|---|
| `93814fec` | `feat(数据层)`: CorgiData 新增 `avatarPath` + Room 升级 v40 + DAO/Repository |
| `747bfc2d` | `feat(ui)`: 新增 `UserAvatar` 通用头像组件（首字母占位 + Coil 预留） |
| `8cb8b8b4` | `refactor(ui)`: AppDrawer 顶部改用 `UserAvatar` 并接 `onProfileClick` 回调 |

master 比 origin 领先 9 个 commit。

### 1.2 当前工作区未提交（5 文件）

| 文件 | 改动行数 | 是否本次任务 | 处理策略 |
|---|---|---|---|
| `MainScreen.kt` | +71 / -27 | ✅ 是 | 提交 |
| `ProfileScreen.kt` | +9 / -7 | ✅ 是 | 提交 |
| `ProfileHeroCard.kt` | +63 / -147 | ✅ 是 | 提交 |
| `CorgiDetailScreen.kt` | +3 | ❌ 无关 IDE import | **还原**（与本任务无关） |
| `HomeScreen.kt` | +1 | ❌ 无关 IDE import | **还原**（与本任务无关） |

**无关 import 还原的原因**：
- 两个文件仅新增了 `rememberModalBottomSheetState` / `BehaviorType` / `Dialog` 等 import，**未实际使用**
- 属于之前会话提到的"IDE 或其他进程自动修改"现象
- 保留会污染本次 commit 信息、降低 diff 可读性
- 若未来真的需要这些符号，由具体任务再补

### 1.3 未追踪文件

- `.trae/documents/user-avatar-profile-redesign.md` → 本次任务文档，**保留追踪但不入仓**（`.trae/` 已被忽略）
- `.trae/documents/成就卡片优先级视觉降权标准.md` → 上一会话遗留，**不动**

---

## 2. 提交计划（commit 4）

### 2.1 还原无关改动

```powershell
cd c:\Users\EDY\Desktop\CorgiMemo
git checkout -- app/src/main/java/com/corgimemo/app/ui/screens/corgi/CorgiDetailScreen.kt
git checkout -- app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
```

### 2.2 暂存 3 个相关文件

```powershell
git add `
  app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt `
  app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt `
  app/src/main/java/com/corgimemo/app/ui/screens/profile/components/ProfileHeroCard.kt
```

### 2.3 写入 commit message

**Commit 信息（中文，遵循既有 `refactor(ui): ...` 风格）**：

```
refactor(ui): Profile 头卡改为用户信息卡,接通 CorgiDetail 入口并加 Tab 切换动画

本次为用户头像统一改造的收尾 commit,主要变更:

1. ProfileHeroCard 重写
   - 移除柯基 emoji 头像、等级徽章、经验进度条、累计/连续/情绪三栏统计
   - 改用 UserAvatar 72dp,展示用户头像(首字母占位)
   - 副标题改为"Lv.X · 柯基陪伴 N 天"
   - 柯基相关内容已迁出到 CorgiDetailScreen

2. ProfileScreen 头卡调用调整
   - 移除废弃参数(levelStage/progressText/hapticEnabled/soundEnabled)
   - 传入 consecutiveDays
   - 改名弹窗文案"柯基的名字"→"昵称","修改柯基名字"→"修改昵称"

3. MainScreen 集成
   - 抽屉宽度响应式:screenWidth*0.8f,约束在 280-360dp
   - onProfileClick 接线:点击顶部头像→selectedTab=PROFILE + 关闭 drawer
   - Tab 切换加 AnimatedContent 淡入淡出(180ms in / 140ms out)
   - 顶部柯基图标 + 悬浮柯基球:接通 CorgiDetailScreen,移除"开发中"snackbar

需求: 侧滑栏顶部头像点击跳"我的" + 头像统一 + 我的页用户化
```

### 2.4 执行 commit

```powershell
# 写入 HEREDOC 到临时文件避免 PowerShell 转义问题
$msg = @"
refactor(ui): Profile 头卡改为用户信息卡,接通 CorgiDetail 入口并加 Tab 切换动画

... (上面整段) ...
"@
$msg | Out-File -FilePath .git/COMMIT_EDITMSG_TMP -Encoding utf8
git commit -F .git/COMMIT_EDITMSG_TMP
Remove-Item .git/COMMIT_EDITMSG_TMP
```

---

## 3. 提交后验证

### 3.1 立即可做的只读检查

```powershell
git log --oneline -5          # 确认 commit 4 已落地
git status                    # 确认工作区干净(除 2 个未追踪 .trae 文档)
git diff HEAD~1 --stat        # 确认仅 3 文件变动
```

### 3.2 必须询问用户的步骤（**不自动执行**）

按项目规则 `.trae/rules/编译验证.md`，**不擅自跑 `gradlew`**；按 `.trae/rules/git提交.md`，commit 后必须用 `AskUserQuestion` 询问：

1. **是否手动执行 `./gradlew assembleDebug` 验证编译？**
2. **是否 `git push origin master`？**

---

## 4. 4 大需求覆盖度自检

| 需求 | 实现位置 | 状态 |
|---|---|---|
| ① 侧滑栏点击头像→"我的"页 | `AppDrawer.DrawerUserHeader.onClick` + `MainScreen.onProfileClick` | ✅ |
| ② 头像一致性 | `UserAvatar` 统一组件，drawer 48dp / profile 72dp | ✅ |
| ③ 我的页用户化 + 柯基迁移 | `ProfileHeroCard` 重写 + `MainScreen` 顶部柯基/悬浮球接 `CorgiDetail` | ✅ |
| ④ 视觉规范（点击查看大图、响应式） | `UserAvatar` 接受 `onClick`（占位）+ `ModalDrawerSheet` 响应式宽度 | ✅（占位实现） |

后续任务（**不在本次范围**）：
- 头像上传功能实现
- 头像点击查看大图功能

---

## 5. 风险与注意

- ⚠️ **无关 import 已还原**：若 `CorgiDetailScreen` / `HomeScreen` 真的在别处用到这些 import，会暴露编译错误——但本会话内未发现使用点
- ⚠️ **未追踪文档不入仓**：`.trae/documents/user-avatar-profile-redesign.md` 不参与 commit
- ✅ **head 卡视觉锚点保持一致**：渐变背景（primaryContainer → surface）+ 圆角 20dp + 头像 72dp，左对齐 Row 布局均与改造前同构，仅内容替换

---

## 6. 执行步骤顺序

1. 还原 2 个无关 import 改动（`git checkout`）
2. 暂存 3 个相关文件（`git add`）
3. 写入 commit message 到临时文件
4. `git commit -F`
5. 删除临时文件
6. `git log` 验证
7. 用 `AskUserQuestion` 询问用户：**是否手动编译验证** + **是否 push**

---

## 7. 优化建议（任务完成后输出）

将在 commit 4 落地后提供，参考 `.trae/rules/优化建议.md`。
