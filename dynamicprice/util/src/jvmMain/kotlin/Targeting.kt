package adsbynimbus.solutions.dynamicprice.util

import com.google.api.ads.admanager.axis.v202602.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

suspend fun AdManagerAxisClient.addTargeting(
    orders: Collection<Long>,
    key: Long,
    values: LongArray,
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
                    line.targeting.customTargeting?.apply {
                        children.filterIsInstance<CustomCriteriaSet>().onEach {
                            if (it.logicalOperator == CustomCriteriaSetLogicalOperator.AND) {
                                it.children += CustomCriteria().apply {
                                    keyId = key
                                    valueIds = values
                                    operator = CustomCriteriaComparisonOperator.IS_NOT
                                }
                            }
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
}
