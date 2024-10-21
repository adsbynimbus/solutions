package adsbynimbus.solutions.app

import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.window.ComposeUIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun mainViewController() = ComposeUIViewController {
    App(screenHeight = LocalWindowInfo.current.containerSize.height)
}
