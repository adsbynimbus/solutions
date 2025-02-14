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

val isFleetIDE = providers.systemProperty("idea.vendor.name").filter { it == "JetBrains" }
val androidGradleOverride = isFleetIDE.map { "8.7.2" }
    .orElse(providers.gradleProperty("android.gradle"))
val androidJvmOverride = providers.gradleProperty("android.jvm")

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
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
include(":dynamic-price:android")
include(":omsdk:android")
