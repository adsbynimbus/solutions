import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    alias(libs.plugins.kotest)
    `maven-publish`
}

val codeQL = providers.environmentVariablesPrefixedBy("CODEQL").map { it.any() }

val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    archiveClassifier = "javadoc"
    description = "Creates a javadoc jar for bundling with an Android Library"
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

val dokkaHtmlJar = tasks.register<Jar>("dokkaHtmlJar") {
    archiveClassifier = "html-doc"
    description = "Creates a jar containing html docs for bundling with an Android Library"
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

kotlin {
    android {
        namespace = "com.adsbynimbus.mediation.max"
        compileSdk = libs.versions.android.sdk.get().toInt()
        minSdk = 21
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget = JvmTarget.JVM_17
            }
        }

        aarMetadata {
            minCompileSdk = 35
            minAgpVersion = "8.5.0"
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
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }

    explicitApi()

    sourceSets {
        androidMain.dependencies {
            api(libs.ads.nimbus.asProvider().flatMap{ library ->
                providers.provider {
                    library.copy().apply {
                        version {
                            require("3.0.0-rc.5")
                            library.version?.let { prefer(it) }
                        }
                    }
                }
            })
            api(libs.ads.max)
            api(libs.ads.google)
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.bundles.test.common)
                implementation(libs.kotest.runner)
                implementation(libs.kotlin.coroutines)
            }
        }
    }
}

dokka {
    moduleName = "Max Solutions"
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
                remoteUrl("https://github.com/adsbynimbus/solutions/tree/main/max-mediation/android/adapter/src/$name/kotlin")
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (JavaVersion.current() >= JavaVersion.VERSION_24) {
        jvmArgs("--sun-misc-unsafe-memory-access=allow")
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "extension-max" + if (name != "kotlinMultiplatform") "-$name" else ""
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
