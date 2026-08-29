# Kuikly 接入 CorgiMemo —— 改动前注意事项与检查清单

> 本文档整理在把 Kuikly 接入 CorgiMemo 工程**之前**，必须先完成的检查项与必须清楚的注意事项。
> 适用对象：已能用 Android Studio 编译并在手机上验证 CorgiMemo 的开发者（含小白）。
> 知识来源：`kuikly-integration/SKILL.md`（Kuikly 工程接入技能文档）及对本工程的实际排查。

---

## 0. 背景与结论

### 当前 CorgiMemo 工程现状（已确认）
- 是**现有 Android 工程**：根项目 `CorgiMemo`，含 `:app`（单平台 Android）、`:richeditor-compose`（KMP 模块，含 `androidMain/commonMain/iosMain/jsMain/wasmJsMain/desktopMain`）。
- 已使用 **Kotlin Multiplatform**（团队有 KMP 经验）。
- 使用 **Jetpack Compose / Hilt / KSP**，工具链版本 **Kotlin 2.4.0 + AGP 9.2.1**。
- 工程中**目前没有任何 Kuikly 依赖或代码**。

### 结论（更新：2026-08-29 已完成 1.3 实测）
⚠️ **当前版本不兼容，暂不能直接接入。** 经实测核对（见 1.3）：CorgiMemo 的 **Kotlin 2.4.0** 高于 Kuikly 当前支持的最高 **Kotlin 2.1.21**，且 Kuikly 依赖按 Kotlin 版本发独立 artifact 变体，仓库中**不存在 2.4.0 变体**，直接接入会 Gradle 解析失败。

> 技术架构上仍**适合**增量接入（新增 `:shared` + 渲染器 + 适配器，不改现有代码），但**必须先把 Kotlin/AGP 降到 Kuikly 支持的版本，或等 Kuikly 发布 2.4.0 变体**。详见 1.3 的三种方案。

---

## 1. 必须做的检查（动手前先完成）

### 1.1 运行环境检查（doctor）
使用 Kuikly CLI 做结构化环境检查，确认 Node / Java / Android SDK 齐全：
```bash
npx --yes @kuikly-ai/create-kuikly-app@latest --json doctor
```
- 所有 CLI 命令都加 `--json`，便于解析。
- 缺失工具（如 JDK、Android SDK）时**不要自动安装**，应告知开发者手动安装后再继续。

### 1.2 JDK 版本必须是 17（硬性要求）
- Kuikly / Android 构建**必须 JDK 17**；**JDK 18+ 会构建失败**。
- Android Studio 版本 ≥ 2024.2.1 时，默认 Gradle JDK 可能为 **21**，需手动切回 17：
  ```
  Android Studio → Settings → Build, Execution, Deployment
    → Build Tools → Gradle → Gradle JDK → 选择 JDK 17
  ```
- 确认本机 `JAVA_HOME` 指向 JDK 17。

### 1.3 版本兼容性核对（★ 最高优先级风险）—— 实测结论：❌ 不兼容

#### 实测方法（不改动 CorgiMemo 工程）
1. 确认 CorgiMemo 锁定的版本：读 `gradle/libs.versions.toml` 与根 `build.gradle.kts` 插件声明。
2. 在临时目录（`C:/Temp/kuikly_probe`）用 CLI 生成最小 Kuikly 工程，读其锁定的版本。
3. 直接查腾讯 Maven 仓库 `com.tencent.kuikly-open` 的 `core` / `core-render-android` **已发布的所有 Kotlin 变体**。

#### 实测数据对照
| 项 | CorgiMemo（宿主） | Kuikly 官方支持 / 脚手架默认 | 仓库实际发布 |
|---|---|---|---|
| **Kotlin** | **2.4.0** | 最高 2.1.21（README 支持列表：1.3.10~2.1.21） | `1.7.20 / 1.8.21 / 1.9.22 / 2.0.21 / 2.0.21-ohos / 2.1.21`，最新 `2.26.0-2.1.21` |
| **AGP** | **9.2.1** | 脚手架默认 8.10.1；官方文档提及 7.4.2 | 仓库未硬性限制，但**未验证 9.x** |

