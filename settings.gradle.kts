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
val androidGradleOverride = isFleetIDE.map { "8.7.0-alpha07" }
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
        mavenCentral()
    }
    // Allows for overriding Android Tooling using gradle.properties
    versionCatalogs.configureEach {
        if (androidGradleOverride.isPresent) version("android", androidGradleOverride.get())
        if (androidJvmOverride.isPresent) version("android-jvm", androidJvmOverride.get())
    }
}

rootProject.name = "nimbus-solutions"

include(":app:compose")
