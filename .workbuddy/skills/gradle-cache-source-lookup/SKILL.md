---
name: gradle-cache-source-lookup
description: This skill should be used when an AndroidX / Jetpack Compose / Kotlin library API signature is uncertain — e.g. a compile error reveals a wrong constructor, an abstract class being instantiated directly, or a method name/parameter mismatch. Instead of guessing from memory or stale docs, extract the real source .kt from the Gradle cache *-sources.jar and read the exact signature before fixing.
agent_created: true
---

# Gradle Cache Source Lookup

## Overview

Verify exact API signatures of AndroidX / Compose / Kotlin dependencies by reading the **real source code** shipped in the Gradle cache `*-sources.jar`, rather than relying on memory or outdated documentation. This eliminates a whole class of "guessed the signature wrong" compile errors and avoids extra build round-trips.

## When To Use

- A Kotlin/Compose compile error reveals an API mismatch: `Class 'X' is not abstract and does not implement abstract members`, `Cannot create an instance of an abstract class`, `overrides nothing`, `Argument type mismatch`, `No value passed for parameter`, `Expression 'x' of type 'T' cannot be invoked as a function`, `Unresolved reference`.
- Unsure about a library class constructor signature, interface method list, or whether a class is `abstract` / `sealed` / `open`.
- Unsure whether a member is a **`val` property** or a **`fun` function** (e.g. `change.position` vs `change.positionChange()`).
- Before fixing any dependency-API compile error — confirm the real signature first.

## Workflow

### Step 0 — 定位本技能自身（如果它没出现在可用技能列表里）

本技能位于**项目级**目录 `<项目根>/.workbuddy/skills/gradle-cache-source-lookup/`，不一定会被自动罗列。
检索时把**绝对路径传给 `path` 参数**、`pattern` 只写相对通配：

- 正确：`path = "C:/Users/Lenovo/Desktop/CorgiMemo"`，`pattern = ".workbuddy/skills/**/*.md"` → 正常命中
- 错误：`pattern = "C:/Users/Lenovo/Desktop/CorgiMemo/.workbuddy/skills/**"`（绝对路径塞进 pattern 会静默返回空，
  容易被误判成「技能不存在」——这个坑已经踩过一次）

（点目录本身不影响检索，纯粹是 pattern 写法的问题。）拿到路径后直接 Read `SKILL.md` 即可，无需等待技能加载。

### Step 1 — 定位 sources jar（hash 目录名未知时）

Gradle 缓存路径是：
`~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/<artifact>-<version>-sources.jar`

**`<group>` 是点分隔的扁平目录名**（如 `androidx.compose.ui`），**不是** `androidx/compose/ui` 这种嵌套路径 —— 这点务必记牢，按嵌套路径拼必然找不到。`<hash>` 是不可预测的sha1串。

不要靠记忆拼路径，先跑定位脚本（Windows 用 Git Bash，python 走绝对路径）：

```bash
"/c/ProgramData/Anaconda3/python.exe" \
  "<项目根>/.workbuddy/skills/gradle-cache-source-lookup/scripts/find_sources_jar.py" \
  --class PointerEvent --group androidx.compose.ui --version 1.11.2
```

脚本会打印命中的 jar 绝对路径 + 内部条目路径。全缓存扫描（约 419 个 jar）耗时 ~3 秒，不加 `--group` 也可接受；
带上 `--group` / `--artifact` / `--version` 能显著加速。

| 常用 group | 说明 |
|---|---|
| `androidx.compose.ui` | ui / ui-text / ui-graphics / ui-tooling 等（Android 版 artifact 名带 `-android` 后缀） |
| `androidx.compose.foundation` | foundation |
| `androidx.compose.material3` | material3 |
| `androidx.activity`、`androidx.lifecycle` | 架构组件 |

**两个高价值提示：**

1. **`--class` / `--match` 只给裸类名**（`AndroidFont`），**不要**写 `AndroidFont.kt`。多平台源码文件名常带 source-set 后缀，
   例如实际条目是 `androidMain/androidx/compose/ui/text/font/AndroidFont.android.kt`——带 `.kt` 反而匹配不到。
2. **`-android` 产物里同样含 `commonMain/`**。找不到时两个产物都要试：`PointerInputChange` 就位于
   `ui-android-1.11.2-sources.jar` 的 `commonMain/androidx/compose/ui/input/pointer/PointerEvent.kt` 里，
   而不是 `ui`（common）产物。反过来说，Android 专属实现在 `androidMain/` 下且只存在于 `-android` 产物。

### Step 2 — 抽取并阅读 `.kt`

拿 Step 1 输出的 jar 绝对路径（Windows 下把 `/` 写成 `\` 或保持 `/` 均可，脚本内部会 normpath）：

```bash
"/c/ProgramData/Anaconda3/python.exe" \
  "<项目根>/.workbuddy/skills/gradle-cache-source-lookup/scripts/extract_source.py" \
  --jar "C:/Users/Lenovo/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text-android/1.11.2/<hash>/ui-text-android-1.11.2-sources.jar" \
  --match "AndroidFont" --out /tmp/lookup
```

The helper lists all matching entries and extracts them to `--out`. Then **Read** the extracted `.kt` file.

Multiplatform sources use layout prefixes: `androidMain/...` for Android-specific classes, `commonMain/...` for common,
`jvmAndAndroidMain/...` for shared JVM/Android.

### Step 3 — Read the real signature, then fix

Open the extracted source and confirm:
- Constructor parameter order, names, and which params have defaults.
- Interface method signatures (names, `suspend`, parameter types — note they may take the concrete class, e.g. `AndroidFont`, not a base `Font`).
- Whether the class is `abstract` (requires a subclass) or `sealed`/`open`.
- **成员是 `val` 属性还是 `fun` 函数**（`change.position` vs `change.positionChange()`）。Compose 里这类
  「同名近义」成员很多，凭印象调用会直接编译失败。
- 该成员是否带 `@Deprecated`（顺带确认替代 API 名字）。

Apply the fix to match the real source exactly. Prefer reading the source over guessing even when a quick fix "looks right" — subtle differences (e.g. `loadBlocked` vs `loadBlocking`, `Font(resId)` vs `Font(file: File)`, `position` vs `positionChange()`) are exactly what cause repeated compile failures.

## Concrete Case Study

- `references/gradle_sources_lookup.md` — `AndroidFont`（Compose UI 1.11.2）真实签名，含 abstract-class-subclass +
  `TypefaceLoader.loadBlocking/awaitLoad` 模式，以及 `Color(Long)` 的位打包规则。

## Resources

### scripts/
- `find_sources_jar.py` — **先跑这个**。按类名在 Gradle 缓存里搜索包含它的 `*-sources.jar`（hash 未知时用）。
- `extract_source.py` — 已知 jar 路径时，按子串匹配抽出 `.kt` / `.java` 条目。

### references/
- `gradle_sources_lookup.md` — 三个已核实案例：① `AndroidFont`（`ui-text-android-1.11.2`）真实签名与
  abstract-subclass + `TypefaceLoader.loadBlocking/awaitLoad` 模式；② `PointerInputChange.position` 属性 vs
  `positionChange()` 函数 + 废弃 API 清单；③ `Color(Long)` 位打包规则。
