# Dynamic Price Next Gen

Implementations demonstrating how to run requests to Nimbus and Amazon in parallel when both
bidders are used with Google Ad Manager using the [Next Gen SDK](https://developers.google.com/admob/android/early-access/nextgen).

## Required Files

#### [Bidders](src/androidMain/kotlin/Bidders.kt)

Contains classes and interfaces for running a parallel auction using any number of bidders and
applying the key-value targeting from each bid to an `BaseAdRequestBuilder`.

#### [DynamicPrice](src/androidMain/kotlin/DynamicPrice.kt)

All methods required for rendering Nimbus Ads using the Next Gen SDK.

#### [ViewUtil](src/androidMain/kotlin/ViewUtil.kt)

Helper methods used for refreshing banner ads.

## [Examples](src/androidMain/kotlin/Examples.kt)

Helper methods demonstrating how to run a parallel auction for Banner and Interstitial Ads and
attaching the callbacks with `handleEventForNimbus` implemented in `onAppEvent`.
