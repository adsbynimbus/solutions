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
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                // The latest api-client binaries are not in the Google repo; recheck this later
                excludeGroupAndSubgroups("com.google.api-client")
                includeGroupAndSubgroups("org.chromium")
            }
        }
        // If openrtb credentials are present, prefer GitHub Packages because it is free
        if (providers.gradleProperty("openrtbUsername").isPresent) {
            maven("https://maven.pkg.github.com/adsbynimbus/nimbus-openrtb") {
                name = "openrtb"
                credentials(PasswordCredentials::class)
                content {
                    includeGroup("com.adsbynimbus.openrtb")
                }
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

layout.rootDirectory.dir("../nimbus-android/library").asFile.takeIf { it.exists() }?.let {
    includeBuild(it) {
        dependencySubstitution {
            substitute(module("com.adsbynimbus.android:nimbus-core")).using(project(":core"))
            substitute(module("com.adsbynimbus.android:nimbus-render")).using(project(":render"))
            substitute(module("com.adsbynimbus.android:nimbus-request")).using(project(":request"))
            substitute(module("com.adsbynimbus.android:nimbus-static")).using(project(":static"))
            substitute(module("com.adsbynimbus.android:nimbus-video")).using(project(":video"))
            substitute(module("com.adsbynimbus.android:nimbus")).using(project(":all"))
        }
    }
}
