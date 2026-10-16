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
import com.esper.android.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: FirebaseUser?,
    currentDisplayName: String,
    notebookCount: Int,
    shelfCount: Int,
    onUpdateDisplayName: (String) -> Unit,
    onExportZip: () -> Unit,
    onImportPackage: (Uri) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var nameInput by remember(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var isSavedNotice by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onImportPackage(uri)
    }

    Scaffold(
        topBar = {
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
                                text = if (currentUser != null) "CLOUD SYNC" else "OFFLINE LOCAL",
                                color = IndigoGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text("• Stored Notebooks: $notebookCount / 50 limit", color = TextSecondary, fontSize = 12.sp)
                    Text("• Shelves: $shelfCount / 10 limit", color = TextSecondary, fontSize = 12.sp)
                    Text("• Inactivity retention: 30 days cloud backup", color = AmberGlow, fontSize = 11.sp)
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoGlow),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Cloud Sync")
                    }

                    Text(
                        text = "Base API: ${RemoteClient.BASE_URL}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
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
