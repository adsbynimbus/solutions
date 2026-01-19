import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
        }
    }

    iosArm64()
    iosSimulatorArm64()

    swiftExport {
        moduleName = "Shared"
        flattenPackage = "adsbynimbus.solutions.compose"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlin.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.androidx.compose.activity)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
