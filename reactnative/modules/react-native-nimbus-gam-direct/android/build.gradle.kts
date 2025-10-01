import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("com.facebook.react")
}

kotlin {
    androidLibrary {
        namespace = "com.adsbynimbus.solutions.react.direct"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = libs.versions.android.min.get().toInt()

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.JVM_1_8
            }
        }

        aarMetadata {
            minCompileSdk = 35
            minAgpVersion = "8.2.0"
        }
    }

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_1_9
        languageVersion = KotlinVersion.KOTLIN_1_9
    }

    explicitApi()

    sourceSets {
        androidMain {
            kotlin.srcDirs("src/androidMain", "generated/java", "generated/jni")
            //noinspection UseTomlInstead
            dependencies {
                // The version of react-native is set by the React Native Gradle Plugin
                implementation("com.facebook.react:react-android")
                implementation("com.facebook.react:hermes-android")
            }
        }
    }
}
