import org.jetbrains.compose.desktop.application.dsl.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
            args(layout.buildDirectory.file(credentialPath).get().asFile)
            mainClass = "adsbynimbus.solutions.dynamicprice.util.AdManagerJvm"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation(compose.components.resources)
            implementation(libs.kotlin.coroutines)
            implementation(libs.bundles.androidx.room)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.bundles.api.admanager)
            runtimeOnly(libs.slf4j)
        }
    }
}

dependencies {
    add("kspJvm", libs.androidx.room.compiler)
}

compose.desktop {
    application {
        mainClass = "adsbynimbus.solutions.dynamicprice.util.LineItemApp_desktopKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DynamicPrice-Util"
            packageVersion = "1.0.0"
        }
    }
}

java {
    targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get())
}

room {
    schemaDirectory(layout.projectDirectory.dir("schemas"))
}
