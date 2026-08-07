@file:Suppress("UnstableApiUsage")

enableFeaturePreview("ENHANCED_GRAPH_ORDERING")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
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
val codeQL = providers.environmentVariablesPrefixedBy("CODEQL").map { it.any() }

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
                includeGroupAndSubgroups("com.adsbynimbus")
                includeGroup("com.iab.omid.library.adsbynimbus")
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
                useVersion(if (requested.module.name == "jackson-annotations") "2.22" else "2.22.1")
                because("Fixes CWE-918 (SSRF)")
            }
        }
    }
    if (codeQL.get()) {
        tasks.withType<JavaCompile>().configureEach {
            outputs.upToDateWhen { false }
        }
        tasks.withType<GroovyCompile>().configureEach {
            outputs.upToDateWhen { false }
        }
        tasks.withType<ScalaCompile>().configureEach {
            outputs.upToDateWhen { false }
        }
        // All kotlin compilation tasks including compileAndroidMain from
        // com.android.kotlin.multiplatform.library
        with(Regex("compile.*[Android|Kotlin]")) {
            tasks.named { it.contains(this) }.configureEach {
                outputs.upToDateWhen { false }
            }
        }
    }
}

rootProject.name = "nimbus-solutions"

include(":compose:android")
include(":compose:shared")
include(":dynamicprice:android")
include(":dynamicprice:nextgen")
include(":dynamicprice:util")
include(":gam-direct:android")
include(":gam-direct:android:instream")
include(":omsdk:android")
include(":sdk-extensions:android:admob")
include(":sdk-extensions:android:admob-nextgen")
