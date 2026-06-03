package adsbynimbus.solutions.dynamicprice.util

import com.google.api.ads.admanager.axis.v202602.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

inline val Size.isBanner get() = width == 320 && height == 50
inline val Size.isInterstitial get() = width == 320 && height == 480

suspend fun AdManagerAxisClient.addAdaptiveBannerSupport(
    orders: Collection<Long>,
    sizeId: Long,
    bannerCreative: Long,
    mrecCreative: Long,
    interstitialCreative: Long,
    bannerTargetingName: String = "Nimbus Banner",
    interstitialTargetingName: String = "Nimbus Interstitial",

) {
    var totalLines = 0
    for (orderId in orders) {
        val orderLines = statement {
            where("orderId = :orderId")
            withBindVariableValue("orderId", orderId)
        }
        do {
            lineItemService.getLineItemsByStatement(orderLines.toStatement()).run {
                totalLines = totalResultSetSize
                val updates = results?.onEach { line ->
                    line.creativeTargetings = arrayOf(
                        CreativeTargeting().also {
                            it.name = bannerTargetingName
                            it.targeting = Targeting().apply {
                                customTargeting = CustomCriteriaSet().apply {
                                    logicalOperator = CustomCriteriaSetLogicalOperator.OR
                                    children = arrayOf(
                                        CustomCriteria().apply {
                                            keyId = sizeId
                                            valueIds = longArrayOf(mrecCreative, interstitialCreative)
                                            operator = CustomCriteriaComparisonOperator.IS_NOT
                                        },
                                    )
                                }
                            }
                        },
                        CreativeTargeting().also {
                            it.name = interstitialTargetingName
                            it.targeting = Targeting().apply {
                                customTargeting = CustomCriteriaSet().apply {
                                    logicalOperator = CustomCriteriaSetLogicalOperator.OR
                                    children = arrayOf(
                                        CustomCriteria().apply {
                                            keyId = sizeId
                                            valueIds = longArrayOf(bannerCreative, mrecCreative)
                                            operator = CustomCriteriaComparisonOperator.IS_NOT
                                        },
                                    )
                                }
                            }
                        },
                    )
                    line.creativePlaceholders.onEach {
                        when {
                            it.size.isBanner -> it.targetingName = bannerTargetingName
                            it.size.isInterstitial -> it.targetingName = interstitialTargetingName
                        }
                    }
                }
                delay(1.seconds)
                lineItemService.updateLineItems(updates)
                delay(1.seconds)
                orderLines.increaseOffsetBy(pageSize)
            }
        } while (orderLines.offset < totalLines)
        totalLines = 0
    }

    val bannerCreative = statement {
        where("creativeId = :creativeId")
        withBindVariableValue("creativeId", bannerCreative)
    }
    val interstitialCreative = statement {
        where("creativeId = :creativeId")
        withBindVariableValue("creativeId", interstitialCreative)
    }
    totalLines = 0
    do {
        lineItemCreativeService.getLineItemCreativeAssociationsByStatement(bannerCreative.toStatement()).run {
            totalLines = totalResultSetSize
            val updates = results?.onEach {
                it.targetingName = bannerTargetingName
            }
            delay(1.seconds)
            lineItemCreativeService.updateLineItemCreativeAssociations(updates)
            delay(1.seconds)
            bannerCreative.increaseOffsetBy(pageSize)
        }
    } while (bannerCreative.offset < totalLines)
    totalLines = 0
    do {
        lineItemCreativeService.getLineItemCreativeAssociationsByStatement(interstitialCreative.toStatement()).run {
            totalLines = totalResultSetSize
            val updates = results.onEach {
                it.apply { targetingName = interstitialTargetingName }
            }
            delay(1.seconds)
            lineItemCreativeService.updateLineItemCreativeAssociations(updates)
            delay(1.seconds)
            interstitialCreative.increaseOffsetBy(pageSize)
        }
    } while (interstitialCreative.offset < totalLines)
}
