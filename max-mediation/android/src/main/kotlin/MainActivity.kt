@file:OptIn(ExperimentalMaterial3Api::class)

package adsbynimbus.solutions.mediation.max

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NimbusTheme {
                App(this)
            }
        }
    }
}

enum class AdTypes(val title: String, val screen: @Composable () -> Unit = {}) {
    AdViewBanner("Banner", screen = ::BannerAdScreen),
    AdViewBannerWithVideo("Banner w/ Video", screen = ::BannerVideoScreen),
    Interstitial("Interstitial", screen = ::InterstitialScreen),
    RewardedVideo("Rewarded Video", screen = ::RewardedAdScreen),
}

@Composable
fun App(
    activity: ComponentActivity,
    appName: String = activity.applicationInfo.loadLabel(activity.packageManager).toString(),
    navController: NavHostController = rememberNavController(),
) {

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = backStackEntry?.destination?.route ?: appName

    Scaffold(
        topBar = {
            AppBar(
                currentScreen = currentScreen,
                canNavigateBack = navController.previousBackStackEntry != null,
                navigateUp = navController::navigateUp,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = appName,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            composable(route = appName) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AdTypes.entries.forEach {
                        Button(
                            onClick = { navController.navigate(route = it.title) },
                            modifier = Modifier.fillMaxWidth(fraction = 0.75f),
                            content = { Text(it.title) },
                        )
                    }
                }
            }
            AdTypes.entries.forEach { adType ->
                composable(route = adType.title) { adType.screen() }
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
