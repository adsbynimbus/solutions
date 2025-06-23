package adsbynimbus.solutions.kmm

/** The Multiplatform implementation of the Nimbus object */
expect object Nimbus {
    var testMode: Boolean
    val version: String
}

expect fun Nimbus.initialize(publisherKey: String, apiKey: String)

expect var Nimbus.endpointOverride: String?

expect var Nimbus.enableLogs: Boolean
