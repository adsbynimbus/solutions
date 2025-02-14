package adsbynimbus.solutions.compose.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*

@Composable
fun App() = NimbusTheme {
    Surface {
        var greetingText by remember { mutableStateOf("Hello World!") }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Button(onClick = { greetingText = "Compose: $platform" }) {
                Text(greetingText)
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

expect val platform: String