> Kuikly 依赖按 Kotlin 版本发独立 artifact：`com.tencent.kuikly-open:core:2.26.0-2.1.21`（后缀即编译所用 Kotlin 版本）。**不存在 `2.26.0-2.4.0` 这种变体。**

#### 结论
- ❌ **Kotlin 不兼容（硬阻断）**：宿主 Kotlin 2.4.0 > Kuikly 支持上限 2.1.21，仓库中无 2.4.0 对应 artifact，接入会直接 `Could not find com.tencent.kuikly-open:core:2.26.0-2.4.0` 解析失败。
- ⚠️ **AGP 未验证风险**：9.2.1 高于 Kuikly 验证过的 8.10.1 / 7.4.2，即便 Kotlin 解决，AGP 9 与 Kuikly 渲染器的兼容性也需实测。

#### 可行方案（任选其一后再接入）
- **方案 A（推荐，若想现在接入）**：把 CorgiMemo 的 **Kotlin 降到 2.1.21、AGP 降到 8.10.1**（与 Kuikly 对齐）。⚠️ 但本工程 Kotlin 2.4.0 是为对齐 `compose-rich-editor` 库 v1.0.0、KSP 2.3.10、AGP 9 内置 Kotlin 模式，**降级可能牵动现有依赖，需先评估影响（建议新建分支试降）**。
- **方案 B（最省事）**：等待 Kuikly 发布 **2.4.0 Kotlin 变体**（关注 ChangeLog），再直接接入，现有工程零改动。
- **方案 C（不推荐）**：强行用 `2.26.0-2.1.21` 变体塞进 2.4.0 工程——Kotlin 多平台元数据（2.1 vs 2.4）不兼容，大概率编译失败。

> ✅ 本步骤核对基于**实测证据**（仓库元数据 + 脚手架生成），非凭记忆。

### 1.4 Android SDK 与构建工具
- 确认已安装 Android SDK，且 `local.properties` 中 `sdk.dir` 指向正确路径。
- 确认 `ANDROID_HOME` 已配置。

### 1.5 现有工程结构确认
- 确认 `settings.gradle.kts` 中已 `include(":app")`（本工程已包含）。
- 确认 `:app` 为普通 Android Application 模块（接入时只在其内**新增**文件，不改现有代码）。

---

## 2. 改动前必须清楚的注意事项

### 2.1 接入是非侵入式的（不覆盖、不改现有代码）
- Kuikly 接入原则是**直接往现有工程添加 KMP `:shared` 模块**，不覆盖或替换工程。
- 不要修改用户原有的启动 Activity 或任何已有代码；Kuikly 页面通过 `KuiklyRenderActivity.start()` **按需跳转**进入。
- 现有 Compose / Hilt 业务代码渲染方式完全不变。

### 2.2 两套渲染引擎共存，不能混排
- Kuikly 有**自己独立的渲染引擎**（渲染器 `core-render-android`），与 Jetpack Compose **不互通**。
- **不能**在同一个屏幕的视图树里把 Compose 组件和 Kuikly 组件混排。
- 两者是「屏幕级」关系：靠 `IKRRouterAdapter` 路由在两者之间跳转。
- 渲染产物是**各平台原生控件**（非 WebView、非自绘 Canvas），性能接近原生。

### 2.3 Kuikly Compose DSL ≠ Jetpack Compose
- Kuikly 的 Compose DSL 写法（继承 `Pager` / `willInit()` / `setContent{}` / `Modifier`）**语法像** Jetpack Compose，但是**不同框架、不同运行时、不同渲染器**。
- 现有 `@Composable` 组件**不能**直接塞进 Kuikly 页面，需要按 Kuikly API 重写。

