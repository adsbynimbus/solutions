@file:OptIn(ExperimentalMaterial3Api::class)

package adsbynimbus.solutions.gamdirect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.adsbynimbus.NimbusAdManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NimbusTheme {
                App(this)
            }
        }
    }
}


@Composable
fun AppBar(
    currentScreen: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(currentScreen) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
    )
}

val ComponentActivity.appName get() = applicationInfo.loadLabel(packageManager).toString()

@Composable
fun App(
    activity: ComponentActivity,
    navController: NavHostController = rememberNavController(),
) {

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = backStackEntry?.destination?.route ?: activity.appName
    val adManager = remember { NimbusAdManager() }

    Scaffold(
        topBar = {
            AppBar(
                currentScreen = currentScreen,
                canNavigateBack = navController.previousBackStackEntry != null,
                navigateUp = { navController.navigateUp() },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = activity.appName,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            composable(route = activity.appName) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Ad Types")
                    Button(onClick = { navController.navigate("instream") }) {
                        Text("Instream Video")
                    }
                }
            }
            composable(route = "instream") {
                ComposePlayerView()
            }
        }
    }
}

@Composable
fun NimbusTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Color(0xFFDB6FA3),   /* Pink */
        secondary = Color(0xFF85D6DA), /* Teal */
        background = Color.Black,
        surface = Color.Black,
    ),
    content = content,
)

