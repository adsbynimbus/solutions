@file:OptIn(ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(UnstableApi::class)

package adsbynimbus.solutions.gamdirect

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.core.net.*
import androidx.lifecycle.compose.*
import androidx.media3.common.*
import androidx.media3.common.MediaItem.*
import androidx.media3.common.util.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.source.*
import androidx.media3.ui.compose.*
import com.adsbynimbus.request.*
import com.adsbynimbus.solutions.gamdirect.instream.*
import com.google.ads.interactivemedia.v3.api.*

val imaSettings = ImaSdkFactory.getInstance().createImaSdkSettings()

fun initializeIMASdk(context: Context) {
    ImaSdkFactory.getInstance().initialize(context, imaSettings)
}

fun initializePlayer(
    context: Context,
    adLoader: NimbusImaAdLoader,
    contentUrl: String,
    adTag: String,
): Player {
    val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
        // Set the NimbusImaAdLoader as the ad insertion for all player Uris
        .setLocalAdInsertionComponents({ _ -> adLoader }, { null })

    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    // Set the player on the NimbusImaAdLoader
    adLoader.setPlayer(player)

    // The Google tag requires nofb=1 and &cust_params=nimbus%3Ddirect appended to it
    val gamDirectAdTag = "$adTag&nofb=1&cust_params=nimbus%3Ddirect"

    player.setMediaItem(
        Builder()
            .setUri(contentUrl)
            .setAdsConfiguration(AdsConfiguration.Builder(gamDirectAdTag.toUri()).build())
            .build(),
    )
    player.playWhenReady = true
    player.prepare()
    return player
}


@Composable
fun ComposePlayerView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adLoader = remember {
        NimbusImaAdLoader.Builder(context)
            .setImaSdkSettings(imaSettings)
            .setNimbusRequest(NimbusRequest.forVideoAd("preroll"))
            .build()
    }
    var player by remember { mutableStateOf<Player?>(null) }

    LifecycleStartEffect(Unit) {
        player = initializePlayer(
            context = context,
            adLoader = adLoader,
            contentUrl = "https://storage.googleapis.com/gvabox/media/samples/stock.mp4",
            adTag = BuildConfig.ADMANAGER_INSTREAM_TAG,
        )
        onStopOrDispose {
            player?.apply { release() }
            player = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ContentFrame(player = player, modifier = modifier.height(250.dp))
    }
}
