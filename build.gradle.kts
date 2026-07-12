buildscript {
    dependencies {
        // KSP buildscript classpath 同步升级到 2.3.9，与 libs.versions.toml 中的 KSP 插件版本保持一致
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.9")
        // Hilt 升级到 2.59.2：与 AGP 9.0 内置 Kotlin 模式兼容（2.57.2 在 BaseExtension 上崩溃）
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.59.2")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    /** 以下插件在根项目声明 apply false，子模块 :richeditor-compose 通过 id() 不带版本应用
     *  原因：AGP 9.2.1 内置 Kotlin 且 application 插件包含 library 插件，
     *  这些插件已在 classpath（版本未知），子模块若带版本应用会触发兼容性检查失败 */
    alias(libs.plugins.kotlin.multiplatform) apply false
    /** AGP 9.0+ KMP 专用 Android Library 插件（替代 com.android.library） */
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}

// 全局强制 kotlin-metadata-jvm 版本：Kotlin 2.3.20 产生 metadata 2.4.0，
// Hilt/Dagger 内部 shaded 的旧版本仅支持到 2.3.0，强制解析到 2.3.20 确保兼容。
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.20")
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}