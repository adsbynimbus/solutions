import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.app)
    id("com.facebook.react")
}

react { autolinkLibrariesWithApp() }

android {
    compileSdk = libs.versions.android.sdk.get().toInt()
    namespace = "com.adsbynimbus.solutions.react"

    defaultConfig {
        applicationId = namespace
        minSdk = libs.versions.android.min.get().toInt()
        targetSdk = libs.versions.android.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appName"] = "Nimbus React Native App"
        with(providers) {
            buildConfigField("String", "API_KEY", "\"${gradleProperty("adsbynimbus.solutions.apiKey").get()}\"")
            buildConfigField("String", "PUBLISHER_KEY", "\"${gradleProperty("adsbynimbus.solutions.publisherKey").get()}\"")
        }
    }

    ndkVersion = "28.2.13676358"

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            isMinifyEnabled = true
        }
    }
}

kotlin {
    androidTarget {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvm.get())
            }
        }
    }
}

androidComponents.finalizeDsl {
    it.compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get())
    }
}

//noinspection UseTomlInstead
dependencies {
    // The version of react-native is set by the React Native Gradle Plugin
    implementation("com.facebook.react:react-android")
    implementation("com.facebook.react:hermes-android")
}