### 2.4 多端环境门槛
- 当前 `:app` 仅 Android。**要扩到 iOS / 鸿蒙需补环境**：
  - iOS：需 **Mac + Xcode + CocoaPods**（`pod install --repo-update`）。
  - 鸿蒙：需 **DevEco Studio**，且鸿蒙模拟器推荐 Apple Silicon Mac。
- 纯 Android 验证全程可在 Android Studio 完成，无需上述环境。

### 2.5 `:shared` 是 KMP 模块
- 跨端业务代码放在 KMP `:shared` 模块，编译为各端产物：
  - Android → `.aar`
  - iOS → `.xcframework`
  - 鸿蒙 → `.so` + ArkTS
- 本工程已有 `:richeditor-compose` 这个 KMP 模块，团队具备 KMP 基础。

### 2.6 版本号一致性 + 2.5.0+ 的 maven 源
- `core-render-android` 与 `core` 的版本号**必须与 KMP `:shared` 工程保持一致**。
- Kuikly **2.5.0 版本后**需额外添加腾讯 maven 源：
  ```kotlin
  maven("https://mirrors.tencent.com/repository/maven-tencent/")
  ```
- 接入后需检查 `shared/build.gradle.kts`、宿主模块、`Podfile`、鸿蒙 `oh-package.json5` 中的版本号一致。

### 2.7 必须实现的四个适配器（Adapters）
Kuikly 渲染器不知道你 App 的原生能力，需提供「翻译官」：
| 适配器 | 作用 |
|---|---|
| `IKRImageAdapter`（图片） | 用宿主图片库（Glide / Coil）加载图片 |
| `IKRLogAdapter`（日志） | 日志打到 `Log.xxx` |
| `IKRRouterAdapter`（路由） | 打开/关闭页面用 `startActivity` / `finish()` |
| `IKRThreadAdapter`（线程） | 后台任务用原生线程池（Compose 场景建议 `stackSize() = 8MB`） |

写完后在 `KuiklyRenderActivity` 的 `companion object init` 中**一次性注册**。

### 2.8 文件与修复范围约束
- 仅修改**项目目录内**文件，不修改项目外文件。
- 构建失败时，**仅修改 `shared/src/` 下的 `.kt` 文件**进行修复，不改动平台宿主代码。

---

## 3. 改动后的验证方式（Android Studio）

- ✅ 接入后**仍可在 Android Studio 里编译并装到手机验证**，日常操作不变：
  - `:app` 模块只**新增**渲染器依赖 + `KuiklyRenderActivity` + 适配器；
  - `:shared` 为新增 KMP 模块，里面是你的 Kuikly 页面（`.kt` 文件）。
- 验证时像现在一样 **Run ▶ 把 `:app` 装到手机**，再从一个按钮调用：
  ```kotlin
  KuiklyRenderActivity.start(context, "test", JSONObject())
  ```
  进入 Kuikly 页面，看到绿色 "Hello Kuikly" 即接入成功。
- 前提仍是：**Gradle JDK = 17** 且 **Kotlin/AGP 版本已兼容**（见 1.2、1.3）。

---

## 4. 两种接入路径（先想清楚再动手）

| 路径 | 说明 | 适用 |
|---|---|---|
| **增量接入（推荐）** | 现有 `:app` 不动，新增 `:shared` + 渲染器 + 适配器；新页面用 Kuikly 写，跨端跑 Android/iOS/鸿蒙 | 想逐步引入、保留现有 Compose 代码 |
| **整体迁移** | 把 CorgiMemo 界面逐步用 Kuikly DSL / Compose DSL 重写，获得真正「一套代码多端」 | 目标就是全跨端重构，工程量大、需按模块推进 |

---

## 5. 来源与参考（均来自 `kuikly-integration/SKILL.md`）

