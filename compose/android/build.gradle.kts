import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val codeQL = providers.provider { extra.properties["codeQL"] }
val githubActions = providers.environmentVariable("GITHUB_ACTIONS")

androidComponents.beforeVariants {
    it.enable = it.name.contains("release", ignoreCase = true) || !githubActions.isPresent
}

android {
    compileSdk = libs.versions.android.sdk.get().toInt()

    defaultConfig {
        applicationId = "adsbynimbus.solutions.compose.app".also { namespace = it }
        minSdk = libs.versions.android.min.get().toInt()
        targetSdk = libs.versions.android.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appName"] = "Nimbus Compose"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = !codeQL.isPresent
        }
    }

    compileOptions{
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get())
    }

    lint {
        checkReleaseBuilds = !codeQL.isPresent
    }

    packaging.resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents.beforeVariants {
    it.enable = it.name.contains("release", ignoreCase = true) || !githubActions.isPresent
}

kotlin.target.compilations.configureEach {
    compileTaskProvider.configure {
        compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvm.get())
    }
}

dependencies {
    implementation(projects.compose.shared)
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.androidx.compose)
}
