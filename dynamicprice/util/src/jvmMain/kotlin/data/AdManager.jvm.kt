package adsbynimbus.solutions.dynamicprice.util.data

import com.google.api.ads.admanager.axis.factory.*
import com.google.api.ads.admanager.axis.v202505.*
import com.google.api.ads.admanager.lib.client.*
import com.google.api.ads.common.lib.auth.*
import com.google.api.client.auth.oauth2.*

class AdManagerAxisClient(
    val network: Network,
    val session: AdManagerSession,
    private val services: AdManagerServices = AdManagerServices(),
) : AdManagerContext {

    override val advertisers: List<Advertiser> get() = companiesService.nimbusAdvertisers.results.map {
        Advertiser(id = it.id, name = it.name)
    }
    override val orders: List<Order> get() = orderService.nimbusOrders.results.map {
        Order(id = it.id, name = it.name, networkId = network.id)
    }

    override val networks: List<Network> get() = networkService.allNetworks.map {
        Network(id = it.id, name = it.displayName, networkCode = it.networkCode)
    }

    override val currentNetwork: Network
        get() = networkService.currentNetwork.let {
            Network(id = it.id, name = it.displayName, networkCode = it.networkCode)
        }

    val companiesService by lazy { services.get<CompanyServiceInterface>() }
    val orderService by lazy { services.get<OrderServiceInterface>() }
    val lineItemService by lazy { services.get<LineItemServiceInterface>() }
    val networkService by lazy { services.get<NetworkServiceInterface>() }
    val targetingService by lazy { services.get<CustomTargetingServiceInterface>() }

    inline fun <reified T> AdManagerServices.get(): T = get(session, T::class.java)
}

actual fun adManagerContext(keyPath: String, appName: String, network: Network): AdManagerContext =
    AdManagerAxisClient(network = network, session = AdManagerSession.Builder()
        .withOAuth2Credential(JsonKeyFile(keyPath).credential)
        .withApplicationName(appName).apply {
            if (network.networkCode.isNotEmpty()) withNetworkCode(network.networkCode)
        }
        .build()
    )


val CompanyServiceInterface.nimbusAdvertisers: CompanyPage
    get() = findBy(name = "Nimbus").run(::getCompaniesByStatement)

val OrderServiceInterface.nimbusOrders: OrderPage
    get() = findBy(name = "Dynamic Price").run(::getOrdersByStatement)

@JvmInline
value class JsonKeyFile(val path: String) {
    inline val credential: Credential get() = OfflineCredentials.Builder()
        .forApi(OfflineCredentials.Api.AD_MANAGER)
        .withJsonKeyFilePath(path)
        .build()
        .generateCredential()
}