- 整体架构与 KMP 产物映射：第 35–53 行
- 现有工程接入模式（模式二）：第 240–266 行
- 环境搭建 / JDK 17 要求：第 270–291 行
- 添加 KMP `:shared` 模块 / 版本一致性 / 2.5.0+ maven 源：第 315–397 行
- Android 平台接入（渲染器依赖、承载容器、适配器、注册）：第 406–606 行
- 编写 TestPage 验证：第 1255–1298 行
- 官方资源：
  - 官方文档：https://kuikly.tds.qq.com/DevGuide/dev-guide-overview.html
  - GitHub：https://github.com/Tencent-TDS/KuiklyUI
  - 版本变更日志：https://kuikly.tds.qq.com/ChangeLog/changelog.html

---

## 6. 推荐起步动作

1. 运行 `doctor` 环境检查（1.1）。
2. **核实 Kuikly 对 Kotlin 2.4.0 / AGP 9.x 的兼容性**（1.3，最高优先级）。
3. 确认本机 Gradle JDK = 17（1.2）。
4. 上述均通过后，按「模式二」在 `:app` 中**新增** `:shared` 模块 + 渲染器 + 适配器，不改动现有代码。

---

## 7. 检查执行记录

### 2026-08-28 环境检查（1.1）复检结果：`all_ok` ✅
- 命令：`npx --yes @kuikly-ai/create-kuikly-app@latest --json doctor`
- **首次检查**：Node / JDK 17 / Android SDK / Kotlin / Git 为 ok；Gradle（未装全局）、OpenHarmony SDK / hvigorw / ohpm / hdc 为 warning（均为可选 / 鸿蒙专用）。
- **用户按指引配置环境变量后复检，10 项全部 ok**：
  - Node v22.22.2、JDK **17.0.19**（满足 1.2 硬性要求）、Gradle 8.14.5、Android SDK 已识别、Kotlin ok
  - OpenHarmony SDK = `C:\DevEco_Studio\DevEco Studio\sdk\default`、hvigorw 6.24.4、ohpm 6.1.2.285、hdc 3.2.0、Git 2.55.0
- **结论：1.1 通过，1.2（JDK 17）同步满足。** 当前 Android + 鸿蒙双端环境均已就绪；iOS 仍需 Mac + Xcode（doctor 不检查，不影响当下）。

### 2026-08-29 版本兼容性核对（1.3）：❌ 不兼容（实测）
- **方法**：读 CorgiMemo `gradle/libs.versions.toml`（Kotlin 2.4.0 / AGP 9.2.1）→ 临时目录 CLI 生成最小 Kuikly 工程（脚手架默认 Kotlin 2.0.21~2.1.21、AGP 8.10.1）→ 直查腾讯 Maven 仓库 `com.tencent.kuikly-open:core` / `core-render-android` 已发布版本。
- **发现**：Kuikly 依赖按 Kotlin 版本发变体（如 `2.26.0-2.1.21`），仓库**所有变体仅到 Kotlin 2.1.21**，无 2.4.0 变体；AGP 方面 Kuikly 仅验证过 8.10.1 / 7.4.2，未验证 9.2.1。
- **结论**：❌ **Kotlin 2.4.0 高于 Kuikly 支持上限 2.1.21，硬阻断**，直接接入会 Gradle 解析失败；AGP 9.2.1 亦属未验证范围。
- **下一步（三选一）**：① CorgiMemo 的 Kotlin 降到 2.1.21 + AGP 降到 8.10.1（需评估对现有依赖影响）；② 等 Kuikly 发布 2.4.0 变体；③ 不推荐强行混用。
- **状态：1.3 已完成，结果为「不兼容」，接入工作暂停，待版本问题解决后再继续。**

### 检查总进度
- [x] 1.1 环境检查（doctor）：✅ all_ok（10 项全过）
- [x] 1.2 JDK 17：✅ 17.0.19
- [x] 1.3 版本兼容性核对：❌ 不兼容（Kotlin 2.4.0 > 2.1.21 上限）
- [x] 1.4 Android SDK：✅ 已识别
- [x] 1.5 工程结构确认：✅ 现有 Android 工程

