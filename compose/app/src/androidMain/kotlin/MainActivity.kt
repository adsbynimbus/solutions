package adsbynimbus.solutions.compose.app

import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.*
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adsbynimbus.solutions.kmm.context = applicationContext
        setContent {
            App(screenHeight = LocalConfiguration.current.screenHeightDp)
        }
    }
}

@Preview @Composable
fun AppPreview() {
    App(screenHeight = 480)
}
