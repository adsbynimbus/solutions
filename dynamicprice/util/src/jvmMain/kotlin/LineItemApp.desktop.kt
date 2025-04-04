package adsbynimbus.solutions.dynamicprice.util

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(),
    ) {
        LineItemApp(getDatabaseBuilder())
    }
}

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> = Room.databaseBuilder<AppDatabase>(
    name = File(System.getProperty("java.io.tmpdir"), "admanager.db").absolutePath,
)

@Composable @Preview
fun LineItemAppPreview() = LineItemApp(getDatabaseBuilder())
