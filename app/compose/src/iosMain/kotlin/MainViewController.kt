package adsbynimbus.solutions.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.*

fun MainViewController() = ComposeUIViewController { App() }

actual val platform: String = UIDevice.currentDevice.systemName()
