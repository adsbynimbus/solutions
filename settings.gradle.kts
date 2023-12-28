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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        /* Includes Nimbus Repository */
        maven("https://adsbynimbus-public.s3.amazonaws.com/android/sdks") {
            credentials { username = "*" }
            content { includeGroupByRegex(".*\\.adsbynimbus.*") }
        }
    }
    /* JetBrains Fleet IDE does not yet support AGP 8.2.0 */
    if (providers.systemProperty("idea.vendor.name").orNull == "JetBrains") {
        versionCatalogs.configureEach {
            version("android", "8.1.4")
        }
    }
}

rootProject.name = "solutions"

include(":app:compose")
include(":bidding")
