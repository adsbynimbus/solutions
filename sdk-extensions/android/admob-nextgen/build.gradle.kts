import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
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
    android {
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

        withHostTest { }
    }

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }

    explicitApi()

    sourceSets {
        androidMain.dependencies {
            api(libs.ads.nimbus)
            api(libs.ads.google.nextgen)
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.bundles.test.common)
                implementation(libs.kotest.runner)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Revisit this when updating mockk where this warning originates from
    if (JavaVersion.current() >= JavaVersion.VERSION_24) {
        jvmArgs("--sun-misc-unsafe-memory-access=allow")
    }
}

dokka {
    moduleName = "AdMob NextGen"
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
                remoteUrl("https://github.com/adsbynimbus/solutions/tree/main/sdk-extensions/android/admob-nextgen/src/$name/kotlin")
            }
        }
    }
}

publishing {
    // Rename root publication to extension-admob and android publication to extension-admob-android
    publications.withType<MavenPublication>().configureEach {
        artifactId = "extension-admob-nextgen" + if (name != "kotlinMultiplatform") "-$name" else ""
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
