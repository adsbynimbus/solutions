package adsbynimbus.solutions.dynamicprice.util

import com.google.api.ads.admanager.axis.v202505.*
import com.google.api.ads.admanager.axis.v202505.CustomTargetingKeyType.*
import java.text.DecimalFormat
import kotlinx.coroutines.delay
import kotlin.collections.addAll
import kotlin.collections.windowed
import kotlin.jvm.Throws

/** Override `name` if more than 1 company found */
suspend fun AdManagerAxisClient.findOrCreateNimbusCompany(
    name: String = "Nimbus",
): Company = companiesService.run {
    val companies = getCompaniesByStatement(findBy(name = name))
    when (companies.totalResultSetSize) {
        0 -> createCompanies(arrayOf(adNetwork(name = name)))[0]
        1 -> companies.getResults(0)
        else -> throw RuntimeException("More than 1 $name company found")
    }
}

/** Creates a new Ad Network Company */
fun adNetwork(name: String): Company = Company().also {
    it.name = name
    it.type = CompanyType.AD_NETWORK
}

suspend fun AdManagerAxisClient.findOrCreateKey(
    name: String,
    displayName: String,
    type: CustomTargetingKeyType,
    reporting: ReportableType,
): CustomTargetingKey = targetingService.run {
    val keys = getCustomTargetingKeysByStatement(findBy(name = name))
    when (keys.totalResultSetSize) {
        0 -> createCustomTargetingKeys(arrayOf(CustomTargetingKey().also {
            it.name = name
            it.displayName = displayName
            it.type = type
            it.reportableType = reporting
        }))[0]
        1 -> keys.getResults(0)
        else -> throw RuntimeException("More than 1 $name key found")
    }
}

suspend fun AdManagerAxisClient.findOrCreateNimbusAuctionIdKey(): CustomTargetingKey = findOrCreateKey(
    name = "na_id",
    displayName = "Nimbus Auction Id",
    type = FREEFORM,
    reporting = ReportableType.OFF,
)

suspend fun AdManagerAxisClient.findOrCreateNimbusBidKey(): CustomTargetingKey = findOrCreateKey(
    name = "na_bid",
    displayName = "Nimbus Bid",
    type = PREDEFINED,
    reporting = ReportableType.ON,
)

suspend fun AdManagerAxisClient.findOrCreateNimbusVideoBidKey(): CustomTargetingKey = findOrCreateKey(
    name = "na_bid_video",
    displayName = "Nimbus Video Bid",
    type = PREDEFINED,
    reporting = ReportableType.ON,
)

suspend fun AdManagerAxisClient.findOrCreateBidValues(
    key: CustomTargetingKey,
    ranges: Collection<IntProgression>,
): List<CustomTargetingValue> = buildList {
    val values = statement {
        where("customTargetingKeyId = :customTargetingKeyId")
        withBindVariableValue("customTargetingKeyId", key.id)
    }
    var existing = 0
    do {
        targetingService.getCustomTargetingValuesByStatement(values.toStatement()).run {
            existing = totalResultSetSize.takeUnless { it == 0 } ?: return@run
            addAll(results)
            values.increaseOffsetBy(pageSize)
            delay(1000)
        }
    } while (existing > 0 && values.offset < existing)
    val valuesToCreate = ranges.flatMap { it.toList() }
        .filter { value -> find { it.name.toInt() == value } == null }
        .map {
            CustomTargetingValue().apply {
                customTargetingKeyId = key.id
                name = it.toString()
                displayName = it.toString()
            }
        }
    valuesToCreate.windowed(size = pageSize, step = pageSize, partialWindows = true).forEach {
        addAll(targetingService.createCustomTargetingValues(it.toTypedArray()))
        delay(1000)
    }
    sortBy { it.name.toInt() }
}

@Throws(RuntimeException::class)
fun validateKeyValues(
    bidValues: List<CustomTargetingValue>,
    videoBidValues: List<CustomTargetingValue>,
    ranges: Collection<IntProgression>,
) {
    val missingBids = ranges.flatMap { it.asSequence() }.filter { value ->
        bidValues.find { it.name.toInt() == value } == null
    }
    val missingVideoBids = ranges.flatMap { it.asSequence() }.filter { value ->
        videoBidValues.find { it.name.toInt() == value } == null
    }
    if (missingBids.isNotEmpty() || missingVideoBids.isNotEmpty()) {
        missingBids.forEach { print("Missing Bid Key: $it") }
        missingVideoBids.forEach { print("Missing Video Bid Key: $it") }
        throw RuntimeException("Missing Key Values")
    }
}


suspend fun AdManagerAxisClient.findOrCreatePlacement(
    name: String = "Nimbus",
    description: String = "Nimbus Dynamic Price Eligible Inventory",
    targetAdUnitIds: Collection<String> = listOf(networkService.currentNetwork.effectiveRootAdUnitId),
): Placement = placementService.run {
    val placements = getPlacementsByStatement(findBy(name = name))
    when (placements.totalResultSetSize) {
        0 -> createPlacements(arrayOf(placement(name, description, targetAdUnitIds)))[0]
        1 -> placements.getResults(0)
        else -> throw RuntimeException("More than 1 $name placement found")
    }
}

