import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 签名配置：**签名材料不入库**（开源仓库绝不能带正式密钥）。
// 来源优先级：
//   1) keystore.properties（本地 ~/.doit-keystore/keystore.properties 或项目根，不入 git）
//   2) 环境变量 DOIT_KEYSTORE / DOIT_KEYSTORE_PASSWORD / DOIT_KEY_ALIAS / DOIT_KEY_PASSWORD
//   3) CI 的 GitHub Secrets 会以同名环境变量注入
// 都没有时回退 debug 签名（仅本地/CI 临时出包，正式 release 必须提供密钥）。
val keystoreProps = Properties().apply {
    // 项目根 or 用户目录的受保护位置（都不入 git）
    val candidates = listOf(
        rootProject.file("keystore.properties"),
        rootProject.file("${System.getProperty("user.home")}/.doit-keystore/keystore.properties"),
    )
    for (f in candidates) {
        if (f.exists()) {
            f.inputStream().use { load(it) }
            break
        }
    }
}
val env = System.getenv()
val releaseStorePath: String? =
    env["DOIT_KEYSTORE"]?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it).absolutePath }

// ---- 版本号：完全由 git tag 推导，不再手改 ----
// 打 v1.2.3 这个 tag，出来的就是 versionName=1.2.3、versionCode=10203；
// 没 tag（本地随便构建 / workflow_dispatch）时退回 1.0.0，保证一定能出包。
fun gitOutput(vararg args: String): String? = runCatching {
    // 必须走 providers.exec：直接 ProcessBuilder 会在 configuration cache 阶段被判定为非法外部进程
    val text = providers.exec {
        workingDir = rootProject.projectDir
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    text.ifEmpty { null }
}.getOrNull()

val semverTag = Regex("""v(\d+)\.(\d+)\.(\d+)$""")

// CI 上 GitHub 会把被推送的 tag 放进 GITHUB_REF_NAME；本地则取最近的一个 tag
val rawTag: String? = System.getenv("GITHUB_REF_NAME")?.takeIf { semverTag.matches(it) }
    ?: gitOutput("describe", "--tags", "--abbrev=0")?.takeIf { semverTag.matches(it) }

val tagNumbers = rawTag?.let { semverTag.find(it) }?.groupValues?.drop(1)?.map { it.toInt() }

val appVersionName = tagNumbers?.joinToString(".") ?: "1.0.0"
// versionCode 由 semver 推导：1.2.3 → 1*10000 + 2*100 + 3 = 10203，
// 随 tag 单调递增，既不用手工维护也不会和旧版本撞号。
val appVersionCode = tagNumbers?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch } ?: 10000
val apkBaseName = "doit"

logger.lifecycle("doit 版本：$appVersionName ($appVersionCode)${rawTag?.let { " 来自 tag $it" } ?: "（无 tag，使用默认值）"}")

android {
    namespace = "com.doit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.doit.app"
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
                // 口令只认 keystore.properties / 环境变量，绝不写死默认值
                storePassword = env["DOIT_KEYSTORE_PASSWORD"]
                    ?: keystoreProps.getProperty("storePassword")
                    ?: error("缺少签名口令：请在 keystore.properties 提供 storePassword 或设 DOIT_KEYSTORE_PASSWORD")
                keyAlias = env["DOIT_KEY_ALIAS"]
                    ?: keystoreProps.getProperty("keyAlias")
                    ?: error("缺少签名别名：请在 keystore.properties 提供 keyAlias 或设 DOIT_KEY_ALIAS")
                keyPassword = env["DOIT_KEY_PASSWORD"]
                    ?: keystoreProps.getProperty("keyPassword")
                    ?: error("缺少密钥口令：请在 keystore.properties 提供 keyPassword 或设 DOIT_KEY_PASSWORD")
            }
        } else {
            logger.warn("⚠ 未找到 release 签名密钥，正式包将使用 debug 证书签名（仅临时出包，不可升级安装）")
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
    implementation(libs.androidx.compose.material.icons.extended)
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
