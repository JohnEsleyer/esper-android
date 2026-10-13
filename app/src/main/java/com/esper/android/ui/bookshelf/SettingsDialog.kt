package com.esper.android.ui.bookshelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.remote.RemoteClient
import com.esper.android.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsDialog(
    currentUser: FirebaseUser?,
    currentDisplayName: String,
    notebookCount: Int,
    shelfCount: Int,
    onUpdateDisplayName: (String) -> Unit,
    onExportZip: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var isSavedNotice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings & Workspace", fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SpaceBlack,
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Display Name", fontSize = 11.sp, color = IndigoGlow, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = {
                                    nameInput = it
                                    isSavedNotice = false
                                },
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
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)
                            ) {
                                Text("Save")
                            }
                        }
                        if (isSavedNotice) {
                            Text("✓ Name updated", color = EmeraldSync, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IndigoAccent.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Member Plan:", color = TextSecondary, fontSize = 12.sp)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = IndigoAccent.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = if (currentUser != null) "VISITOR PLAN" else "OFFLINE LOCAL",
                                    color = IndigoGlow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Notebooks used: $notebookCount / 50 max", color = TextPrimary, fontSize = 12.sp)
                        Text("• Shelves used: $shelfCount / 10 max", color = TextPrimary, fontSize = 12.sp)
                        Text("• Inactivity retention: 30 days", color = AmberGlow, fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onExportZip,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSync),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export All Notebooks as ZIP")
                }

                Text(
                    text = "API: ${RemoteClient.BASE_URL}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseRuby),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (currentUser != null) "Sign Out" else "Exit Offline Mode")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceSlate)
            ) {
                Text("Close")
            }
        },
        containerColor = CosmicSlate
    )
}
