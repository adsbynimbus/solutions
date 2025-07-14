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
            args(providers.gradleProperty("dynamicprice.util.appname").getOrElse(""))
            args(providers.gradleProperty("dynamicprice.util.networkcode").getOrElse(""))
            args(providers.gradleProperty("dynamicprice.util.orderid").getOrElse(""))
            args(layout.buildDirectory.file(credentialPath).get().asFile)
            mainClass = "adsbynimbus.solutions.dynamicprice.util.update.Reconfigure"
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
