import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
}

val codeQL = providers.provider { extra.properties["codeQL"] }

val dokkaJavadocJar by tasks.registering(Jar::class) {
    description = "Creates a javadoc jar using Dokka"
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
    description = "Creates a HTML documentation jar using Dokka"
    archiveClassifier.set("html-doc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

kotlin {
    android {
        namespace = "$group.gamdirect.instream"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = 24
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.JVM_17
            }
        }

        aarMetadata {
            minCompileSdk = 36
            minAgpVersion = "8.5.0" // Min Required for Kotlin 2.0
        }

        lint {
            checkReleaseBuilds = !codeQL.isPresent
        }

        mavenPublication {
            artifact(dokkaJavadocJar)
            artifact(dokkaHtmlJar)
        }
    }

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }

    sourceSets {
        removeIf { it.name == "commonTest" } // Fixes Unused Kotlin Source Sets warning
        androidMain.dependencies {
            api(libs.ads.nimbus)
            api(libs.ads.google.ima)
            api(libs.androidx.media3)
        }
    }
}

dokka {
    moduleName = "Nimbus GAM Direct Instream"
    dokkaGeneratorIsolation = ClassLoaderIsolation()
    dokkaSourceSets {
        named("androidMain") {
            includes.from("Module.md")

            perPackageOption {
                matchingRegex = """.*\.internal.*"""
                suppress = true
            }

            sourceLink {
                localDirectory = layout.projectDirectory.dir("src/$name/kotlin")
                remoteLineSuffix = "#L"
                remoteUrl("https://github.com/adsbynimbus/solutions/tree/main/gam-direct/android/instream/src/$name/kotlin")
            }
        }
    }
}

publishing {
    // Rename root publication to gamdirect-instream and android publication to gamdirect-instream-android
    publications.withType<MavenPublication>().configureEach {
        artifactId = "gamdirect-instream" + if (name != "kotlinMultiplatform") "-$name" else ""
    }
    repositories {
        providers.environmentVariable("GITHUB_REPOSITORY").orNull?.let {
            maven("https://maven.pkg.github.com/$it") {
                name = "github"
                credentials(PasswordCredentials::class)
            }
        }
    }
}
