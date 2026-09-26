# Max Mediation Adapter for Nimbus SDK

Provides AppLovin MAX bidding support for Nimbus SDK.

## Usage

Add the extension to your dependencies:

```kotlin
dependencies {
    implementation("com.adsbynimbus.solutions:extension-max:1.0.0")
}
```

Use the extension functions on `NimbusRequest`:

```kotlin
NimbusRequest.forBannerAd(position = "banner", Format.BANNER_320_50, Position.FOOTER).apply {
    withMaxBanner(placementId = "YOUR_MAX_PLACEMENT_ID")
}
```

```kotlin
NimbusRequest.forInterstitialAd(position = "interstitial").apply {
    withMaxInterstitial(placementId = "YOUR_MAX_PLACEMENT_ID")
}
```

```kotlin
NimbusRequest.forRewardedAd(position = "rewarded").apply {
    withMaxRewarded(placementId = "YOUR_MAX_PLACEMENT_ID")
}
```

## Setup

1. Add the AppLovin MAX SDK to your app
2. Initialize AppLovin SDK before Nimbus
3. Ensure AppLovin SDK Key is configured in MAX dashboard
4. Create placement IDs for banner/interstitial/rewarded
5. Add Nimbus as a mediation partner in MAX dashboard with bidding enabled

## Notes

This API is pre-release and subject to change.
