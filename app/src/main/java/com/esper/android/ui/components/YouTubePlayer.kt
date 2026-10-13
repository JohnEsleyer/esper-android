package com.esper.android.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface YouTubePlayerState {
    object Idle : YouTubePlayerState
    object Ready : YouTubePlayerState
    object Playing : YouTubePlayerState
    object Paused : YouTubePlayerState
    data class Error(val message: String) : YouTubePlayerState
}

@Stable
class YouTubePlayerHostState {
    var currentState by mutableStateOf<YouTubePlayerState>(YouTubePlayerState.Idle)
        internal set

    private var webViewRef: WebView? = null

    internal fun attachWebView(webView: WebView) {
        webViewRef = webView
    }

    internal fun detachWebView() {
        webViewRef = null
    }

    fun loadVideo(videoId: String) {
        webViewRef?.post {
            webViewRef?.evaluateJavascript("if (window.loadVideo) { window.loadVideo('$videoId'); }", null)
        }
    }

    fun play() {
        webViewRef?.post {
            webViewRef?.evaluateJavascript("if (window.playVideo) { window.playVideo(); }", null)
        }
    }

    fun pause() {
        webViewRef?.post {
            webViewRef?.evaluateJavascript("if (window.pauseVideo) { window.pauseVideo(); }", null)
        }
    }

    fun seekTo(seconds: Int) {
        webViewRef?.post {
            webViewRef?.evaluateJavascript("if (window.seekTo) { window.seekTo($seconds); }", null)
        }
    }
}

private class YouTubeJsBridge(
    private val hostState: YouTubePlayerHostState,
    private val coroutineScope: CoroutineScope
) {
    @JavascriptInterface
    fun onReady() {
        coroutineScope.launch(Dispatchers.Main) {
            hostState.currentState = YouTubePlayerState.Ready
        }
    }

    @JavascriptInterface
    fun onStateChange(state: Int) {
        coroutineScope.launch(Dispatchers.Main) {
            hostState.currentState = when (state) {
                1 -> YouTubePlayerState.Playing
                2 -> YouTubePlayerState.Paused
                else -> hostState.currentState
            }
        }
    }

    @JavascriptInterface
    fun onError(errorCode: Int) {
        coroutineScope.launch(Dispatchers.Main) {
            hostState.currentState = YouTubePlayerState.Error("YouTube Player Error code: $errorCode")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    hostState: YouTubePlayerHostState? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val state = hostState ?: remember { YouTubePlayerHostState() }

    val htmlContent = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
            #player { width: 100%; height: 100%; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%',
                height: '100%',
                videoId: '$videoId',
                playerVars: {
                  'autoplay': 1,
                  'playsinline': 1,
                  'enablejsapi': 1,
                  'rel': 0,
                  'origin': 'https://www.youtube.com',
                  'widget_referrer': 'https://www.youtube.com'
                },
                events: {
                  'onReady': onPlayerReady,
                  'onError': onPlayerError,
                  'onStateChange': onPlayerStateChange
                }
              });
            }

            function onPlayerReady(event) {
              if (window.EsperYouTubeBridge) {
                window.EsperYouTubeBridge.onReady();
              }
            }

            function onPlayerError(event) {
              if (window.EsperYouTubeBridge) {
                window.EsperYouTubeBridge.onError(event.data);
              }
            }

            function onPlayerStateChange(event) {
              if (window.EsperYouTubeBridge) {
                window.EsperYouTubeBridge.onStateChange(event.data);
              }
            }

            window.seekTo = function(seconds) {
              if (player && player.seekTo) {
                player.seekTo(seconds, true);
                player.playVideo();
              }
            };

            window.loadVideo = function(videoId) {
              if (player && player.loadVideoById) {
                player.loadVideoById(videoId);
              }
            };

            window.playVideo = function() {
              if (player && player.playVideo) {
                player.playVideo();
              }
            };

            window.pauseVideo = function() {
              if (player && player.pauseVideo) {
                player.pauseVideo();
              }
            };
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mediaPlaybackRequiresUserGesture = false

                        val originalUa = userAgentString
                        userAgentString = originalUa.replace("; wv", "")
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()

                    addJavascriptInterface(
                        YouTubeJsBridge(state, coroutineScope),
                        "EsperYouTubeBridge"
                    )

                    state.attachWebView(this)

                    loadDataWithBaseURL(
                        "https://www.youtube.com",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                state.attachWebView(webView)
            },
            onRelease = {
                state.detachWebView()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
