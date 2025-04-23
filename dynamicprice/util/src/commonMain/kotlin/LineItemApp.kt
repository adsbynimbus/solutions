package adsbynimbus.solutions.dynamicprice.util

import adsbynimbus.solutions.dynamicprice.util.data.AdManager
import adsbynimbus.solutions.dynamicprice.util.data.Network
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nimbus_solutions.dynamicprice.util.generated.resources.Res
import nimbus_solutions.dynamicprice.util.generated.resources.networks
import nimbus_solutions.dynamicprice.util.generated.resources.orders
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class Destinations(
    val label: StringResource,
) {
    Networks(label = Res.string.networks),
    Orders(label = Res.string.orders)
}

@Composable
fun LineItemApp(
    databaseBuilder: RoomDatabase.Builder<AppDatabase>,
) {
    val appDatabase = remember { getRoomDatabase(databaseBuilder) }
    var currentDestination by rememberSaveable { mutableStateOf(Destinations.Networks) }
    val currentNetwork by AdManager.currentNetwork.collectAsState()
    NimbusTheme {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Destinations.entries.forEach {
                    item(
                        icon = { },
                        label = { Text(stringResource(it.label)) },
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            when(currentDestination) {
                Destinations.Networks -> Networks(appDatabase, currentNetwork) {
                    AdManager.network = it
                }
                Destinations.Orders -> Orders(appDatabase)
            }
        }
    }
}

@Composable
fun Orders(
    database: AppDatabase,
    scope: CoroutineScope = rememberCoroutineScope(),
) {
    val orders by database.ordersDao.listFlow().collectAsState(emptyList())
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = {
            scope.launch {
                database.ordersDao.insert(AdManager.nimbusOrders)
            }
        }) {
            Text("Refresh")
        }
        orders.forEach {
            Button(onClick = { }) {
                Text("${it.name} - ${it.id}")
            }
        }
    }
}

@Composable
fun Networks(
    database: AppDatabase,
    currentNetwork: Network,
    scope: CoroutineScope = rememberCoroutineScope(),
    onNetworkSelected: (Network) -> Unit,
) {
    val networks by database.networkDao.listFlow().collectAsState(emptyList())
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = {
            scope.launch {
                database.networkDao.insert(AdManager.networks)
            }
        }) {
            Text("Refresh")
        }
        networks.forEach {
            Button(
                onClick = { onNetworkSelected(it) },
                enabled = it != currentNetwork,
            ) {
                Text("${it.name} - ${it.networkCode}")
            }
        }
    }
}
