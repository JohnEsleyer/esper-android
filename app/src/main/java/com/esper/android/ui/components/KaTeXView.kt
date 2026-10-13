package com.esper.android.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXMathView(
    latex: String,
    isDisplayMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }

    val sanitizedLatex = latex
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
          <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            html, body {
              background-color: transparent;
              color: #E0E7FF;
              font-size: 15px;
              display: flex;
              align-items: center;
              justify-content: ${if (isDisplayMode) "center" else "flex-start"};
              min-height: 100%;
              overflow: hidden;
            }
            #math { padding: 4px 6px; }
            .katex { font-size: 1.12em; color: #A5B4FC; }
          </style>
        </head>
        <body>
          <div id="math"></div>
          <script>
            window.addEventListener('DOMContentLoaded', () => {
              try {
                katex.render(`$sanitizedLatex`, document.getElementById('math'), {
                  displayMode: ${isDisplayMode},
                  throwOnError: false
                });
              } catch(e) {
                document.getElementById('math').innerText = `$sanitizedLatex`;
              }
            });
          </script>
        </body>
        </html>
    """.trimIndent()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF060B1A))
            .padding(vertical = if (isDisplayMode) 8.dp else 2.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setBackgroundColor(0)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
                }
            },
            update = { wv ->
                wv.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
            },
            modifier = Modifier.fillMaxWidth().height(if (isDisplayMode) 58.dp else 34.dp)
        )
    }
}
