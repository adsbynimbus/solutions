import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

val swiftPackageSimulator = tasks.register<SwiftPackageBuild>("swiftPackageSimulator")
val swiftPackageDevice = tasks.register<SwiftPackageBuild>("swiftPackageDevice") {
    mustRunAfter(swiftPackageSimulator)
}

kotlin {

    androidLibrary {
        namespace = "adsbynimbus.solutions.kmm"
        minSdk = libs.versions.android.min.get().toInt()
        compileSdk = libs.versions.android.sdk.get().toInt()

        compilations.configureEach {
            compileTaskProvider.configure {
                (compilerOptions as KotlinJvmCompilerOptions).jvmTarget = JvmTarget.fromTarget(libs.versions.android.jvm.get())
            }
        }

        // Enables Unit Tests
        withHostTestBuilder { }
    }

    val iosTargets = objects.namedDomainObjectSet(KotlinNativeTarget::class).apply {
        add(iosArm64())
        add(iosSimulatorArm64())

        // Optionally add x64 support if kotlin.mpp.x64 is present in gradle.properties
        if (providers.gradleProperty("kotlin.mpp.x64").orNull.toBoolean()) add(iosX64())
    }

    iosTargets.configureEach {
        val swiftPackageTask = when (this) {
            is KotlinNativeTargetWithSimulatorTests -> swiftPackageSimulator
            else -> swiftPackageDevice
        }
        swiftPackageTask.configure { scheme = "NimbusKMM" }
        compilations.named("main") {
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
            implementation(libs.ads.nimbus)
        }
    }
}

@CacheableTask
abstract class SwiftPackageBuild : DefaultTask() {
    init {
        group = "interop"
        description = "Unpacks iOS Dependencies using Swift Package Manager"
    }

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val packageFile: RegularFileProperty

    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourcesDir: DirectoryProperty

    @get:Input abstract val scheme: Property<String>
    @get:Input abstract val target: Property<String>
    @get:Inject abstract val ex: ExecOperations
    @get:Inject abstract val providers: ProviderFactory
    @get:Inject abstract val layout: ProjectLayout

    @get:Internal val xcodeConfiguration: Provider<String> = providers.environmentVariable("CONFIGURATION")
    @get:Internal val configuration: Provider<String> = xcodeConfiguration.orElse("Release")
    @get:Internal val platform: Provider<String> =
        target.map { if (it == "iphoneos") "iOS" else "iOS Simulator" }
    @get:Internal val symRoot: Provider<Directory> =
        layout.buildDirectory.dir(providers.environmentVariable("SYMROOT").orElse(target))

    @get:Internal val objRoot: Provider<Directory> =
        layout.buildDirectory.dir(providers.environmentVariable("OBJROOT").orElse(target))

    @get:OutputDirectory abstract val frameworks: DirectoryProperty
    @get:OutputDirectory abstract val moduleMaps: DirectoryProperty

    init {
        // We can use the task name because targetName checks for "Simulator"
        target.convention(if (name.contains("Simulator")) "iphonesimulator" else "iphoneos")
        packageFile.convention(layout.projectDirectory.file("Package.swift"))
        sourcesDir.convention(layout.projectDirectory.dir("Sources"))
        frameworks.convention(symRoot.flatMap { it.dir(configuration.zip(target) { config, type -> "$config-$type" }) })
        moduleMaps.convention(objRoot.flatMap { it.dir(target.map { "GeneratedModuleMaps-$it" }) })
    }

    @TaskAction
    fun action() = ex.takeUnless { xcodeConfiguration.isPresent }?.exec {
        executable = "xcodebuild"
        args("-workspace", packageFile.asFile.get().parent, "-scheme", scheme.get())
        args("-destination", "generic/platform=${platform.get()}")
        args("-configuration", configuration.get())
        args("OBJROOT=${objRoot.get()}")
        args("SYMROOT=${symRoot.get()}")
    }
}
