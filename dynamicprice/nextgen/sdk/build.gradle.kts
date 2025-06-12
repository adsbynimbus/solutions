import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.adsbynimbus.dynamicprice.nextgen"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = 24

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.JVM_1_8
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ads.nimbus)
            implementation(libs.ads.google.nextgen)
        }
    }
}
