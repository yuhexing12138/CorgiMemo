object Version {

    // 脚手架模板默认锁定 2.22.0；升级到 Maven 仓库当前最新版 2.26.0。
    // 原因：2.22.0 在 KSP 2.x 下会把 Android 平台专属的 KuiklyCoreEntry
    // （package com.tencent.kuikly.core.android）生成到 commonMain metadata 源集，
    // 而它引用的 IKuiklyCoreEntry 只存在于 core-android 变体，
    // 导致 compileCommonMainKotlinMetadata 报 Unresolved reference。
    private const val KUIKLY_VERSION = "2.26.0"
    private const val KOTLIN_VERSION = "2.1.21"
    private const val KOTLIN_OHOS_VERSION = "2.0.21-ohos"

    /**
     * 获取 Kuikly 版本号，版本号规则：${shortVersion}-${kotlinVersion}
     * 适用于 core、core-ksp、core-annotation、core-render-android
     */
    fun getKuiklyVersion(): String {
        return "$KUIKLY_VERSION-$KOTLIN_VERSION"
    }

    /**
     * 获取 Kuikly Ohos版本号
     */
    fun getKuiklyOhosVersion(): String {
        return "$KUIKLY_VERSION-$KOTLIN_OHOS_VERSION"
    }
}

object BuildPlugin {
    val kuikly by lazy {
        "com.tencent.kuikly-open:core-gradle-plugin:${Version.getKuiklyVersion()}"
    }
}