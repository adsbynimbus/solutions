//
//  AdInitializer.swift
//  DynamicPrice
//
//  Created by Jason Sznol on 4/8/25.
//

import DTBiOSSDK
import NimbusKit

extension DynamicPriceApp {
    func initNimbus() {
        let apiKey = Bundle.main.infoDictionary?["Nimbus API Key"] as! String
        let publisherKey = Bundle.main.infoDictionary?["Nimbus Publisher Key"] as! String

        Nimbus.initialize(publisherKey: publisherKey, apiKey: apiKey)

        Nimbus.configuration.testMode = true
    }

    func initAmazon() {
        let appKey = Bundle.main.infoDictionary?["Amazon App Id"] as! String

        let apsConfig = APSInitConfig()
        apsConfig.testMode = true
        apsConfig.logLevel = APSLogLevel.debug
        apsConfig.mraidPolicy = APSMraidPolicy.dfp
        apsConfig.mraidSupportedVersions = [APSMraidVersion2_0, APSMraidVersion3_0]

        APS.initialize(withAppKey: appKey, config: apsConfig)
    }

    static let amazonBannerSlot = Bundle.main.infoDictionary?["Amazon Banner Slot Id"] as! String

    static let amazonInterstitialSlot = Bundle.main.infoDictionary?["Amazon Interstitial Slot Id"] as! String

    static let googleAdUnitId = Bundle.main.infoDictionary?["AdManager AdUnit Id"] as! String
}