> 下一步（待你定夺）：选 1.3 中方案 A / B / C 之一，再决定是否继续接入。

---

## 8. 方案 D（AAR 桥接）执行记录 —— 已产出 AAR ✅

因 1.3 判定不兼容，改走**方案 D**：主工程**不降级**，另建独立 Kuikly 工程产出 AAR 供主工程引入。

### 8.1 已完成

| 项 | 状态 |
|---|---|
| 独立 Kuikly 工程 `CorgiMemo/kuikly-shared/` | ✅ 已生成，未被主工程 settings include（完全隔离） |
| 版本组合（Gradle 8.11.1 + AGP 8.10.1 + Kotlin 2.1.21 + Kuikly 2.26.0-2.1.21） | ✅ 与官方支持范围匹配 |
| `shared-release.aar` 构建 | ✅ `assembleRelease` BUILD SUCCESSFUL |
| AAR 内容校验 | ✅ classes.jar 含 `RouterPage` / `BasePager` / **`KuiklyCoreEntry`**（页面注册入口）+ assets |

- 主工程侧版本**保持原样**：Kotlin 2.4.0 / AGP 9.2.1 / Gradle 9.6.1，未做任何降级。

### 8.2 过程中踩到的坑（重要，避免重复排查）

| # | 现象 | 真实原因 | 解法 |
|---|---|---|---|
| 1 | Gradle 分发下载超时 | `services.gradle.org` 国内不可达 | 改用腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip`（阿里云该路径 404） |
| 2 | `IOException: 文件名、目录名或卷标语法不正确` | 新工程**缺 `local.properties`**，AGP 拿到空 SDK 路径（报错极具误导性） | 复制主工程 `local.properties`（`sdk.dir` 需 `\:` 与 `\\` 转义） |
| 3 | 同类 IOException / `jsBrowserDevelopmentExecutableDistribution not found` | Kuikly Gradle 插件**强依赖 js target** | `js(IR)` target 与 `KuiklyConfig.js{}` 必须**成对保留**（可只配置不编译）；iOS/CocoaPods 可安全移除 |
| 4 | `compileCommonMainKotlinMetadata` 报 Unresolved reference | KSP 把 Android 专属 `KuiklyCoreEntry` 生成到了 commonMain metadata | 升级 Kuikly 2.26.0、清空 `kspCommonMainMetadata` 均无效 → **改用 `assembleRelease`**（Android 编译本身成功，不需要 metadata 产物） |
| 5 | aapt2 `RES_TABLE_TYPE_TYPE entry offsets overlap` | AGP 8.10.1 **自带 aapt2**（与 build-tools 目录无关）解析不了本机新版 android.jar | 改 `buildToolsVersion` 无效；shared 无 res 资源 → 跳过 `verify*Resources` 任务 |
| 6 | `rm -rf` / Gradle `clean` 失败 | 安全删除拦截 + 文件被 daemon 占用 | 先 `./gradlew --stop` |
| 7 | 独立 `androidApp` 无法编译 | 它含 res 资源，不能跳过 aapt2 校验 | **取消独立 App 验证，改为直接在主工程（AGP 9.2.1）中接入并验证** |

### 8.3 任务 4：主工程接入（代码已完成，编译验证中）

#### 8.3.1 git 安全网（⚠️ 有一个必知的坑）

已建分支 `kuikly/aar-bridge`（指向 `54cdd071`，与 master 同起点）。

**坑**：本仓库安装了 graphify 的 `.githooks/post-checkout`，它会打断 `git checkout -b`，
使 `.git/HEAD` 指向**不存在的** `refs/heads/kuikly/aar-bridge`。表现为：

- `git log` 报 `your current branch does not have any commits yet`
- **7768 个文件被误暂存为新增**（连 `.gitignore`、`.gitmodules` 都变成新文件）

> ⚠️ **极度危险**：此状态下执行 `git commit` 会创建**无父提交的孤立根提交**，直接破坏提交历史。

**修复**：`git branch` 与 `git update-ref` 均**静默失败**（返回成功但 ref 未创建），
最终用底层写入解决（均不触发 post-checkout hook）：

```bash
mkdir -p .git/refs/heads/kuikly
git rev-parse master > .git/refs/heads/kuikly/aar-bridge
git symbolic-ref HEAD refs/heads/kuikly/aar-bridge
```

**结论：本仓库不要用 `git checkout -b` 建分支。**

#### 8.3.2 新增文件（位于 `app/src/main/java/com/corgimemo/app/kuikly/`）

| 文件 | 作用 |
|---|---|
| `KuiklyContextHandler.kt` | 上下文处理器（按官方 demo `ContextCodeHandler` 精简，去掉自定义 Module 与性能监控）；核心调用 `delegator.onAttach(container, "", pageName, pageData)` |
| `KuiklyAdapters.kt` | `KRLogAdapter` / `KRRouterAdapter` / `KRThreadAdapter` / `KRImageAdapter` / `KRExceptionAdapter` |
| `KuiklyRenderActivity.kt` | 承载页 + 适配器注册 + `start(context, pageName, pageData)` 跳转入口 |

#### 8.3.3 修改的现有文件（新增式改动，不动业务代码）

| 文件 | 改动 |
|---|---|
| `settings.gradle.kts` | 新增腾讯 Maven 源（`FAIL_ON_PROJECT_REPOS` 下仓库必须在 settings 声明） |
| `app/build.gradle.kts` | 新增 `core` / `core-render-android` 2.26.0-2.1.21 + AAR 依赖 |
| `AndroidManifest.xml` | 注册 `.kuikly.KuiklyRenderActivity`（`exported=false`） |
| `ui/MainActivity.kt` | `OnboardingRouter` 的 Box 内新增临时悬浮按钮（左下角，文字 "K"）作为跳转入口 |

#### 8.3.4 两个关键决策（避坑）

1. **用 `ComponentActivity` 而非官方示例的 `AppCompatActivity`**
   主工程主题为 `Theme.CorgiMemo`（非 AppCompat 主题），用 AppCompatActivity 启动即崩：
   `You need to use a Theme.AppCompat theme (or descendant) with this activity`。
   Kuikly 只需一个能承载原生 View 的 Activity，ComponentActivity 完全满足。

2. **AAR 用 `files()` 而非 flatDir 写法**
   Gradle 9.6.1 已不支持 `implementation(name = "...", ext = "aar")`，会报
   `No parameter with name 'name' found`。改用：

   ```kotlin
   implementation(files("../kuikly-shared/shared/build/outputs/aar/shared-release.aar"))
   ```

#### 8.3.5 Kuikly API 来源（均经查证，非凭记忆）

- 适配器包名：`com.tencent.kuikly.core.render.android.adapter.*`
  （注意官方拼写是 `performace`，不是 performance）
- 打开页面：`KuiklyRenderViewBaseDelegator.onAttach(ViewGroup, String, String, Map)`
  第 2 个参数为 turboDisplayKey，传空串（与官方 demo 一致）
- 执行模式：`KuiklyRenderCoreExecuteModeBase.JVM`
- 适配器注册：`KuiklyRenderAdapterManager`
- 官方参考实现：
  `https://github.com/Tencent-TDS/KuiklyUI/blob/main/androidApp/src/main/java/com/tencent/kuikly/android/demo/ContextCodeHandler.kt`

