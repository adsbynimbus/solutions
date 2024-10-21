import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

val androidJvmVersion = providers.gradleProperty("android.jvm").orElse(libs.versions.android.jvm)

val swiftPackageSimulator = tasks.register<SwiftPackageBuild>("swiftPackageSimulator")
val swiftPackageDevice = tasks.register<SwiftPackageBuild>("swiftPackageDevice") {
    mustRunAfter(swiftPackageSimulator)
}

tasks.withType<SwiftPackageBuild>().configureEach {
    sources = objects.fileCollection().from(
        layout.projectDirectory.file("Package.swift"),
        layout.projectDirectory.dir("Sources"),
    )
    // We can use the task name because targetName checks for "Simulator"
    target = if (name.contains("Simulator")) "iphonesimulator" else "iphoneos"
    scheme = "NimbusKMM"
    configuration = providers.environmentVariable("CONFIGURATION")
    frameworks = layout.buildDirectory.dir(providers.environmentVariable("SYMROOT").orElse(target)).flatMap {
        it.dir(buildConfig.zip(target) { config, type -> "$config-$type" })
    }.also { directory -> localState.register(directory.map { it.files("*.tmp") }) }
    moduleMaps = layout.buildDirectory.dir(providers.environmentVariable("OBJROOT").orElse(target))
        .zip(target) { intermediates, os -> intermediates.dir("GeneratedModuleMaps-$os") }
}

kotlin {
    androidTarget {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = androidJvmVersion.map { JvmTarget.fromTarget(it) }
            }
        }
    }

    val iosTargets = objects.namedDomainObjectSet(KotlinNativeTarget::class).apply {
        // Add the default list of iOS targets
        addAll(listOf(iosArm64(), iosSimulatorArm64()))

        // Optionally add x64 support if kotlin.mpp.x64 is present in gradle.properties
        if (providers.gradleProperty("kotlin.mpp.x64").orNull.toBoolean()) add(iosX64())
    }
    iosTargets.configureEach {
        val swiftPackageTask = if (name.contains("X64") || name.contains("Simulator")) swiftPackageSimulator else swiftPackageDevice

        val main by compilations.getting {
            cinterops.create("NimbusKMM") {
                header(swiftPackageTask.flatMap { it.moduleMaps.file("NimbusKMM-Swift.h") })
            }
            cinterops.configureEach {
                swiftPackageTask.flatMap { it.frameworks }.get().let {
                    compilerOpts("-F$it", "-F$it/PackageFrameworks", "-I$it")
                }
            }
        }
        binaries.configureEach {
            swiftPackageTask.flatMap { it.frameworks }.get().let {
                linkerOpts("-F$it", "-F$it/PackageFrameworks", "-I$it")
            }
        }
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.nimbus)
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

@CacheableTask
abstract class SwiftPackageBuild : DefaultTask() {
    init {
        group = "interop"
        description = "Unpacks iOS Dependencies using Swift Package Manager"
    }

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input @get:Optional
    abstract val configuration: Property<String>

    @get:Input abstract val scheme: Property<String>
    @get:Input abstract val target: Property<String>
    @get:Inject abstract val ex: ExecOperations
    @get:OutputDirectory abstract val frameworks: DirectoryProperty
    @get:OutputDirectory abstract val moduleMaps: DirectoryProperty

    @get:Internal val buildConfig: Provider<String>
        get() = configuration.orElse("Debug")

    @get:Internal val platform: Provider<String>
        get() = target.map { if (it == "iphoneos") "iOS" else "iOS Simulator" }

    @TaskAction
    fun action() {
        if (!configuration.isPresent) {
            ex.exec {
                executable = "xcodebuild"
                args("-workspace", ".", "-scheme", scheme.get())
                args("-destination", "generic/platform=${platform.get()}")
                args("-clonedSourcePackagesDirPath", ".swiftpm")
                args("-configuration", buildConfig.get())
                args("OBJROOT=${moduleMaps.get().dir("..")}")
                args("SYMROOT=${frameworks.get().dir("..")}")
            }
        }
    }
}
