package adsbynimbus.solutions.dynamicprice.util.data

import kotlinx.coroutines.flow.MutableStateFlow

object AdManager {
    val currentNetwork = MutableStateFlow(textNow)

    var network = currentNetwork.value
        set(value) {
            currentNetwork.value = value
            context = adManagerContext(network = currentNetwork.value)
        }
    var context = adManagerContext(network = currentNetwork.value)

    val nimbusAdvertisers: List<Advertiser> get() = context.advertisers
    val nimbusOrders: List<Order> get() = context.orders
    val networks: List<Network> get() = context.networks
}

interface AdManagerContext {
    val advertisers: List<Advertiser>
    val orders: List<Order>
    val networks: List<Network>
    val currentNetwork: Network
}

data class Advertiser(
    var id: Long,
    var name: String,
)

val textNow = Network(id = 0, name = "TextNow", networkCode = "2897118")

expect fun adManagerContext(
    keyPath: String = System.getenv("GOOGLE_APPLICATION_CREDENTIALS"),
    appName: String = "ascendeum-automation",
    network: Network = textNow,
): AdManagerContext
