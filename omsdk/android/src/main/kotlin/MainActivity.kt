@file:OptIn(ExperimentalMaterial3Api::class)

package com.adsbynimbus.android.omsdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adsbynimbus.NimbusAdManager
import com.adsbynimbus.android.omsdk.AdTypes.*
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.request.NimbusRequest

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
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(currentScreen) },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        }
    )
}

enum class AdTypes(val screen: String) {
    DisplayInline("Display Ad HTML - Inline"),
    VideoInline("Video Ad Native - Inline"),
    DisplayInterstitial("Display Ad HTML - Interstitial"),
    VideoInterstitial("Video Ad Native - Interstitial"),
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
                navigateUp = { navController.navigateUp() }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = activity.appName,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            composable(route = activity.appName) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Ad Types")
                    Button(onClick = { navController.navigate(DisplayInline.screen) }) {
                        Text(DisplayInline.screen)
                    }
                    Button(onClick = { navController.navigate(VideoInline.screen) }) {
                        Text(VideoInline.screen)
                    }
                    Button(onClick = {
                        adManager.showBlockingAd(
                            request = NimbusRequest.forInterstitialAd(DisplayInterstitial.screen).apply {
                                request.imp[0].video = null
                            },
                            activity = activity,
                            listener = { }
                        )
                    }) {
                        Text(DisplayInterstitial.screen)
                    }
                    Button(onClick = {
                        adManager.showBlockingAd(
                            request = NimbusRequest.forInterstitialAd(VideoInterstitial.screen).apply {
                                request.imp[0].banner = null
                            },
                            activity = activity,
                            listener = { }
                        )
                    }) {
                        Text(VideoInterstitial.screen)
                    }
                    Text(
                        text = "If an ad does not appear when clicking a button Nimbus mimicked a no fill scenario, please back out and try again.",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            composable(route = DisplayInline.screen) {
                NimbusAd(
                    request = NimbusRequest.forBannerAd(DisplayInline.screen, Format.MREC),
                    adManager = adManager,
                    modifier = Modifier.padding(4.dp),
                )
            }
            composable(route = VideoInline.screen) {
                DoublePreroll(modifier = Modifier.padding(4.dp).width(320.dp).height(480.dp))
                /*NimbusAd(
                    request = NimbusRequest.forVideoAd(VideoInline.screen),
                    adManager = adManager,
                    modifier = Modifier.padding(4.dp).width(320.dp).height(480.dp),
                ) */
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
