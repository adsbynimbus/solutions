package adsbynimbus.solutions.dynamicprice.util.service

import com.google.api.ads.admanager.axis.factory.*
import com.google.api.ads.admanager.axis.v202505.*
import com.google.api.ads.admanager.lib.client.*
import com.google.api.ads.common.lib.auth.*
import com.google.api.client.auth.oauth2.*

class AdManagerAxisClient(
    val session: AdManagerSession,
    private val services: AdManagerServices = AdManagerServices(),
) : AdManagerContext {

    override val advertisers: List<Advertiser> get() = companiesService.nimbusAdvertisers.results.map {
        Advertiser(id = it.id, name = it.name)
    }
    override val orders: List<Order> get() = orderService.nimbusOrders.results.map {
        Order(id = it.id, name = it.name)
    }

    override val networks: List<Network> get() = networkService.allNetworks.map {
        Network(id = it.id, name = it.displayName, networkCode = it.networkCode)
    }

    private val companiesService by lazy { services.get<CompanyServiceInterface>() }
    private val orderService by lazy { services.get<OrderServiceInterface>() }
    private val lineItemService by lazy { services.get<LineItemServiceInterface>() }
    private val networkService by lazy { services.get<NetworkServiceInterface>() }
    private val targetingService by lazy { services.get<CustomTargetingServiceInterface>() }

    inline fun <reified T> AdManagerServices.get(): T = get(session, T::class.java)
}

actual fun adManagerContext(keyPath: String, appName: String): AdManagerContext =
    AdManagerAxisClient(AdManagerSession.Builder()
        .withOAuth2Credential(JsonKeyFile(keyPath).credential)
        .withApplicationName(appName)
        .build())


val CompanyServiceInterface.nimbusAdvertisers: CompanyPage
    get() = findBy(name = "Nimbus").run(::getCompaniesByStatement)

val OrderServiceInterface.nimbusOrders: OrderPage
    get() = findBy(name = "Nimbus").run(::getOrdersByStatement)

@JvmInline
value class JsonKeyFile(val path: String) {
    inline val credential: Credential get() = OfflineCredentials.Builder()
        .forApi(OfflineCredentials.Api.AD_MANAGER)
        .withJsonKeyFilePath(path)
        .build()
        .generateCredential()
}
