import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
}

val codeQL = providers.provider { extra.properties["codeQL"] }

val dokkaJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
    archiveClassifier.set("html-doc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

kotlin {
    androidLibrary {
        namespace = "$group.nextgen"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = 24

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.JVM_17
            }
        }

        aarMetadata {
            minCompileSdk = 35
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

    explicitApi()

    sourceSets {
        removeIf { it.name == "commonTest" } // Fixes Unused Kotlin Source Sets warning
        androidMain.dependencies {
            implementation(libs.ads.nimbus)
            implementation(libs.ads.google.nextgen)
        }
    }
}

dependencies.constraints {
    androidMainImplementation(libs.androidx.core) {
        version {
            require("[1.13.0,)")
            because("BundleCompat.getSerializable added in 1.13.0")
        }
    }
    androidMainImplementation(libs.okio) {
        version {
            require("[3.4.0,)")
            because("Addresses CVE-2023-3635 reported on Okio 3.2.0")
        }
    }
}

dokka {
    moduleName = "Dynamic Price Next Gen"
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
                remoteUrl("https://github.com/adsbynimbus/solutions/tree/main/dynamicprice/nextgen/sdk/src/$name/kotlin")
            }
        }
    }
}

publishing {
    // Rename root publication to nextgen and android publication to nextgen-android
    publications.withType<MavenPublication>().configureEach {
        artifactId = "nextgen" + if (name != "kotlinMultiplatform") "-$name" else ""
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
