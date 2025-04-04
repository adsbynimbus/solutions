package adsbynimbus.solutions.dynamicprice.util.service

object AdManager {
    private val context = adManagerContext(keyPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS"))

    val nimbusAdvertisers: List<Advertiser> by lazy { context.advertisers }
    val nimbusOrders: List<Order> by lazy { context.orders }
    val networks: List<Network> by lazy { context.networks }
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

data class Order(
    var id: Long,
    var name: String,
)

expect fun adManagerContext(keyPath: String, appName: String = "LineItemTool"): AdManagerContext
