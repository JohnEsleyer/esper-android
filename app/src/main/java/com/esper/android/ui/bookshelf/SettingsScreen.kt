package com.esper.android.ui.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.remote.RemoteClient
import com.esper.android.ui.components.LocalReadOnlyMode
import com.esper.android.ui.components.OfflineNoticeBanner
import com.esper.android.ui.theme.*
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: FirebaseUser?,
    currentDisplayName: String,
    memberType: String? = null,
    maxNotebooks: Int? = null,
    maxShelves: Int? = null,
    // Null = unknown (offline/loading) or admin-immune; resolved below.
    inactivityDays: Long? = null,
    notebookCount: Int,
    shelfCount: Int,
    lastSyncError: String? = null,
    apiBaseUrl: String = RemoteClient.DEFAULT_BASE_URL,
    onApiBaseUrlChange: (String?) -> Unit = {},
    onUpdateDisplayName: (String) -> Unit,
    onExportZip: () -> Unit,
    onImportPackage: (Uri) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
    isOfflineMode: Boolean = false,
    onToggleOfflineMode: (Boolean) -> Unit = {},
    offlineVersionTimestamp: Long? = null,
    offlineVersionSummary: String? = null,
    isCreatingOfflineVersion: Boolean = false,
    onCreateOfflineVersion: () -> Unit = {},
    onLoadOfflineMode: () -> Unit = {}
) {
    val isReadOnly = LocalReadOnlyMode.current
    var nameInput by remember(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var isSavedNotice by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onImportPackage(uri)
    }

    Scaffold(
        topBar = {
            Column {
                if (isOfflineMode) {
                    OfflineNoticeBanner(
                        message = "Offline Mode Active — Using local offline data.",
                        onToggleOnline = { onToggleOfflineMode(false) }
                    )
                }
                TopAppBar(
                    title = { Text("Settings & Workspace", fontWeight = FontWeight.Bold, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SpaceBlack)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSlate,
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(IndigoAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (currentUser?.displayName?.firstOrNull() ?: currentUser?.email?.firstOrNull() ?: "E").toString().uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = currentUser?.displayName ?: "Esper User",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = currentUser?.email ?: "Offline mode enabled",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Change Display Name", fontSize = 11.sp, color = IndigoGlow, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                isSavedNotice = false
                            },
                            singleLine = true,
                            enabled = !isReadOnly,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (nameInput.isNotBlank()) {
                                    onUpdateDisplayName(nameInput.trim())
                                    isSavedNotice = true
                                }
                            },
                            enabled = !isReadOnly,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save")
                        }
                    }
                    if (isSavedNotice) {
                        Text("✓ Display name updated", color = EmeraldSync, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Offline Mode & Data Management Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSlate,
                border = BorderStroke(1.dp, if (isOfflineMode) AmberGlow.copy(alpha = 0.6f) else BorderSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Mode",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isOfflineMode) {
                                    "Currently in Offline Mode (Working with local data)"
                                } else {
                                    "Currently in Online Mode (Cloud sync enabled)"
                                },
                                color = if (isOfflineMode) AmberGlow else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = onToggleOfflineMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AmberGlow,
                                checkedTrackColor = AmberGlow.copy(alpha = 0.35f),
                                uncheckedThumbColor = IndigoAccent,
                                uncheckedTrackColor = SurfaceSlate
                            )
                        )
                    }

                    HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f), thickness = 1.dp)

                    Text(
                        text = "Offline Data Version",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    val formattedOfflineDate = remember(offlineVersionTimestamp) {
                        offlineVersionTimestamp?.let {
                            try {
                                SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(it))
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    if (formattedOfflineDate != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceSlate.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, EmeraldSync.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSync,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Offline snapshot: $formattedOfflineDate",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!offlineVersionSummary.isNullOrBlank()) {
                                        Text(
                                            text = offlineVersionSummary,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No offline version created yet. Create an offline version to cache all your books and chapters for offline access.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCreateOfflineVersion,
                            enabled = !isCreatingOfflineVersion,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCreatingOfflineVersion) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Creating...", fontSize = 11.sp)
                            } else {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (offlineVersionTimestamp != null) "Update Offline Data" else "Create Offline Version",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onLoadOfflineMode,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGlow),
                            border = BorderStroke(1.dp, AmberGlow.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Offline Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSlate,
                border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Workspace Status", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndigoAccent.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = tierBadgeText(memberType, currentUser != null),
                                color = IndigoGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    // Live server quotas (GET /api/me). "…" while offline or still syncing.
                    Text("• Stored Notebooks: $notebookCount / ${maxNotebooks ?: "…"} limit", color = TextSecondary, fontSize = 12.sp)
                    Text("• Shelves: $shelfCount / ${maxShelves ?: "…"} limit", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = retentionText(memberType, inactivityDays),
                        color = AmberGlow,
                        fontSize = 11.sp
                    )
                    if (lastSyncError != null) {
                        Text(
                            "• Last sync failed: $lastSyncError",
                            color = RoseRuby,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSlate,
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data Management", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Button(
                        onClick = onExportZip,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSync),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export All Notebooks as ZIP")
                    }

                    OutlinedButton(
                        onClick = { importPicker.launch("*/*") },
                        enabled = !isReadOnly,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSync),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Book Package (.zip / .esper / .json)")
                    }

                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = !isReadOnly,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoGlow),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Cloud Sync")
                    }

                    Text(
                        text = "Base API: $apiBaseUrl",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    var endpointInput by remember(apiBaseUrl) { mutableStateOf(apiBaseUrl) }
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("Custom API endpoint (blank = default)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onApiBaseUrlChange(endpointInput.ifBlank { null }) },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                endpointInput = RemoteClient.DEFAULT_BASE_URL
                                onApiBaseUrlChange(null)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Changing the endpoint rebuilds the API client; restart the app to reconnect SSE.",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseRuby),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RoseRuby.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (currentUser != null) "Sign Out" else "Exit Offline Mode")
            }
        }
    }
}
