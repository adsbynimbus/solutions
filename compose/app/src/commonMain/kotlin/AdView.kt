package adsbynimbus.solutions.compose.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.*

@Composable
expect fun AdView(modifier: Modifier = Modifier, position: String, size: Pair<Int, Int>)
