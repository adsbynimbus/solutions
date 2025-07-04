import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
}

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
                // Casting works around a type issue with AGP 8.10.0 that is fixed in 8.12.0
                (compilerOptions as KotlinJvmCompilerOptions).jvmTarget = JvmTarget.JVM_1_8
            }
        }

        aarMetadata {
            minCompileSdk = 35
            minAgpVersion = "8.2.0" // Copied from NextGen metadata
        }

        mavenPublication {
            artifact(dokkaJavadocJar)
            artifact(dokkaHtmlJar)
        }
    }

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_1_9
        languageVersion = KotlinVersion.KOTLIN_1_9
    }

    explicitApi()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ads.nimbus)
            implementation(libs.ads.google.nextgen)
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
