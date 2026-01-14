# AdMob Bidding NextGen Extension

Provides support for AdMob adaptive banners

## Usage

Replace the `com.adsbynimbus.android:extension-admob` module with `com.adsbynimbus.solutions:extension-admob-nextgen:0.22.0`

The package name for the imports is `import com.adsbynimbus.solutions.nextgen`

```kotlin
NimbusRequest.forBannerAd(position = ..., Format.BANNER_320_50, Position.FOOTER).apply {
    withAdMobAnchoredBanner(adUnitId = "ca-pub...")
}
```

## Manual Setup

Copy [src/androidMain/kotlin/AdMobNextGenExtensions.kt](src/androidMain/kotlin/AdMobNextGenExtensions.kt) into your
project and replace the package name before referencing `withAdMobAdaptiveBanner`

##

#### settings.gradle.kts

Add the `solutions` repository following the instructions in [sdk-extensions/README.md](../README.md)

#### build.gradle.kts

Add the `com.adsbynimbus.solutions:extension-admob` module to you application or library dependencies.

```kotlin
dependencies {
    implementation("com.adsbynimbus.solutions:extension-admob-nextgen:0.22.0")
}
```
