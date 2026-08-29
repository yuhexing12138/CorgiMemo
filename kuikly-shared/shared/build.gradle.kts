import com.tencent.kuikly.gradle.config.KuiklyConfig

/**
 * Kuikly shared 模块 —— 方案 D（AAR 桥接）专用配置
 *
 * 裁剪原则：只移除 Windows 环境下「必然失败」的部分，其他一律保留原模板。
 *
 * 已移除：iOS target（iosX64/iosArm64/iosSimulatorArm64）+ CocoaPods
 *  - 当前为 Windows，未安装 CocoaPods；且主工程只需要 Android 产物
 *  - 实测移除后 Kuikly 插件无异常（插件不依赖 iOS 任务）
 *
 * 必须保留：js(IR) target
 *  - Kuikly Gradle 插件（JSSplitProcessor / JSProcessor）在配置阶段会查找
 *    jsBrowserDevelopmentExecutableDistribution 等 JS 任务
 *  - 移除 js target + 未配置 js{} → java.io.IOException: 文件名、目录名或卷标语法不正确
 *  - 移除 js target + 配置了 js{} → Task 'jsBrowserDevelopmentExecutableDistribution' not found
 *  - 因此：js target 与 KuiklyConfig 的 js{} 必须成对保留
 *  - 仅「配置」不「编译」：本次只构建 Android 产物，不会触发 JS 编译
 */
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("maven-publish")
    id("com.tencent.kuikly-open.kuikly")
}

val KEY_PAGE_NAME = "pageName"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    // 见文件头说明：Kuikly 插件强依赖 js target，必须保留
    js(IR) {
        browser {
            webpackTask {
                outputFileName = "nativevue2.js"
            }
            commonWebpackConfig {
                output?.library = null
                devtool = "source-map"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyVersion()}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyVersion()}")
            }
        }
    }
}

group = "com.corgimemo.kuikly"
version = System.getenv("kuiklyBizVersion") ?: "1.0.0"

publishing {
    repositories {
        maven {
            credentials {
                username = System.getenv("mavenUserName") ?: ""
                password = System.getenv("mavenPassword") ?: ""
            }
            rootProject.properties["mavenUr?"]?.toString()?.let { url = uri(it) }
        }
    }
}

ksp {
    arg(KEY_PAGE_NAME, getPageName())
}

dependencies {
    compileOnly("com.tencent.kuikly-open:core-ksp:${Version.getKuiklyVersion()}") {
        add("kspAndroid", this)
        add("kspJs", this)
    }
}

android {
    namespace = "com.corgimemo.kuikly.shared"
    // compileSdk 用 35 而非主工程的 36：
    // AGP 8.10.1 自带的 aapt2 无法解析 android-36 的 android.jar，
    // 会报 RES_TABLE_TYPE_TYPE entry offsets overlap actual entry data。
    // 主工程（AGP 9.2.1）用的是更新版 aapt2，所以 36 在其上正常。
    // minSdk 26 / Java 17 与主工程保持一致。
    compileSdk = 36
    // 显式指定 build-tools：AGP 8.10.1 自带的 aapt2 无法解析本机 platform 的
    // android.jar（RES_TABLE_TYPE_TYPE entry offsets overlap actual entry data）。
    // 本机已安装到 build-tools 37.0.0，其 aapt2 支持新版 ARSC 格式。
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            assets.srcDirs("src/commonMain/assets")
        }
    }
}

// Kuikly 插件配置：必须与 js(IR) target 成对出现（见文件头说明）
configure<KuiklyConfig> {
    js {
        outputName("nativevue2")
    }
}

fun getPageName(): String {
    return (project.properties[KEY_PAGE_NAME] as? String) ?: ""
}

/**
 * 阻止 KSP 处理 commonMain metadata 源集
 *
 * 问题：Kuikly Gradle 插件会把 core-ksp 注入 kspCommonMainMetadata 配置
 * （在 dependencies 报告中表现为 kspCommonMainKotlinMetadataProcessorClasspath 含 core-ksp），
 * 于是 KSP 在 commonMain metadata 编译阶段就生成了 Android 平台专属的
 * KuiklyCoreEntry（package com.tencent.kuikly.core.android）。
 * 该文件引用的 IKuiklyCoreEntry / NativeBridgeDelegate 仅存在于 core-android 变体，
 * common 源集无法解析，导致 compileCommonMainKotlinMetadata 报 Unresolved reference。
 *
 * 处理：清空 kspCommonMainMetadata，使 KSP 仅在 androidMain（kspAndroid）生成。
 * 这样 Android 专属代码编译在 Android 源集，classpath 上才有 core-android。
 * 注意：本模块只产出 Android 产物，不需要 commonMain 的 KSP 产出。
 */
afterEvaluate {
    configurations.findByName("kspCommonMainMetadata")?.dependencies?.clear()
}

/**
 * 跳过 Android 资源校验
 *
 * 本模块不含任何 Android res 资源（只有 src/commonMain/assets 下的 assets），
 * 而 AGP 8.10.1 自带的 aapt2 无法解析本机较新的 platform android.jar，会报：
 *   RES_TABLE_TYPE_TYPE entry offsets overlap actual entry data
 * （主工程用的 AGP 9.2.1 自带更新的 aapt2，因此不受影响）
 * 由于没有 res 需要链接校验，跳过该任务不影响 AAR 产物（classes.jar + assets）。
 */
tasks.matching { it.name.startsWith("verify") && it.name.endsWith("Resources") }.configureEach {
    enabled = false
}
