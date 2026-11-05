# AdMob Android Extension

Provides support for AdMob adaptive banners

## Usage

```kotlin
Nimbus.bannerAd(position = bannerName, size = AdSize.Banner) {
    demand {
        admobAnchoredBanner(adUnitId = "ca-pub...")
    }
}
```

## Manual Setup

Copy [src/androidMain/kotlin/AdMobExtensions.kt](src/androidMain/kotlin/AdMobExtensions.kt) into your
project and replace the package name before referencing `admobAdaptiveBanner`

##

#### settings.gradle.kts

Add the `solutions` repository following the instructions in [sdk-extensions/README.md](../README.md)

#### build.gradle.kts

Add the `com.adsbynimbus.solutions:extension-admob` module to you application or library dependencies.

```kotlin
dependencies {
    implementation("com.adsbynimbus.solutions:extension-admob:25.0.0")
}
```
