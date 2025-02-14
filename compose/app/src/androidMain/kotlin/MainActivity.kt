package adsbynimbus.solutions.compose.app

import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

actual val platform: String get() = "Android ${Build.VERSION.RELEASE}"

@Preview @Composable
fun AppPreview() {
    App()
}
