buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 内置 Kotlin 支持；显式指定 KGP 版本，与 Compose 编译器插件保持一致
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
