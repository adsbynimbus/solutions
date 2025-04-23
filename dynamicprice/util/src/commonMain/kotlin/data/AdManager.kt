package adsbynimbus.solutions.dynamicprice.util.data

import kotlinx.coroutines.flow.MutableStateFlow

object AdManager {
    val currentNetwork = MutableStateFlow(Network.None)

    var network = currentNetwork.value
        set(value) {
            currentNetwork.value = value
            context = adManagerContext(network = currentNetwork.value.networkCode)
        }
    var context = adManagerContext(network = currentNetwork.value.networkCode)

    val nimbusAdvertisers: List<Advertiser> get() = context.advertisers
    val nimbusOrders: List<Order> get() = context.orders
    val networks: List<Network> get() = context.networks
}

interface AdManagerContext {
    val advertisers: List<Advertiser>
    val orders: List<Order>
    val networks: List<Network>
}

data class Advertiser(
    var id: Long,
    var name: String,
)

expect fun adManagerContext(
    keyPath: String = System.getenv("GOOGLE_APPLICATION_CREDENTIALS"),
    appName: String = "LineItemTool",
    network: Network = Network.None,
): AdManagerContext
