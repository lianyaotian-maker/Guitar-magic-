import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 在 GitHub Actions 中使用运行编号作为版本号，保证新版本可以直接覆盖安装
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.guitarmagic.tuner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.guitarmagic.tuner"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // 固定的调试签名（仓库内的 debug.keystore），这样每次云端编译出的 APK 签名一致，
        // 手机上可以直接覆盖升级，不需要先卸载
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    testImplementation("junit:junit:4.13.2")
}
