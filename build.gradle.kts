buildscript {
    dependencies {
        // KSP buildscript classpath 同步升级到 2.3.9，与 libs.versions.toml 中的 KSP 插件版本保持一致
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.9")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.57.2")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}