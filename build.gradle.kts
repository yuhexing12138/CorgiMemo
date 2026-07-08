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
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}