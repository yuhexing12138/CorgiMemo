plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.corgimemo.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("../corgimemo-key.jks")
            storePassword = "yhx31415926@"
            keyAlias = "corgimemo"
            keyPassword = "yhx31415926@"
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
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

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
    implementation(libs.google.accompanist.permissions)
    implementation("com.google.accompanist:accompanist-swiperefresh:0.34.0")
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}