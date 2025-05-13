# Dynamic Price

Official Nimbus documentation can be found at [https://docs.adsbynimbus.com/docs/extended-documentation/dynamic-price](https://docs.adsbynimbus.com/docs/extended-documentation/dynamic-price)

Implementations demonstrating how to run requests to Nimbus and Amazon in parallel when both
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

Support for Google Mobile Ads 24 is available starting from the following versions:

- Nimbus Android: [2.30.0](https://docs.adsbynimbus.com/docs/sdk/android/changelog#id-2.30.0-5-13-25)
- Amazon APS Android: [11.0.0](https://ams.amazon.com/webpublisher/uam/docs/aps-mobile/android/release-notes) (Link requires Login)

<details>
<summary>Workaround for older SDK Versions</summary>
<br/>
Previous versions of the Nimbus and Amazon SDKs will crash at runtime when used with version 24 or
higher due to the `addCustomTargeting` method moving from the `AdManagerAdRequest.Builder` to the
super class `AbstractAdRequestBuilder<*>`.

###### Nimbus - Requires 2.28.0 or higher
Replace calls to `applyDynamicPrice` with it's method body
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
Replace calls to `DTBAdUtil.INSTANCE.loadDTBParams` with it's method body
```kotlin
/** Implementation of a private helper method used by loadDTBParams to retrieve kv pairs */
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
</details>

## iOS

#### [Bidders](ios/Sources/Bidders.swift)

Contains classes and interfaces for running a parallel auction using Nimbus + Amazon and
applying the key-value targeting from each bid to a `AdManagerRequest`.

#### [DynamicPriceView](ios/Sources/DynamicPriceView.swift)

Provides a wrapper implementation for the `AdManagerBannerView` that automatically manages the bidding
process and refreshes ads every 30 seconds when attached to the view hierarchy.

## Next Gen

Android Implementation of Dynamic Price using the Google Mobile Ads Next Gen SDK
