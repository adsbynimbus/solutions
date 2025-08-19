package com.adsbynimbus.android.omsdk

import android.graphics.Color
import android.graphics.Rect
import android.widget.FrameLayout
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbynimbus.render.*

@Composable
fun ListWithAdInsertion(cells: List<Any>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            cells.size,
            contentType = { it }
        ) {
            for (cell in cells)
                when (cell) {
                    is AdController -> Ad(cell)
                    else -> Text(text = cell.toString(), modifier = Modifier.fillMaxWidth().height(80.dp))
                }
        }
    }
}

@Composable
fun Ad(controller: AdController) {
    NimbusAdView(controller = controller)
}

@Composable
fun NimbusAdView(
    modifier: Modifier = Modifier,
    controller: AdController,
) {
    var exposureText by remember { mutableStateOf(0) }
    var rect by remember { mutableStateOf(Rect()) }
    Column {
        Text("$controller exposure = $exposureText; rect = $rect")
        AndroidView(
            modifier = modifier.fillMaxWidth().height(250.dp),
            factory = { context -> FrameLayout(context) },
            onReset = { it.adController = null },
            onRelease = { it.adController = null },
            update = {
                if (it.adController != controller) {
                    it.adController = controller.apply {
                        if (this is ExposureController) exposureChange = { e, r ->
                            exposureText = e
                            rect = r
                            Log.d("Exposure", "[$name] exposure = $e; rect = $r")
                        }
                    }
                    it.contentDescription = controller.toString()
                }
            },
        )
    }
}

class ExposureController(
    val name: String,
    val events: MutableList<String> = mutableListOf(),
    var exposureChange: (Int, Rect) -> Unit = { e, r -> },
) : AdController() {

    override fun destroy() {
        events.add("destroy")
    }

    override fun onClickDetected() {
        events.add("click")
    }

    override fun onExposureChanged(exposure: Int, visibleRect: Rect) {
        events.add("exposure = $exposure; rect = $visibleRect")
        exposureChange.invoke(exposure, visibleRect)
    }

    override fun onViewableChanged(isViewable: Boolean) {
        events.add("viewable = $isViewable")
    }

    override fun toString(): String = name
}

val items: List<Any> = buildList {
    addAll(0..20)
    add(4, ExposureController("One"))
  //  add(10, ExposureController("Two"))
  //  add(15, ExposureController("Three"))
}

@Preview @Composable
fun Preview() {
    ListWithAdInsertion(items)
}
