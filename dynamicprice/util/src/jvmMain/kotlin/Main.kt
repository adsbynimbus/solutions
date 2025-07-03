fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1200.dp, height = 960.dp)
    ) {

    }
}
