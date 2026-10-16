package com.esper.android.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Stable
class YouTubePlayerHostState {
    var playerInstance by mutableStateOf<YouTubePlayer?>(null)
        internal set

    var isReady by mutableStateOf(false)
        internal set

    fun seekTo(seconds: Int) {
        playerInstance?.seekTo(seconds.toFloat())
        playerInstance?.play()
    }

    fun play() {
        playerInstance?.play()
    }

    fun pause() {
        playerInstance?.pause()
    }

    fun loadVideo(videoId: String) {
        playerInstance?.loadVideo(videoId, 0f)
    }

    fun cueVideo(videoId: String) {
        playerInstance?.cueVideo(videoId, 0f)
    }
}

@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    hostState: YouTubePlayerHostState? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = hostState ?: remember { YouTubePlayerHostState() }
    val currentVideoId = rememberUpdatedState(videoId)

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)

            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    state.playerInstance = youTubePlayer
                    state.isReady = true
                    val id = currentVideoId.value
                    if (id.isNotBlank()) {
                        youTubePlayer.cueVideo(id, 0f)
                    }
                }

                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    playerState: PlayerConstants.PlayerState
                ) {
                    if (playerState == PlayerConstants.PlayerState.PAUSED) {
                        // handle paused state if needed
                    }
                }

                override fun onError(
                    youTubePlayer: YouTubePlayer,
                    error: PlayerConstants.PlayerError
                ) {
                    // Falls back gracefully if video fails
                }
            })
        }
    }

    LaunchedEffect(videoId, state.isReady) {
        if (state.isReady && videoId.isNotBlank()) {
            state.cueVideo(videoId)
        }
    }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
            state.playerInstance = null
            state.isReady = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
