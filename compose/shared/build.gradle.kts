@file:OptIn(org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl::class)

import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

val codeQL = providers.provider { extra.properties["codeQL"] }

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
            sarifReport = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    swiftExport {
        moduleName = "Shared"
        flattenPackage = "adsbynimbus.solutions.compose"
        configure {
            // SWIFT_EXPORT_COROUTINES_SUPPORT_TURNED_ON does not exist in Kotlin 2.2.21 which
            // is still used by the CodeQL build until support for 2.3.0 is added
            settings.put("enableCoroutinesSupport", "true")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlin.coroutines)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
