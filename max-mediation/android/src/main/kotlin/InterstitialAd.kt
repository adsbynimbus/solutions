package adsbynimbus.solutions.mediation.max

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*

@Composable
fun InterstitialScreen(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    if (activity == null) {
        Text(modifier = modifier, text = "Unable to resolve Activity")
        return
    }
    LaunchedEffect(true) {

    }
}
