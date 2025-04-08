# Dynamic Price

Official Nimbus documentation can be found at [https://docs.adsbynimbus.com/docs/extended-documentation/dynamic-price](https://docs.adsbynimbus.com/docs/extended-documentation/dynamic-price)

Implementations showing how to run requests to Nimbus and Amazon in parallel when both
bidders are used with Google Ad Manager.

## Android

#### [Bidders](android/src/androidMain/kotlin/Bidders.kt)

Contains classes and interfaces for running a parallel auction using any number of bidders and
applying the key-value targeting from each bid to an `AdManagerAdRequest.Builder`.

#### [DynamicPriceAd](android/src/androidMain/kotlin/DynamicPriceAd.kt)

Provides a wrapper implementation for the `AdManagerAdView` that automatically manages the bidding
process and refreshes ads every 30 seconds using a lifecycleScope.

### Google Mobile Ads 24

[Migration Guide](https://developers.google.com/ad-manager/mobile-ads-sdk/android/migration#migrate-to-v24)

Current versions of Nimbus and Amazon SDKs were compiled against Google Mobile Ads 23 and will crash
at runtime when used with version 24 or higher due to the `addCustomTargeting` method moving from
the `AdManagerAdRequest.Builder` to the super class `AbstractAdRequestBuilder<*>`.

A workaround for this has been implemented for both SDKs in [Bidders.applyTargeting](android/src/androidMain/kotlin/Bidders.kt#L63).

###### Nimbus
```kotlin
when (response) {
    is NimbusResponse -> response.run {
        dynamicPriceAdCache.put(auctionId, this)
        targetingMap(linearPriceMapping).forEach {
            request.addCustomTargeting(it.key, it.value)
        }
    }
}
```

###### Amazon
```kotlin
inline val DTBAdResponse.adManagerParams: Map<String, List<String>>
    get() = when (dtbAds.first().dtbAdType) {
        AdType.VIDEO -> defaultVideoAdsRequestCustomParams.mapValues { listOf(it.value) }
        else -> defaultDisplayAdsRequestCustomParams
    }

when (response) {
    is DTBAdResponse if response.adCount > 0 -> response.adManagerParams.forEach {
        request.addCustomTargeting(it.key, it.value)
    }
}
```

## iOS

#### [Bidders](ios/Sources/Bidders.swift)

Contains classes and interfaces for running a parallel auction using Nimbus + Amazon and
applying the key-value targeting from each bid to a `GAMRequest`.

#### [DynamicPriceView](ios/Sources/DynamicPriceView.swift)

Provides a wrapper implementation for the `GAMBannerView` that automatically manages the bidding
process and refreshes ads every 30 seconds using a lifecycleScope.
