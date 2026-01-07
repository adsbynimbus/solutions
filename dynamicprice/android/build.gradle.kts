import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.app)
}

val codeQL = providers.provider { extra.properties["codeQL"] }
val githubActions = providers.environmentVariable("GITHUB_ACTIONS")

kotlin {
    androidTarget {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvm.get())
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.bundles.androidx)
            implementation(libs.bundles.dynamicprice)
            implementation(libs.kotlin.coroutines)
        }
    }
}

androidComponents.beforeVariants {
    it.enable = it.name.contains("release", ignoreCase = true) || !githubActions.isPresent
}

android {
    compileSdk = libs.versions.android.sdk.get().toInt()

    defaultConfig {
        applicationId = "adsbynimbus.solutions.dynamicprice".also { namespace = it }
        minSdk = libs.versions.android.min.get().toInt()
        targetSdk = libs.versions.android.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appName"] = "Nimbus Dynamic Price"
        with(providers) {
            manifestPlaceholders["gamAppId"] = gradleProperty("adsbynimbus.solutions.admanagerAppId").get()
            buildConfigField("String", "API_KEY", "\"${gradleProperty("adsbynimbus.solutions.apiKey").get()}\"")
            buildConfigField("String", "PUBLISHER_KEY", "\"${gradleProperty("adsbynimbus.solutions.publisherKey").get()}\"")
            buildConfigField("String", "ADMANAGER_ADUNIT_ID", "\"${gradleProperty("adsbynimbus.solutions.admanagerAdUnitId").get()}\"")
            buildConfigField("String", "AMAZON_APP_KEY", "\"${gradleProperty("adsbynimbus.solutions.amazonAppId").get()}\"")
            buildConfigField("String", "AMAZON_BANNER_SLOT_ID", "\"${gradleProperty("adsbynimbus.solutions.amazonBannerSlotId").get()}\"")
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
