import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    /** Kotlin 2.0+ 必须显式应用 Compose Compiler 插件 */
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

// ============================================================================
// BlockNote 编辑器产物构建（v1.8）
//
// 背景：app/src/main/assets/blocknote-web/editor/editor.html 是 vite 用
// viteSingleFile 打出的**单文件内联产物**（约 1.88MB），源码在 blocknote-probe/。
// Gradle 只把 assets 当静态资源原样打包，**不会**触发 npm 构建——曾因此出现
// 「JS 源码已改、App 也已重编，但真机仍是旧 bundle」的事故（真机实测踩坑）。
//
// 这里把 vite 构建注册为 Gradle task，并让 assets 资源任务依赖它，做到：
//   1) assembleDebug/Release 自动带上最新产物，不再依赖人工记得跑 npm
//   2) 用 inputs/outputs 声明实现**增量**——源码未变时 SKIPPED，不浪费 45 秒
//   3) 任何失败都不阻断 App 编译（仅告警），避免未装 node 的机器无法构建
//
// 手动强制重建：./gradlew :app:buildBlockNoteEditor --rerun-tasks
// ============================================================================

/** blocknote-probe 子工程根目录（vite 工程位置） */
val blocknoteProbeDir = rootProject.file("blocknote-probe")

/** 编辑器产物输出目录（vite.editor.config.ts 的 outDir 指向此处） */
val blocknoteEditorOutDir = file("src/main/assets/blocknote-web/editor")

/** 编辑器产物文件名 */
val blocknoteEditorArtifact = blocknoteEditorOutDir.resolve("editor.html")

/**
 * 解析 npm 可执行文件路径。
 *
 * Windows 下 npm 实际是 npm.cmd（批处理），Gradle 直接执行 "npm" 会因找不到
 * 可执行文件而失败；这里按平台显式补后缀。
 */
fun resolveNpmExecutable(): String {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    return if (isWindows) "npm.cmd" else "npm"
}

/**
 * 构建 BlockNote 编辑器产物（editor.html）。
 *
 * 通过 inputs.dir 声明「编辑器源码 + 构建配置」为输入、editor.html 为输出，
 * 由 Gradle 的增量检查决定是否需要真正执行——源码未动时该 task 显示 UP-TO-DATE。
 */
val buildBlockNoteEditor by tasks.registering(Exec::class) {
    group = "blocknote"
    description = "构建 BlockNote 编辑器产物（vite build:editor → assets/blocknote-web/editor）"

    workingDir = blocknoteProbeDir
    commandLine(resolveNpmExecutable(), "run", "build:editor")

    // 增量输入：编辑器源码、入口 html、vite 配置、依赖清单
    inputs.dir(blocknoteProbeDir.resolve("src"))
    inputs.file(blocknoteProbeDir.resolve("editor.html"))
    inputs.file(blocknoteProbeDir.resolve("vite.editor.config.ts"))
    inputs.file(blocknoteProbeDir.resolve("package.json"))
    inputs.file(blocknoteProbeDir.resolve("package-lock.json"))
        .withPropertyName("packageLock")
        .optional(true)

    // 增量输出：产物本身（vite 每次重排压缩短变量名，内容必然变化，不影响增量判定）
    outputs.file(blocknoteEditorArtifact)

    /**
     * 失败标记文件：存在即代表「上次构建失败」，用于强制下次重跑。
     *
     * 为什么不直接用 outputs.upToDateWhen { executionResult... }：
     * up-to-date 检查发生在 task **执行之前**，那会儿 executionResult 还不存在，
     * 读取会抛异常。所以改用「失败即落一个标记文件」的方式间接判断：
     * 标记存在 → upToDateWhen 返回 false → 必然重跑；成功则删掉标记。
     */
    val failureMarker = layout.buildDirectory.file("blocknote-editor-build.failed")
    outputs.upToDateWhen { !failureMarker.get().asFile.exists() }

    /**
     * 失败处理策略：**告警、不阻断 App 编译；失败必重试；绝不销毁已有产物**。
     *
     * 两个约束要同时满足：
     * 1) 未装 node / 依赖缺失的机器仍能编 App → isIgnoreExitValue = true，
     *    否则 npm 一失败整条构建链就断。
     * 2) 但吞掉失败后 Gradle 会认为本 task「成功」并缓存该状态，下次直接
     *    UP-TO-DATE 跳过——开发者修好源码后仍一直拿旧产物，更难排查。
     *
     * 解法：失败时落标记文件（配合上面的 upToDateWhen 强制下次重试），
     * 并保留既有 editor.html——删掉反而会让 App 运行时白屏，比用旧产物更糟。
     */
    isIgnoreExitValue = true

    doLast {
        val marker = failureMarker.get().asFile
        if (executionResult.get().exitValue != 0) {
            marker.parentFile?.mkdirs()
            marker.writeText("exit=${executionResult.get().exitValue}")
            logger.warn(
                "[blocknote] 编辑器产物构建失败（exit=${executionResult.get().exitValue}），" +
                    "App 将继续使用既有的 editor.html，下次构建会自动重试。\n" +
                    "  排查：cd blocknote-probe && npm install && npm run build:editor"
            )
        } else {
            marker.delete()
            logger.lifecycle("[blocknote] 编辑器产物已刷新 → $blocknoteEditorArtifact")
        }
    }
}

