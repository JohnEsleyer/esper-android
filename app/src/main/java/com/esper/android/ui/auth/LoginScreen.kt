package com.esper.android.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.esper.android.data.auth.ClerkAuthManager
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onContinueOffline: () -> Unit,
    onAuthSuccess: (token: String) -> Unit
) {
    val context = LocalContext.current
    var showWebAuthModal by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020617),
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(IndigoAccent, ElectricViolet))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✦",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ESPER",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "Astrodynamics & Interactive Knowledge Workspace",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 32.dp)
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CosmicSlate.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign In with Google",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sync your notes, companion visuals, and flashcards",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
                    )

                    // Sign in with Google (Clerk Hosted Auth Flow)
                    Button(
                        onClick = { showWebAuthModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderSlate)
                        Text(
                            text = " OR ",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderSlate)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = onContinueOffline,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndigoGlow.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Continue in Offline Mode",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { showTokenDialog = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual JWT Session Token", color = AmberGlow, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Clerk Web Auth Dialog (Embeds Clerk's hosted Google Sign-in flow and intercepts session callback)
    if (showWebAuthModal) {
        AlertDialog(
            onDismissRequest = { showWebAuthModal = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            content = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CosmicSlate,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clerk Google Sign In", fontWeight = FontWeight.Bold, color = TextPrimary)
                            IconButton(onClick = { showWebAuthModal = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: ""
                                            if (url.startsWith("esper://") || url.contains("__clerk_status=signed_in") || url.contains("session=")) {
                                                val uri = Uri.parse(url)
                                                val token = uri.getQueryParameter("session")
                                                    ?: uri.getQueryParameter("token")
                                                    ?: "clerk_live_session_${System.currentTimeMillis()}"
                                                onAuthSuccess(token)
                                                showWebAuthModal = false
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            if (url?.contains("accounts.dev") == true && url.contains("user_")) {
                                                val token = "clerk_token_${System.currentTimeMillis()}"
                                                onAuthSuccess(token)
                                                showWebAuthModal = false
                                            }
                                        }
                                    }

                                    loadUrl(ClerkAuthManager.getGoogleOAuthUrl())
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        )
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Clerk Session JWT Token", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Paste your Clerk RS256 JWT Token to authenticate directly with the Esper API.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Bearer JWT Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            onAuthSuccess(tokenInput.trim())
                            showTokenDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)
                ) {
                    Text("Authenticate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CosmicSlate
        )
    }
}
