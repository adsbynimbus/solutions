@file:OptIn(ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(UnstableApi::class)

package adsbynimbus.solutions.gamdirect

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.*
import androidx.media3.common.MediaItem.AdsConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.*
import androidx.media3.exoplayer.source.ads.AdsLoader
import androidx.media3.ui.compose.ContentFrame
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory

const val contentUrl = "https://storage.googleapis.com/gvabox/media/samples/stock.mp4"
// The Google tag requires nofb=1 and &cust_params=nimbus%3Ddirect appended to it
const val directAdTag = BuildConfig.GAMDIRECT_INSTREAM_TAG + "&nofb=1" + "&cust_params=nimbus%3Ddirect"

val imaSettings = ImaSdkFactory.getInstance().createImaSdkSettings()

fun initializeIMASdk(context: Context) {
    ImaSdkFactory.getInstance().initialize(context, imaSettings)
}

fun initializePlayer(context: Context, adLoader: AdsLoader): Player =
    ExoPlayer.Builder(context).setMediaSourceFactory(
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
            .setLocalAdInsertionComponents({ _ -> adLoader }, { null }),
    ).build().apply {
        adLoader.setPlayer(this)

        setMediaItem(
            MediaItem.Builder()
                .setUri(contentUrl)
                .setAdsConfiguration(AdsConfiguration.Builder(directAdTag.toUri()).build())
                .build(),
        )
        playWhenReady = true
        prepare()
    }

@Composable
fun ComposePlayerView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adLoader = remember {
        ImaAdsLoader.Builder(context)
            .setImaSdkSettings(imaSettings)
            .build()
    }
    var player by remember { mutableStateOf<Player?>(null) }

    LifecycleStartEffect(Unit) {
        player = initializePlayer(context, adLoader)
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
