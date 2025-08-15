import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
        namespace = "$group.admob"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = 21
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
            implementation(libs.ads.nimbus.admob)
        }
    }
}

dokka {
    moduleName = "AdMob Solutions"
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
                remoteUrl("https://github.com/adsbynimbus/solutions/tree/main/sdk-extensions/android/admob/src/$name/kotlin")
            }
        }
    }
}

publishing {
    // Rename root publication to extension-admob and android publication to extension-admob-android
    publications.withType<MavenPublication>().configureEach {
        artifactId = "extension-admob" + if (name != "kotlinMultiplatform") "-$name" else ""
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