fun placement(
    name: String,
    description: String,
    targetAdUnitIds: Collection<String>,
) = Placement().also {
    it.name = name
    it.description = description
    it.targetedAdUnitIds = targetAdUnitIds.toTypedArray()
}

suspend fun AdManagerAxisClient.findOrCreateCreatives(
    sizes: Collection<Pair<Int, Int>>,
    company: Company,
    name: String = company.name,
): List<Creative> = buildList {
    val creatives = statement {
        where("name LIKE :name AND advertiserId = :company")
        withBindVariableValue("name", "%$name%")
        withBindVariableValue("company", company.id)
    }
    var existing = 0
    do {
        creativeService.getCreativesByStatement(creatives.toStatement()).run {
            existing = totalResultSetSize.takeUnless { it == 0 } ?: return@run
            addAll(results.filterIsInstance<ThirdPartyCreative>())
            creatives.increaseOffsetBy(pageSize)
            delay(1000)
        }
    } while (existing > 0 && creatives.offset < existing)
    val newCreatives = sizes.filter { (width, height) ->
        find { it.size.width == width && it.size.height == height } == null
    }.map { (width, height) ->
        ThirdPartyCreative().also {
            it.name = "$name ${width}x${height}"
            it.size = Size().also {
                it.width = width
                it.height = height
                it.isAspectRatio = false
            }
            it.advertiserId = company.id
            it.snippet = """
<script type="text/javascript" src="https://www.gstatic.com/afma/api/v1/google_mobile_app_ads.js"></script>
<script type="text/javascript">
  admob.events.dispatchAppEvent("na_render", '{"na_id": "%%PATTERN:na_id%%", "ga_click": "%%CLICK_URL_UNESC%%"}');
  admob.events.addEventListener("onshow", () => admob.events.dispatchAppEvent("na_show", ''));
</script>
            """.trimIndent()
        }
    }
    if (newCreatives.size > 0) {
        addAll(creativeService.createCreatives(newCreatives.toTypedArray()))
    }
}

val df = DecimalFormat("00.00")
suspend fun AdManagerAxisClient.createOrdersAndLines(
    orderName: String,
    company: Company,
    trafficker: User,
    placement: Placement,
    creatives: List<Creative>,
    bidKey: CustomTargetingKey,
    bidValues: List<CustomTargetingValue>,
    videoBidKey: CustomTargetingKey,
    videoBidValues: List<CustomTargetingValue>,
    ranges: Collection<IntProgression>,
): List<LineItem> {
    validateKeyValues(bidValues = bidValues, videoBidValues = videoBidValues, ranges = ranges)
    val breakdown = ranges.flatMap { it.toList() }.sorted().windowed(size = 450, step = 450, partialWindows = true)
    val orders = orderService.createOrders(breakdown.map {
        Order().apply {
            name = "$orderName ${df.format(it.first() / 100f)} - ${df.format(it.last() / 100f)}"
            advertiserId = company.id
            traffickerId = trafficker.id
        }
    }.toTypedArray())
    delay(1000)
    return buildList {
        breakdown.zip(orders).forEach { (lines, order) ->
            lines.windowed(size = pageSize, step = pageSize, partialWindows = true).map { page ->
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
}

fun createLineItem(
    lineName: String,
    value: Int,
    order: Order,
    bidKey: CustomTargetingKey,
    bidValue: CustomTargetingValue,
    videoKey: CustomTargetingKey,
    videoValue: CustomTargetingValue,
    creatives: List<Creative>,
    targetedPlacement: Placement,
): LineItem = LineItem().apply {
    name = "$lineName ${df.format(value / 100f)}"
    orderId = order.id
    lineItemType = LineItemType.PRICE_PRIORITY
    priority = 12
    costType = CostType.CPM
    costPerUnit = Money("USD", value * 10000L)
    primaryGoal = Goal().apply { goalType = GoalType.NONE }
    startDateTime = DateTime()
    startDateTimeType = StartDateTimeType.IMMEDIATELY
    unlimitedEndDateTime = true
    creativeRotationType = CreativeRotationType.EVEN
    creativePlaceholders = creatives.map { CreativePlaceholder().apply { size = it.size } }.toTypedArray()
    skipInventoryCheck = true
    targeting = Targeting().apply {
        inventoryTargeting = InventoryTargeting().apply {
            targetedPlacementIds = longArrayOf(targetedPlacement.id)
        }
        customTargeting = CustomCriteriaSet().apply {
            logicalOperator = CustomCriteriaSetLogicalOperator.OR
            children = arrayOf(
                CustomCriteria().apply {
                    keyId = bidKey.id
                    valueIds = longArrayOf(bidValue.id)
                    operator = CustomCriteriaComparisonOperator.IS
                },
                CustomCriteria().apply {
                    keyId = videoKey.id
                    valueIds = longArrayOf(videoValue.id)
                    operator = CustomCriteriaComparisonOperator.IS
                },
            )
        }
    }
}

suspend fun AdManagerAxisClient.associateCreatives(
    lineItems: Collection<LineItem>,
    creatives: Collection<Creative>,
) {
    val associations = lineItems.flatMap { line ->
        creatives.map {
            LineItemCreativeAssociation().apply {
                lineItemId = line.id
                creativeId = it.id
            }
        }
    }
    lineItemCreativeService.createLineItemCreativeAssociations(associations.toTypedArray())
}
