package adsbynimbus.solutions.app

import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.*

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController() = ComposeUIViewController {
    App(screenHeight = LocalWindowInfo.current.containerSize.height)
}
