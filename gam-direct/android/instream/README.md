# Nimbus Instream Media3 Extension ![Latest Release](https://img.shields.io/github/v/tag/adsbynimbus/solutions?filter=instream*)

Provides support for running Nimbus Instream ads using an extension of the Media3 IMA SDK Plugin

## Setup
1. Follow the instructions in [Build Setup](#build-setup) to import the module in your application.
   - If your app supports minSdk < 26, enable [coreLibraryDesugaring](#core-library-desugaring) required by
       the [IMA SDK](https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/get-started#2.-add-the-ima-sdk-to-your-project)
2. Replace the existing references to `ImaAdLoader` with the `NimbusImaAdLoader` implementation.
3. Call `setNimbusRequest(NimbusRequest.forVideoAd(position = "preroll"))` on the
`NimbusImaAdLoader.Builder` before building the AdLoader.
4. Ensure the `NimbusImaAdLoader` is passed to the player by calling `setLocalAdInsertionComponents({ _ -> adLoader}, playerView)`
on the MediaSourceFactory instance used by the player. Note: the `playerView` parameter is optional
and null may be used if the previous implementation did not use it.
5. Set the player on the `NimbusImaAdLoader` by calling `setPlayer(player)`.
6. Modify your existing Google Ad tag passed to the `AdsConfiguration` to include `&nofb=1` to
disable serving fallback video ads and `&cust_params=nimbus%3Ddirect` to identify this request as a
Nimbus GAM Direct request.

### Example
The following implementation can be found in [GAMDirectInstream.kt](../src/main/kotlin/GAMDirectInstream.kt)
```kotlin
import com.adsbynimbus.solutions.gamdirect.instream

// Create a NimbusImaAdLoader with the NimbusRequest from forVideoAd set on it; the position
// property is used for reporting metrics of this ad on the Nimbus dashboard
val adLoader = NimbusImaAdLoader.Builder(context)
    .setImaSdkSettings(imaSettings)
    .setNimbusRequest(NimbusRequest.forVideoAd(position = "preroll"))
    .build()

val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
    // Set the NimbusImaAdLoader as the ad insertion for all player Uris
    .setLocalAdInsertionComponents({ _ -> adLoader }, playerView)

// Pass the mediaSourceFactory with NimbusImaAdLoader to the ExoPlayer instance
val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()

// Set the player on the NimbusImaAdLoader
adLoader.setPlayer(player)

// The Google tag requires nofb=1 and &cust_params=nimbus%3Ddirect appended to it
val gamDirectAdTag = "$adTag&nofb=1&cust_params=nimbus%3Ddirect"

// Set the AdsConfiguration on the MediaItem pointed to the Google Ad Tag
player.setMediaItem(
    Builder()
        .setUri(contentUrl)
        .setAdsConfiguration(AdsConfiguration.Builder(gamDirectAdTag.toUri()).build())
        .build(),
)

player.playWhenReady = true
player.prepare()
```

## Build Setup

#### settings.gradle.kts

Add the `solutions` repository and provide a GitHub username and access token to use for the credentials.

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/adsbynimbus/solutions") {
            name = "solutions"
            credentials(PasswordCredentials::class)
            content {
                includeGroup("com.adsbynimbus.solutions")
            }
        }
    }
}
```

<details>
<summary>Providing Credentials for GitHub Packages</summary>

GitHub Packages requires a username and personal access token for authentication to download the sdk.

### Local Dev Environment

The snippet above looks for a Gradle property named `solutionsUsername` and `solutionsPassword` to
authenticate for the repository named `solutions`. To prevent leaking credentials, these properties
should be stored in your Gradle user home directory.

#### ~/.gradle/gradle.properties

```properties
solutionsUsername=yourGithubUsername
solutionsPassword=ghp_personalAccessTokenWithPackagesAccess
```

You can also pass credentials directly in the maven repository definition using any type of Gradle
provider. The name of the repository can be omitted when passing credentials directly.

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/adsbynimbus/solutions") {
            credentials {
                username = providers.environmentVariable("GITHUB_USERNAME").get()
                password = providers.systemProperty("GITHUB_ACCESS_TOKEN").get()
            }
            content {
                includeGroup("com.adsbynimbus.solutions")
            }
        }
    }
}
```

#### GitHub Actions

Credentials can be passed directly to the Gradle build by declaring a top level environment variable
with the GitHub workflow actor and token.

##### .github/workflows/build.yml

```yaml
env:
  ORG_GRADLE_PROJECT_solutionsUsername: ${{ github.actor }}
  ORG_GRADLE_PROJECT_solutionsPassword: ${{ github.token }}
```

An example can be found in the [admob.yml](../../../.github/workflows/admob.yml) workflow.

</details>

#### build.gradle.kts

Add the `com.adsbynimbus.solutions:gamdirect-instream` module to you application or library dependencies.

```kotlin
dependencies {
    implementation("com.adsbynimbus.solutions:gamdirect-instream:1.10.1")
}
```

## Manual Setup

Copy the files in [src/androidMain/kotlin](src/androidMain/kotlin) into your project.

## Core Library Desugaring

If your app supports minSdk < 26, enable `coreLibraryDesugaring` with the following code in your
application build script.

`build.gradle.kts`
```kotlin
android {
    compileOptions {
        // Required by IMA SDK v3.37.0+
        coreLibraryDesugaringEnabled = true

        // Java 17 required by Gradle 8+
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
```
