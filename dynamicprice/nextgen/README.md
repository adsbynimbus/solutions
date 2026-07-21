# Dynamic Price Next Gen

Implementations demonstrating how to run requests to Nimbus and Amazon in parallel when both
bidders are used with Google Ad Manager using the [Next Gen SDK](https://developers.google.com/ad-manager/mobile-ads-sdk/android/next-gen).

## SDK

The Dynamic Price SDK for Next Gen has been moved to [adsbynimbus/dynamic-price](https://github.com/adsbynimbus/dynamic-price/tree/main/library/android)
with a new module name `dynamicprice` and is available in the standard Nimbus maven repository.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://adsbynimbus-public.s3.amazonaws.com/android/sdks")
    }
}

// build.gradle.kts
implementation("com.adsbynimbus.dynamicprice:dynamicprice:+")
```


#### [Bidders](src/main/kotlin/Bidders.kt)

Contains classes and interfaces for running a parallel auction using any number of bidders and
applying the key-value targeting from each bid to an `BaseAdRequestBuilder`.

#### [ViewUtil](src/main/kotlin/ViewUtil.kt)

Helper methods used for refreshing banner ads.

## [Examples](src/main/kotlin/Examples.kt)

Helper methods demonstrating how to run a parallel auction for Banner and Interstitial Ads and
attaching the callbacks with `handleEventForNimbus` implemented in `onAppEvent`.
