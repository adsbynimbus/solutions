plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.configureEach {
            kotlinOptions.jvmTarget = libs.versions.android.jvm.get()
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.ads.amazon)
            implementation(libs.ads.google)
            implementation(libs.bundles.nimbus)
        }
    }
}

android {
    namespace = "adsbynimbus.solutions.bidding"
    compileSdk = libs.versions.android.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.get().toInt()
    }

    compileOptions.targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get())
}
