import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val codeQL = providers.provider { extra.properties["codeQL"] }
val githubActions = providers.environmentVariable("GITHUB_ACTIONS")

android {
    compileSdk = libs.versions.android.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.adsbynimbus.android.omsdk".also { namespace = it }
        minSdk = libs.versions.android.min.get().toInt()
        targetSdk = libs.versions.android.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appName"] = "Nimbus OMSDK Validator"
        with(providers) {
            buildConfigField("String", "API_KEY", "\"${gradleProperty("adsbynimbus.solutions.apiKey").get()}\"")
            buildConfigField("String", "PUBLISHER_KEY", "\"${gradleProperty("adsbynimbus.solutions.publisherKey").get()}\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = !codeQL.isPresent
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                layout.projectDirectory.file("src/androidMain/proguard-rules.txt"),
            )
        }
    }

    compileOptions {
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
    implementation(libs.ads.nimbus)
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.androidx.compose)
    implementation(libs.kotlin.coroutines)
    debugImplementation(libs.compose.ui.tooling)
}
