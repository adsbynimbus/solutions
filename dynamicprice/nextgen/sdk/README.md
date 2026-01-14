# Dynamic Price Next Gen

Dynamic Price Next Gen is a set of utilities and helper methods for including Nimbus in a Google Ad
Manager mediated auction using the [Next Gen SDK](https://developers.google.com/admob/android/early-access/nextgen).

## Setup

#### settings.gradle.kts

Add the `solutions` repository and provide a GitHub username and access token to use for the credentials.

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/adsbynimbus/solutions") {
            name = "solutions"
            credentials(PasswordCredentials::class)
            content {
                includeGroup("com.adsbynimbus.dynamicprice")
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
                includeGroup("com.adsbynimbus.dynamicprice")
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
An example can be found in the [nextgen.yml](../../../.github/workflows/nextgen.yml) workflow.

</details>

#### build.gradle.kts

Add the `com.adsbynimbus.dynamicprice:nextgen` module to you application or library dependencies.

```kotlin
dependencies {
    implementation("com.adsbynimbus.dynamicprice:nextgen:0.19.0")
}
```
