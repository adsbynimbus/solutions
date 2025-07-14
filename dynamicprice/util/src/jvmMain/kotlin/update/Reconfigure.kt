@file:JvmName("Reconfigure")

package adsbynimbus.solutions.dynamicprice.util.update

import adsbynimbus.solutions.dynamicprice.util.*
import com.google.api.ads.admanager.axis.v202505.*
import kotlinx.coroutines.delay

suspend fun main(args: Array<String>) {
    val context: AdManagerAxisClient = adManagerContext(
        appName = args[0],
        networkCode = args[1],
        credential = args.getOrNull(3)?.let { JsonKeyFile(it).credential } ?: applicationDefaultCredential
    )
    // Ranges should not overlap and should match the LinearPriceMapping used in app
    val lineItemMapping = listOf(
        IntProgression.fromClosedRange(rangeStart = 5020, rangeEnd = 10000, step = 20),
        IntProgression.fromClosedRange(rangeStart = 10050, rangeEnd = 12500, step = 50),
    )
    // Nimbus only supports standard IAB sizes
    val supportedCreativeSizes = listOf(
        Pair(320, 50),
        Pair(300, 250),
        Pair(320, 480),
    )
    var orderId = args[2].toLong()
    context.reconfigureOrder(
        lineItemMapping = lineItemMapping,
        creativeSizes = supportedCreativeSizes,
        orderId = orderId,
    ).onFailure {
        println(it.message)
        println(it)
    }
}

suspend fun AdManagerAxisClient.reconfigureOrder(
    lineItemMapping: Collection<IntProgression>,
    creativeSizes: Collection<Pair<Int, Int>>,
    orderId: Long,
    companyName: String = "Nimbus",
    orderName: String = "Nimbus Dynamic Price"
) = runCatching {
    val nimbusCompany = findOrCreateNimbusCompany(name = companyName)
    val bidKey = findOrCreateNimbusBidKey()
    val videoBidKey = findOrCreateNimbusVideoBidKey()
    delay(1000)
    val bidValues = findOrCreateBidValues(key = bidKey, ranges = lineItemMapping)
    val videoValues = findOrCreateBidValues(key = videoBidKey, ranges = lineItemMapping)
    val placement = findOrCreatePlacement(name = companyName)
    val creatives = findOrCreateCreatives(
        sizes = creativeSizes,
        company = nimbusCompany,
    )
    if (creatives.size != creativeSizes.size) throw RuntimeException("Creatives not found")
    val lineItems = appendToOrder(
        orderId = orderId,
        orderName = orderName,
        placement = placement,
        creatives = creatives,
        bidKey = bidKey,
        bidValues = bidValues,
        videoBidKey = videoBidKey,
        videoBidValues = videoValues,
        ranges = lineItemMapping
    )
    // This step takes a long time to complete
    associateCreatives(lineItems, creatives)
}

suspend fun AdManagerAxisClient.appendToOrder(
    orderId: Long,
    orderName: String,
    placement: Placement,
    creatives: List<Creative>,
    bidKey: CustomTargetingKey,
    bidValues: List<CustomTargetingValue>,
    videoBidKey: CustomTargetingKey,
    videoBidValues: List<CustomTargetingValue>,
    ranges: Collection<IntProgression>,
): List<LineItem> {
    validateKeyValues(bidValues = bidValues, videoBidValues = videoBidValues, ranges = ranges)
    val breakdown = ranges.flatMap { it.toList() }.sorted()
    if (breakdown.size != 300) throw RuntimeException("Wrong size")
    val order = orderService.getOrdersByStatement(findBy(id = orderId)).results[0]
    delay(1000)
    return buildList {
        breakdown.windowed(size = pageSize, step = pageSize, partialWindows = true).map { page ->
            page.map { lineValue ->
                createLineItem(
                    lineName = orderName,
                    value = lineValue,
                    order = order,
                    bidKey = bidKey,
                    bidValue = bidValues.find { it.name.toInt() == lineValue }!!,
                    videoKey = videoBidKey,
                    videoValue = videoBidValues.find { it.name.toInt() == lineValue }!!,
                    creatives = creatives,
                    targetedPlacement = placement
                )
            }
        }.forEach {
            addAll(lineItemService.createLineItems(it.toTypedArray()))
            delay(1000)
        }
    }
}
