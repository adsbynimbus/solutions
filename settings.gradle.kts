@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://adsbynimbus-public.s3.amazonaws.com/android/sdks") {
            credentials { username = "*" }
            content { includeGroupByRegex(".*\\.adsbynimbus.*") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    /* Allow for overriding Android Gradle Plugin for Studio Previews */
    if (providers.systemProperty("idea.vendor.name").orNull != "JetBrains") {
        providers.gradleProperty("android.gradle").let { agp ->
            if (agp.isPresent) versionCatalogs.configureEach { version("android", agp.get()) }
        }
    }
}

rootProject.name = "nimbus-solutions"

include(":app:compose")
include(":nimbus-kmm")
