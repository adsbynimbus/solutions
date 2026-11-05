# Dynamic Price

Dynamic Price documentation and modules are available in the
[Dynamic Price GitHub Repository](https://github.com/adsbynimbus/dynamic-price)

Implementations demonstrating how to run requests to Nimbus and Amazon in parallel when both
bidders are used with Google Ad Manager.

## [Android Next Gen](nextgen)

Android Dynamic Price implementation using the Google Mobile Ads Next Gen SDK.

## Android

#### [Bidders](android/src/main/kotlin/Bidders.kt)

Contains classes and interfaces for running a parallel auction using any number of bidders and
applying the key-value targeting from each bid to an `AdManagerAdRequest.Builder`.

#### [DynamicPriceAd](android/src/main/kotlin/DynamicPriceAd.kt)

Provides a wrapper implementation for the `AdManagerAdView` that automatically manages the bidding
process and refreshes ads every 30 seconds using a lifecycleScope.

#### [DynamicPriceInterstitial](android/src/main/kotlin/DynamicPriceInterstitial.kt)

Coroutine implementation for fetching bids and loading an `AdManagerInterstitialAd`.

## iOS

#### [Bidders](ios/Sources/Bidders.swift)

Contains classes and interfaces for running a parallel auction using Nimbus + Amazon and
applying the key-value targeting from each bid to a `AdManagerRequest`.

#### [DynamicPriceView](ios/Sources/DynamicPriceView.swift)

Provides a wrapper implementation for the `AdManagerBannerView` that automatically manages the bidding
process and refreshes ads every 30 seconds when attached to the view hierarchy.
