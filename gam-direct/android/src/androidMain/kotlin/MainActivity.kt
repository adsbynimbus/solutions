package adsbynimbus.solutions.gamdirect

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).also { frame ->
            frame.fitsSystemWindows = true

        })
    }
}
