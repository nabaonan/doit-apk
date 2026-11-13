import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 本地签名配置：优先取 local.properties / 环境变量 / keystore/ 目录里的发布证书，
// 找不到时回退到 debug 证书，保证任何一台机器（含 CI）都能产出可安装的 APK。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStorePath: String? = System.getenv("QUANDIAN_KEYSTORE")
    ?: keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).absolutePath }
    ?: rootProject.file("keystore/quandian-release.jks").takeIf { it.exists() }?.absolutePath

// 版本号与产物名前缀：APK 统一叫 doit-<版本>-<构建类型>.apk
val appVersionCode = 8
val appVersionName = "1.7.0"
val apkBaseName = "doit"

android {
    namespace = "com.quandian.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quandian.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (releaseStorePath != null) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("QUANDIAN_KEYSTORE_PASSWORD")
                    ?: keystoreProps.getProperty("storePassword") ?: "quandian"
                keyAlias = System.getenv("QUANDIAN_KEY_ALIAS")
                    ?: keystoreProps.getProperty("keyAlias") ?: "quandian"
                keyPassword = System.getenv("QUANDIAN_KEY_PASSWORD")
                    ?: keystoreProps.getProperty("keyPassword") ?: "quandian"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // 真机调试时在桌面同时看到正式版与调试版
            resValue("string", "app_name", "doit · 调试")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// 产物重命名：AGP 默认输出 app-release.apk，这里统一成 doit-<版本>-<构建类型>.apk，
// 本地 dist/ 与 CI artifact 拿到的文件名一致，下载下来就知道是什么。
androidComponents {
    onVariants { variant ->
        val type = variant.buildType ?: "release"
        variant.outputs.forEach { output ->
            // outputFileName 还没进公开 API，只能落到 AGP 的实现类上
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("$apkBaseName-$appVersionName-$type.apk")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
