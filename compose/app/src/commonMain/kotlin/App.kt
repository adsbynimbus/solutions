package adsbynimbus.solutions.compose.app

import adsbynimbus.solutions.kmm.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

@Composable
fun App(screenHeight: Int) {
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Nimbus.initialize(publisherKey = "", apiKey = "")
        Nimbus.testMode = true
        Nimbus.enableLogs = true
        //Nimbus.endpointOverride =
        initialized = true
    }
    NimbusTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (initialized) {
                    Spacer(Modifier.height((screenHeight / 3).dp))
                    AdView(position = "test_banner", size = Pair(320, 50))
                    Spacer(Modifier.height(screenHeight.dp))
                    AdView(position = "test_mrec", size = Pair(300, 250))
                    Spacer(Modifier.height((screenHeight / 3).dp))
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
