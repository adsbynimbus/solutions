package adsbynimbus.solutions.bidding

import kotlinx.coroutines.*
import kotlin.jvm.*

fun interface Bidder<R> {
    suspend fun fetchBid(): Bid<R>
}

@JvmInline value class Bid<R>(val response: R)

suspend fun Collection<Bidder<*>>.auction(
    timeoutInMillis: Long = 1000L,
): List<Bid<*>> = supervisorScope {
    val requests = map {
        async { withTimeout(timeoutInMillis) { it.fetchBid() } }
    }

    requests.map { it.await() }
}
