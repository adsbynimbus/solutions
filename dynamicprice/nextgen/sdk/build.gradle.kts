import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
}

kotlin {
    androidLibrary {
        namespace = "com.adsbynimbus.dynamicprice.nextgen"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = 24

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.JVM_1_8
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ads.nimbus)
            implementation(libs.ads.google.nextgen)
        }
    }
}

dokka {
    moduleName = "Dynamic Price NextGen"
    dokkaGeneratorIsolation = ClassLoaderIsolation()
    dokkaSourceSets {
        named("androidMain") {
            perPackageOption {
                matchingRegex = """.*\.internal.*"""
                suppress = true
            }

            sourceLink {
                localDirectory = layout.projectDirectory.dir("src/$name/kotlin")
                remoteLineSuffix = "#L"
                remoteUrl("https://github.com/adsbynimbus/solutions/tree/main/dynamicprice/nextgen/sdk/src/$name/kotlin")
            }
        }
    }
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
    archiveClassifier.set("html-doc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}
