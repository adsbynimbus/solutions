package adsbynimbus.solutions.dynamicprice.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun NimbusTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Color.Pink,
        secondary = Color.Teal,
        background = Color.Black,
        surface = Color.Black,
    ),
    content = content,
)

inline val Color.Companion.Pink get() = Color(0xFFDB6FA3)
inline val Color.Companion.Teal get() = Color(0xFF85D6DA)
