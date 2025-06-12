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

// Allows Android Gradle Plugin override if build is started from Android Studio or CI
val androidGradleOverride = providers.gradleProperty("android.gradle").filter {
    providers.systemProperty("idea.vendor.name").orNull != "JetBrains"
}
val androidJvmOverride = providers.gradleProperty("android.jvm")

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("org.chromium")
            }
        }
        maven("https://adsbynimbus-public.s3.amazonaws.com/android/sdks") {
            content { includeGroupByRegex(".*\\.adsbynimbus.*") }
        }
        mavenCentral()
    }
    // Allows for overriding Android Tooling using gradle.properties
    versionCatalogs.configureEach {
        if (androidGradleOverride.isPresent) version("android", androidGradleOverride.get())
        if (androidJvmOverride.isPresent) version("android-jvm", androidJvmOverride.get())
    }
}

rootProject.name = "nimbus-solutions"

include(":compose:app")
include(":dynamicprice:android")
include(":dynamicprice:nextgen")
include(":dynamicprice:nextgen:sdk")
include(":omsdk:android")