#### 8.3.6 验证方式（在 Android Studio 中）—— ✅ 已验证通过（2026-08-29）

> ✅ **验证结果**：用户在 Android Studio 编译并装机成功；点击 "K" 按钮跳转到 Kuikly 页面，
> 绿色 "hello kuikly" 正常显示。**方案 D（AAR 桥接）正式成立**——主工程 Kotlin 2.4.0
> 成功读取 Kotlin 2.1.21 编译的 AAR，全程零降级、零破坏。

> 说明：命令行编译在本机受沙箱限制（Gradle 无法访问 `~/.gradle` 缓存目录，报
> `gradle-9.6.1-bin.zip.lck (拒绝访问.)`），因此验证改在 Android Studio 中进行。
> 这也更直接 —— 可以一步完成「编译 + 装手机 + 看渲染」。

**步骤：**

1. 用 Android Studio 打开 `C:\Users\Lenovo\Desktop\CorgiMemo`
2. 确认 Gradle JDK 为 17：
   `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK → 选 17`
3. 同步：`File → Sync Project with Gradle Files`，等同步完成
   - 首次同步会下载 Kuikly（`core` / `core-render-android`）依赖，需几分钟
4. 编译：`Build → Make Project`（或 `Build → Build Bundle(s)/APK(s) → Build APK(s)`）
   - **首次编译较慢**：KSP 处理 Room + Hilt 可能 10~20 分钟，请耐心等待、不要中断
