@file:OptIn(ExperimentalMaterial3Api::class)

package com.adsbynimbus.android.omsdk

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.adsbynimbus.AdSize
import com.adsbynimbus.Nimbus
import com.adsbynimbus.android.omsdk.AdTypes.*
import com.adsbynimbus.banner
import com.adsbynimbus.video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    scope: CoroutineScope = rememberCoroutineScope(),
) {

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = backStackEntry?.destination?.route ?: activity.appName

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
                    Button(onClick = { navController.navigate(DisplayInline.screen) }) {
                        Text(DisplayInline.screen)
                    }
                    Button(onClick = { navController.navigate(VideoInline.screen) }) {
                        Text(VideoInline.screen)
                    }
                    Button(onClick = {
                        scope.launch {
                            Nimbus.fullscreenAd(DisplayInterstitial.screen) {
                                banner(AdSize.InterstitialPortrait)
                            }.show(from = activity)
                        }
                    }) {
                        Text(DisplayInterstitial.screen)
                    }
                    Button(onClick = {
                        scope.launch {
                            Nimbus.fullscreenAd(VideoInterstitial.screen) {
                                video()
                            }.show(from = activity)
                        }
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
                    ad = Nimbus.bannerAd(DisplayInline.screen, AdSize.Mrec),
                    modifier = Modifier.padding(4.dp),
                )
            }
            composable(route = VideoInline.screen) {
                NimbusAd(
                    ad = Nimbus.inlineAd(VideoInline.screen) { video() },
                    modifier = Modifier.padding(4.dp).width(320.dp).height(480.dp),
                )
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
