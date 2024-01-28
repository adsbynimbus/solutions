import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

val androidJvmVersion = providers.gradleProperty("android.jvm").orElse(libs.versions.android.jvm)

/* Global SPM cache used to speed up builds */
val spmCache = rootProject.layout.projectDirectory.dir(".gradle/spm")

/* Build Configuration from XCode */
val configuration = providers.environmentVariable("CONFIGURATION").orElse("Release")

/* Converts the targetName to the xcodebuild directory */
val KotlinNativeTarget.targetType get() =
    if (name.contains("Simulator")) "iphonesimulator" else "iphoneos"

/* SPM must be unpacked into two folders since it will clear the unused target framework */
val KotlinNativeTarget.frameworkPath get() = configuration.flatMap {
    layout.buildDirectory.dir("$targetType/Build/Products/$it-$targetType")
}

kotlin {
    androidTarget {
        compilations.configureEach {
            kotlinOptions.jvmTarget = androidJvmVersion.get()
        }
    }

    configure(listOf(iosArm64(), iosSimulatorArm64())) {
        val main by compilations.getting {
            cinterops.create("NimbusKMM") {
                header(layout.buildDirectory.file("$targetType/Build/Intermediates.noindex/GeneratedModuleMaps-$targetType/NimbusKMM-Swift.h"))
            }
            cinterops.configureEach {
                frameworkPath.get().asFile.path.let {
                    compilerOpts("-F$it", "-F$it/PackageFrameworks")
                }
            }
        }
        binaries.configureEach {
            frameworkPath.get().asFile.path.let {
                linkerOpts("-F$it", "-F$it/PackageFrameworks")
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            api(libs.nimbus)
        }
    }
}

android {
    namespace = "adsbynimbus.solutions.kmm"
    compileSdk = libs.versions.android.sdk.get().toInt()

    compileOptions.targetCompatibility = JavaVersion.toVersion(androidJvmVersion.get())

    defaultConfig {
        minSdk = libs.versions.android.min.get().toInt()
    }
}

abstract class SwiftPackageBuild : DefaultTask() {
    init {
        group = "interop"
        description = "Unpacks iOS Dependencies using Swift Package Manager"
    }

    @get:Inject abstract val ex: ExecOperations
    @get:Input abstract val buildType: Property<String>
    @get:Input abstract val platform: Property<String>
    @get:Input abstract val scheme: Property<String>
    @get:OutputDirectory abstract val cache: DirectoryProperty
    @get:OutputDirectory abstract val destination: DirectoryProperty

    @TaskAction
    fun action() {
        ex.exec {
            executable = "xcodebuild"
            args(
                "-workspace", ".", "-scheme", scheme.get(),
                "-destination", "generic/platform=${platform.get()}",
                "-derivedDataPath", destination.file("../../..").get().asFile.path,
                "-clonedSourcePackagesDirPath", cache.asFile.get().path,
                "-configuration", buildType.get(),
            )
        }
    }
}

val swiftSimulatorDependencies by tasks.registering(SwiftPackageBuild::class) {
    inputs.file(layout.projectDirectory.file("Package.swift"))
    buildType = configuration
    cache = spmCache
    destination = kotlin.iosSimulatorArm64().frameworkPath
    scheme = "NimbusKMM"
    platform = "iOS Simulator"
}

val swiftDeviceDependencies by tasks.registering(SwiftPackageBuild::class) {
    inputs.file(layout.projectDirectory.file("Package.swift"))
    buildType = configuration
    cache = spmCache
    destination = kotlin.iosArm64().frameworkPath
    scheme = "NimbusKMM"
    platform = "iOS"
}

tasks.withType<CInteropProcess>().configureEach {
    when {
        name.contains("Simulator") -> inputs.dir(swiftSimulatorDependencies.flatMap { it.destination })
        name.contains("Arm64") -> inputs.dir(swiftDeviceDependencies.flatMap { it.destination })
    }
}

/* Fixes the Make Project menu option in Android Studio */
tasks.register("testClasses")
