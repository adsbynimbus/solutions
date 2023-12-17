plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.app)
    alias(libs.plugins.compose)
}

/* Allows for overriding Android Jvm version and compile sdk using gradle.properties */
val androidJvmVersion = providers.gradleProperty("android.jvm").orElse(libs.versions.android.jvm)
val androidCompileSdk = providers.gradleProperty("android.sdk").orElse(libs.versions.android.sdk)

kotlin {
    androidTarget {
        compilations.configureEach {
            kotlinOptions.jvmTarget = androidJvmVersion.get()
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Compose"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation(libs.kotlin.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.compose.activity)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
        }
    }
}

android {
    defaultConfig {
        applicationId = "adsbynimbus.solutions.app".also { namespace = it }
        minSdk = libs.versions.android.min.get().toInt()
        targetSdk = androidCompileSdk.get().toInt().also { compileSdk = it }
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appName"] = "Nimbus"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    compileOptions.targetCompatibility = JavaVersion.toVersion(androidJvmVersion.get())

    packaging.resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    val main by sourceSets.getting {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }

    val release by buildTypes.getting {
        isMinifyEnabled = false
    }

    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

/* Fixes the Make Project menu option in Android Studio */
tasks.register("testClasses")
