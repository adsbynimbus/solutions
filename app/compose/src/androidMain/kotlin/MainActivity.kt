package adsbynimbus.solutions.app

import adsbynimbus.solutions.app.mediators.*
import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.startup.*
import com.adsbynimbus.*
import com.google.android.gms.ads.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App {
                AdManagerView(
                    adUnit = BuildConfig.adManagerBannerId,
                    adSizes = listOf(AdSize.MEDIUM_RECTANGLE)
                )
            }
        }
    }
}

class NimbusInitializer : Initializer<Unit> {
    override fun create(context: Context) = with(Nimbus) {
        initialize(context, BuildConfig.nimbusPublisherKey, BuildConfig.nimbusApiKey)
        testMode = true
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}

actual val platform: String get() = "Android ${Build.VERSION.RELEASE}"

@Preview @Composable
fun AppPreview() {
    App()
}
