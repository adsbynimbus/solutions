@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

val openrtbCredentials = providers.gradleProperty("openrtbUsername")

// Allows Android Gradle Plugin override if build is started from Android Studio or CI
val androidGradleOverride = providers.gradleProperty("android.gradle").filter {
    providers.systemProperty("idea.vendor.name").orNull != "JetBrains"
}
val androidJvmOverride = providers.gradleProperty("android.jvm")
val kotlinOverride = providers.gradleProperty("kotlin.gradle")

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google.android")
                includeGroupAndSubgroups("com.google.net.cronet")
                includeGroupAndSubgroups("org.chromium.net")
            }
        }
        exclusiveContent {
            forRepository {
                maven("https://adsbynimbus-public.s3.amazonaws.com/android/sdks")
            }
            filter {
                includeGroupAndSubgroups("com.adsbynimbus.android")
                includeGroup("com.iab.omid.library.adsbynimbus")
                if (!openrtbCredentials.isPresent) {
                    includeGroup("com.adsbynimbus.openrtb")
                }
            }
        }
        // If openrtb credentials are present, prefer GitHub Packages because it is free
        if (openrtbCredentials.isPresent) {
            maven("https://maven.pkg.github.com/adsbynimbus/nimbus-openrtb") {
                name = "openrtb"
                credentials(PasswordCredentials::class)
                content {
                    includeGroup("com.adsbynimbus.openrtb")
                }
            }
        }
        mavenCentral()
    }
    // Allows for overriding Android Tooling using gradle.properties
    versionCatalogs.configureEach {
        if (androidGradleOverride.isPresent) version("android", androidGradleOverride.get())
        if (androidJvmOverride.isPresent) version("android-jvm", androidJvmOverride.get())
        if (kotlinOverride.isPresent) version("kotlin", kotlinOverride.get())
    }
}

rootProject.name = "nimbus-solutions"

include(":compose:app")
include(":dynamicprice:android")
include(":dynamicprice:nextgen")
include(":dynamicprice:nextgen:sdk")
include(":dynamicprice:util")
include(":omsdk:android")
include(":sdk-extensions:android:admob")
