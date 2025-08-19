@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("../node_modules/@react-native/gradle-plugin")
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

plugins { id("com.facebook.react.settings") }

//extensions.configure(com.facebook.react.ReactSettingsExtension){ ex -> ex.autolinkLibrariesFromCommand() }

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
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        configureEach {
            if (androidGradleOverride.isPresent) version("android", androidGradleOverride.get())
            if (androidJvmOverride.isPresent) version("android-jvm", androidJvmOverride.get())
        }
    }
}

rootProject.name = "reactnative"

include(":app")
includeBuild("../node_modules/@react-native/gradle-plugin")
