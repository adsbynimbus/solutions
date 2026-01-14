[![Build](https://github.com/adsbynimbus/solutions/actions/workflows/build.yml/badge.svg)](https://github.com/adsbynimbus/solutions/actions/workflows/build.yml)
[![CodeQL Analysis](https://github.com/adsbynimbus/solutions/actions/workflows/codeql.yml/badge.svg)](https://github.com/adsbynimbus/solutions/actions/workflows/codeql.yml)
# Nimbus Solutions Engineering

Official Nimbus documentation can be found at [https://docs.adsbynimbus.com/docs/](https://docs.adsbynimbus.com/docs/)

| Platform                                             | Supported Languages | Latest Version                                                                                                                       |
|------------------------------------------------------|---------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| Android                                              | Java, Kotlin        | ![Android](https://img.shields.io/badge/release-v2.35.2-blue)                                                                        |
| [iOS](https://github.com/adsbynimbus/nimbus-ios-sdk) | Swift               | [![iOS](https://img.shields.io/github/v/release/adsbynimbus/nimbus-ios-sdk)](https://github.com/adsbynimbus/nimbus-ios-sdk/releases) |
| [Unity](https://github.com/adsbynimbus/nimbus-unity) | C#                  | [![OpenRTB](https://img.shields.io/github/v/release/adsbynimbus/nimbus-unity)](https://github.com/adsbynimbus/nimbus-unity/releases) |
[]()

## Build / IDE Setup

IntelliJ IDEs such as IDEA Ultimate or Community edition requires version `2025.2` to build with the
Android Gradle Plugin defined in the [project version catalog](gradle/libs.versions.toml).

Android Studio Narwhal Feature Drop `2025.1.2` or newer with the latest [Kotlin Multiplatform Plugin](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-plugin-releases.html#release-details)
is recommended.

### Overriding Tooling

The Android Gradle Plugin and JVM Target can be overridden using the properties `android.gradle`
and `android.jvm` which can be defined in a `gradle.properties` file in the Gradle User Home directory
or passed via the command line.

#### gradle.properties
```properties
# Override the Android Gradle Plugin to a canary version
android.gradle=9.0.0-alpha05
# Override the bytecode used to build the project
android.jvm=17
```
#### CLI
```shell
./gradlew build -Dandroid.gradle=9.0.0-alpha05 -Dandroid.jvm=17
```
