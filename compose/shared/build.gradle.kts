import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

val codeQL = providers.provider { extra.properties["codeQL"] }
val githubActions = providers.environmentVariable("GITHUB_ACTIONS")

kotlin {
    androidLibrary {
        namespace = "adsbynimbus.solutions.compose"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = libs.versions.android.min.get().toInt()
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvm.get())
            }
        }

        lint {
            checkReleaseBuilds = !codeQL.isPresent
        }
    }

    val iosTargets = objects.namedDomainObjectSet(KotlinNativeTarget::class).apply {
        add(iosArm64())
        add(iosSimulatorArm64())

        // Optionally add x64 support if kotlin.mpp.x64 is present in gradle.properties
        if (providers.gradleProperty("kotlin.mpp.x64").orNull.toBoolean()) add(iosX64())
    }

    iosTargets.configureEach {
        binaries.framework(
            buildList {
                if (!githubActions.isPresent) add(DEBUG)
                add(RELEASE)
            })
        {
            binaryOption("bundleId", "adsbynimbus.solutions.compose.app")
            binaryOption("bundleShortVersionString", "1.0")
            binaryOption("bundleVersion", "1.0")
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlin.coroutines)
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.10.0")
        }
        androidMain.dependencies {
            implementation(libs.compose.activity)
            implementation(libs.compose.ui)
        }
    }
}

dependencies {
    androidRuntimeClasspath("org.jetbrains.compose.ui:ui-tooling:1.10.0")
}
