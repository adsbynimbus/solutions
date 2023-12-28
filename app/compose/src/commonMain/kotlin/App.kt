package adsbynimbus.solutions.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*

@Composable
fun App(content: @Composable () -> Unit = { }) = NimbusTheme {
    Scaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
fun NimbusTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Pink,
        secondary = Teal,
        background = Color.Black,
        surface = Color.Black,
    ),
    content = content,
)

val Pink = Color(0xFFDB6FA3)
val Teal = Color(0xFF85D6DA)

expect val platform: String
