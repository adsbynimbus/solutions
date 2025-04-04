package adsbynimbus.solutions.dynamicprice.util

import adsbynimbus.solutions.dynamicprice.util.service.AdManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LineItemApp(
    databaseBuilder: RoomDatabase.Builder<AppDatabase>,
    scope: CoroutineScope = rememberCoroutineScope(),
) {
    val appDatabase = remember { getRoomDatabase(databaseBuilder) }
    NimbusTheme {
        Surface {
            val networks by appDatabase.networkDao().listFlow().collectAsState(emptyList())
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Button(onClick = {
                    scope.launch {
                        appDatabase.networkDao().insert(AdManager.networks)
                    }
                }) {
                    Text("Refresh")
                }
                networks.forEach {
                    Button(onClick = { }) {
                        Text("${it.name} - ${it.networkCode}")
                    }
                }
            }
        }
    }
}

@Composable
fun NimbusTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Color(0xFFDB6FA3),   /* Pink */
        secondary = Color(0xFF85D6DA), /* Teal */
        background = Color.Black,
        surface = Color.Black,
    ),
    content = content,
)
