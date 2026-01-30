package adsbynimbus.solutions.compose

import android.os.*

actual val platform: String get() = "Android ${Build.VERSION.RELEASE}"
