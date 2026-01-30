package adsbynimbus.solutions.compose

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.*

@Suppress("unused")
fun mainViewController() = ComposeUIViewController { App() }

actual val platform: String = UIDevice.currentDevice.systemName()
