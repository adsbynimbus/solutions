import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val credentialPath = providers.environmentVariable("HOME").map {
    "$it/.config/gcloud/application_default_credentials.json"
}

kotlin {
    jvm {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvm.get())
                }
            }
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            /* App Name    */ args("")
            /* Network     */ args("")
            /* Credentials */ args(layout.buildDirectory.file(credentialPath).get().asFile)
            mainClass = "adsbynimbus.solutions.dynamicprice.util.AdManagerJvm"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.coroutines)
        }
        jvmMain.dependencies {
            implementation(libs.bundles.api.admanager)
            runtimeOnly(libs.slf4j)
        }
    }
}
