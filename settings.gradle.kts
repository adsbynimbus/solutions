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
                includeGroupAndSubgroups("com.google.ads")
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
                includeGroup("com.adsbynimbus.openrtb")
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

gradle.beforeProject {
    buildscript.configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.fasterxml.jackson.core") {
                useVersion(if (requested.module.name == "jackson-annotations") "2.22" else "2.22.0")
                because("Fixes CWE-918 (SSRF)")
            }
        }
    }
}

rootProject.name = "nimbus-solutions"

include(":compose:android")
include(":compose:shared")
include(":dynamicprice:android")
include(":dynamicprice:nextgen")
include(":dynamicprice:nextgen:sdk")
include(":dynamicprice:util")
include(":gam-direct:android")
include(":gam-direct:android:instream")
include(":omsdk:android")
include(":sdk-extensions:android:admob")
include(":sdk-extensions:android:admob-nextgen")
