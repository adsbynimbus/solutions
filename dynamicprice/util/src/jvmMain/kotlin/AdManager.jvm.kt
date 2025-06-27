@file:JvmName("AdManagerJvm")
package adsbynimbus.solutions.dynamicprice.util

import com.google.api.ads.admanager.axis.factory.*
import com.google.api.ads.admanager.axis.v202408.LineItemCreativeAssociationService
import com.google.api.ads.admanager.axis.v202505.*
import com.google.api.ads.admanager.lib.client.*
import com.google.api.ads.common.lib.auth.*
import com.google.api.client.auth.oauth2.*
import kotlinx.coroutines.*

actual suspend fun main(args: Array<String>) {
    val context: AdManagerAxisClient = adManagerContext(
        appName = args[0],
        networkCode = args[1],
        credential = args.getOrNull(2)?.let { JsonKeyFile(it).credential } ?: applicationDefaultCredential
    )
    // Ranges should not overlap and should match the LinearPriceMapping used in app
    val lineItemMapping = listOf(
        IntProgression.fromClosedRange(rangeStart = 1, rangeEnd = 999, step = 1),
        IntProgression.fromClosedRange(rangeStart = 1000, rangeEnd = 1995, step = 5),
        IntProgression.fromClosedRange(rangeStart = 2000, rangeEnd = 5000, step = 10),
    )
    // Nimbus only supports standard IAB sizes
    val supportedCreativeSizes = listOf(
        Pair(320, 50),
        Pair(300, 250),
        Pair(320, 480),
    )
    context.runCatching {
        val nimbusCompany = findOrCreateNimbusCompany(name = "Nimbus")
        findOrCreateNimbusAuctionIdKey() // check creation but not required for rest of script
        delay(1000)
        val bidKey = findOrCreateNimbusBidKey()
        val videoBidKey = findOrCreateNimbusVideoBidKey()
        delay(1000)
        val bidValues = findOrCreateBidValues(key = bidKey, ranges = lineItemMapping)
        val videoValues = findOrCreateBidValues(key = videoBidKey, ranges = lineItemMapping)
        val placement = findOrCreatePlacement(name = "Nimbus")
        val creatives = findOrCreateCreatives(
            sizes = supportedCreativeSizes,
            company = nimbusCompany,
        )
        if (creatives.size != supportedCreativeSizes.size) throw RuntimeException("Creatives not found")
        val lineItems = createOrdersAndLines(
            orderName = "Nimbus Dynamic Price",
            company = nimbusCompany,
            trafficker = userService.currentUser,
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
    }.onFailure {
        println(it.message)
        println(it)
    }
}

class AdManagerAxisClient(
    val session: AdManagerSession,
    private val services: AdManagerServices = AdManagerServices(),
) {
    val companiesService by lazy { services.get<CompanyServiceInterface>() }
    val creativeService by lazy { services.get<CreativeServiceInterface>() }
    val orderService by lazy { services.get<OrderServiceInterface>() }
    val lineItemService by lazy { services.get<LineItemServiceInterface>() }
    val lineItemCreativeService by lazy { services.get<LineItemCreativeAssociationServiceInterface>() }
    val networkService by lazy { services.get<NetworkServiceInterface>() }
    val placementService by lazy { services.get<PlacementServiceInterface>() }
    val targetingService by lazy { services.get<CustomTargetingServiceInterface>() }
    val userService by lazy { services.get<UserServiceInterface>() }

    inline fun <reified T> AdManagerServices.get(): T = get(session, T::class.java)
}

fun adManagerContext(
    appName: String,
    networkCode: String,
    credential: Credential,
): AdManagerAxisClient = AdManagerAxisClient(
    session = AdManagerSession.Builder()
        .withOAuth2Credential(credential)
        .withApplicationName(appName).apply {
            if (networkCode.isNotEmpty()) withNetworkCode(networkCode)
        }
        .build()
    )

inline val applicationDefaultCredential: Credential
    get() = OfflineCredentials.Builder()
        .forApi(OfflineCredentials.Api.AD_MANAGER)
        .fromFile()
        .build()
        .generateCredential()

@JvmInline
value class JsonKeyFile(val path: String) {
    inline val credential: Credential get() = OfflineCredentials.Builder()
        .forApi(OfflineCredentials.Api.AD_MANAGER)
        .withJsonKeyFilePath(path)
        .build()
        .generateCredential()
}
