pluginManagement {
    repositories {
        // 国内镜像加速：与主工程 CorgiMemo 保持一致，避免 mavenCentral/google 直连超时
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        // Kuikly 官方库托管在腾讯源，必须保留
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

rootProject.name = "kuikly-shared"
include(":androidApp")
include(":shared")