/**
 * 让所有 assets 合并任务（mergeDebugAssets / mergeReleaseAssets / …）依赖产物构建。
 *
 * ⚠️ 时序问题：AGP 的 merge*Assets task 由 variant API 注册，时机晚于脚本顶层、
 * 且不保证在 afterEvaluate 之前。用 `tasks.configureEach { }` 而非
 * `tasks.matching { }.configureEach { }`——前者对**此后注册的每个 task**都执行回调，
 * 天然不受注册时序影响；后者在调用时刻就绑定了当时的集合快照，会静默失配。
 *
 * 用 name 前缀/后缀判断而非类型，是因为 merge*Assets 是 AGP 内部实现类。
 */
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(buildBlockNoteEditor)
    }
}

/** Release signing is intentionally local-only. Keep the actual values in the
 * repository-root `keystore.properties` file (which is ignored by Git), for
 * example by copying `keystore.properties.example`.
 */
// 用 bufferedReader().use { load(it) } 而非 inputStream().use(::load)：
// Properties.load 同时有 load(InputStream) 与 load(Reader) 两个重载，原写法
// 让 use<T : Closeable?, R> 中 T 可空 + load 重载歧义叠加，Kotlin 2.3 在
// Gradle 9.6.1 脚本编译中无法推断 R，导致整条类型链断裂（util / getProperty
// 全部 unresolved）。bufferedReader() 返回 Reader，唯一匹配 load(Reader) 重载。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.bufferedReader().use { load(it) }
    }
}

val releaseSigningConfigured = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword"
).all { keystoreProperties.getProperty(it)?.isNotBlank() == true }

android {
    namespace = "com.corgimemo.app"
    compileSdk = 36

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.corgimemo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9.0 内置 Kotlin 模式：使用 kotlin { } 扩展替代旧版 kotlinOptions 块
    //  jvmToolchain(17) 让 Kotlin 编译与 Java 编译共用同一个 JVM 17 toolchain
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    /** composeOptions 块已移除：Kotlin 2.0+ 使用 kotlin-compose 插件自动管理 Compose Compiler 版本 */

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    /**
     * Hilt / Dagger 编译器选项
     *
     * useBindingGraphFix: 启用绑定图修复（Dagger 2.58 默认开启）。
     * 确保所有 @Provides 方法安装在正确的 Component 中，
     * 提前发现潜在的依赖注入错误，为升级到 Dagger 2.58+ 做好准备。
     */
    ksp {
        arg("dagger.useBindingGraphFix", "enabled")
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)  // ProcessLifecycleOwner 支持
    implementation(libs.androidx.paging.runtime)     // Paging 3 核心库
    implementation(libs.androidx.paging.compose)    // Paging 3 Compose 集成
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.compose.icons.lucide)       // Lucide 描边图标（编辑页工具栏字体/字号颜色按钮）

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    /** 加密 SharedPreferences（替代明文 DataStore，所有 key 自动 AES-256-GCM 加密）*/
    implementation(libs.androidx.security.crypto.ktx)

    implementation(libs.coil.compose)
    // Coil 3.x 网络图片支持（CorgiCompanion 通过 HTTPS URL 加载图片）
    implementation(libs.coil.network.okhttp)
    implementation(libs.google.accompanist.permissions)
    /**
     * 下拉刷新已从 accompanist-swiperefresh（已废弃）迁移到 Material3 PullToRefreshBox，
     * 移除 accompanist-swiperefresh 依赖。
     */
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.compiler)
    // Hilt 使用 kotlin-metadata-jvm 读取 Kotlin 元数据，Kotlin 2.3.20 产生 metadata 2.4.0，
    // 但 Hilt 内部 shaded 版本仅支持到 2.3.0。Dagger 2.57+ 已 unshade 该依赖，
    // 显式声明匹配 Kotlin 版本的 kotlin-metadata-jvm 即可解决兼容性问题。
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.20")

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.appcompat)

    /** 灵感图片详情页：读取 EXIF「拍摄时间」（androidx.exifinterface，v2026-09-10） */
    implementation(libs.androidx.exifinterface)

    /** 农历库：tyme4kt（基于 6tail/lunar 升级的 KMP 版本） */
    implementation("cn.6tail:tyme4kt:1.4.5")

    /** 拖拽排序库 Calvin-LL/Reorderable */
    implementation(libs.reorderable)

    /** 富文本编辑器库（源码 module 集成） */
    implementation(project(":richeditor-compose"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    /** Room Migration 单元测试：用纯 JVM SQLite 验证 migrate() 的 SQL 逻辑 */
    testImplementation(libs.sqlite.jdbc)
    /** Robolectric：在 JVM 上模拟 Android Framework，使 Bitmap 等 Android API 能在单元测试中使用 */
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    /** Room Migration 测试支持：提供 MigrationTestHelper 用于验证数据库升级脚本 */
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    /**
     * Kuikly 渲染器与核心库（方案 D：AAR 桥接）
     *
     * 版本号必须与独立工程 kuikly-shared 中使用的 Kuikly 版本严格一致（2.26.0-2.1.21），
     * 否则会出现 Kotlin 元数据 / API 不匹配问题。
     * 说明：AAR 通过 flatDir 引入不会传递依赖，因此 core 与 core-render-android
     * 必须在此显式声明。
     */
    implementation("com.tencent.kuikly-open:core:2.26.0-2.1.21")
    implementation("com.tencent.kuikly-open:core-render-android:2.26.0-2.1.21")
    /**
     * Kuikly shared 模块产出的 AAR（含 @Page("router") 页面与 KuiklyCoreEntry 注册入口）
     *
     * 用 files() 而非 flatDir 的 implementation(name=..., ext=...) 写法：
     * 后者在 Gradle 9.x 已不被支持，会报 No parameter with name 'name' found。
     * 路径相对于本模块目录（app/）。
     */
    implementation(files("../kuikly-shared/shared/build/outputs/aar/shared-release.aar"))
}