5. 装到手机：连接手机（开启 USB 调试），点 `Run ▶`
6. 验证渲染：进入 App 主界面后，点击**铅笔 FAB 正上方的 "K" 悬浮按钮**
   （已从原左下角上移，避免遮挡底部「待办」按钮）
   - ✅ 成功：跳转到 Kuikly 页面，显示绿色文字 **hello kuikly**
   - AAR 自带的 assets 示例图（`image_adapter/sample.png`）也会一并打包进 APK

> **核心验证点**：主工程 Kotlin 2.4.0 能否成功编译并读取 Kotlin 2.1.21 编译的 AAR。

#### 8.3.7 出问题怎么排查

| 现象 | 原因 / 处理 |
|---|---|
| Sync 报 Kuikly 依赖找不到 | 检查 `settings.gradle.kts` 中腾讯 Maven 源已添加；或检查网络/代理 |
| 报 `shared-release.aar` 不存在 | 需在 `kuikly-shared` 目录重新构建：`.\\gradlew :shared:assembleRelease` |
| 编译停在 KSP 很久 | 正常（Room + Hilt 注解处理慢）；**不要中断**，中断会残留 `.lck` 锁文件 |
| 点 "K" 后闪退 / 白屏 | 打开 Logcat，过滤 `KuiklyError` 或 `ContextCodeHandler` 查看异常 |
| 报 `You need to use a Theme.AppCompat` | 说明基类被改回 AppCompatActivity，应保持 `ComponentActivity` |
| Gradle wrapper 报 `.lck (拒绝访问)` | 有残留锁文件，删除 `C:\Users\Lenovo\.gradle\wrapper\dists\gradle-9.6.1-bin\<hash>\gradle-9.6.1-bin.zip.lck` |

**若日后修改了 Kuikly 页面**，需重新产出 AAR（在 `kuikly-shared` 目录）：

```bash
./gradlew :shared:assembleRelease
```

#### 8.3.8 验证通过后如何移除临时入口

> ✅ 验证已通过（2026-08-29）。

**临时入口当前位于 `HomeScreen.kt`**（铅笔 FAB 正上方；原左下角位置会遮挡底部「待办」按钮，已上移）。
删除 `HomeScreen.kt` 中「临时验证入口」代码块及 import
`com.corgimemo.app.kuikly.KuiklyRenderActivity` 即可完全还原。
（`MainActivity.kt` 已还原，无需再动。）

> 若确定不再使用 Kuikly，可进一步删除 `app/src/main/java/com/corgimemo/app/kuikly/`
> 整个目录、`AndroidManifest.xml` 中的 Activity 注册、`app/build.gradle.kts` 中的
> Kuikly 依赖与 AAR 引用、`settings.gradle.kts` 中的腾讯 Maven 源，
> 以及 `kuikly-shared/` 目录。
