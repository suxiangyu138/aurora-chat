import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 签名密钥配置来自 local.properties(gitignore,不入库)
val keystoreProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.aichat.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aichat.client"
        minSdk = 30          // 最低 Android 11,兼容绝大多数 iQOO 机型
        targetSdk = 36
        versionCode = 22
        versionName = "2.0.1"
    }

    signingConfigs {
        create("release") {
            val path = keystoreProps.getProperty("keystore.path")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = keystoreProps.getProperty("keystore.password")
                keyAlias = keystoreProps.getProperty("keystore.alias")
                keyPassword = keystoreProps.getProperty("keystore.password")
            }
        }
    }

    buildTypes {
        debug {
            // 本机配置了发行密钥时,debug 也用同一密钥签名:
            // 这样测试包可以直接覆盖已发行的正式版,不必每次卸载重装
            if (!keystoreProps.getProperty("keystore.path").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            // 发行包开裁剪:R8 去未用代码 + 资源裁剪,实测可从 60MB+ 降到个位数
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 本机配置了密钥则签名发布包
            if (!keystoreProps.getProperty("keystore.path").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // release 前不做 lint 阻塞,缩短发行构建时间(日常 CI 单独跑 lint)
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX 基础(2025 年中稳定版,与 AGP 8.13/compileSdk 36 匹配)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.activity:activity-compose:1.11.0")

    // Compose(版本由 BOM 统一管理)
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 图标库已冻结,固定版本(不在 BOM 中)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 导航
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // 网络:OkHttp(通用请求 + SSE 流式)+ Retrofit(预留)+ Gson(JSON 解析)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // 本地存储:Room(会话/消息)+ DataStore(模型配置)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // PDF 文本抽取(纯本地,不依赖厂商文件接口)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
