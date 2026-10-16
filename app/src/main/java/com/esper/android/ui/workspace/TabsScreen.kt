package com.esper.android.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.NotebookEntity
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    openNotebooks: List<NotebookEntity>,
    activeNotebookId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAllTabs: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Tabs", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text("${openNotebooks.size} notebooks open", color = IndigoGlow, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (openNotebooks.isNotEmpty()) {
                        TextButton(onClick = onCloseAllTabs) {
                            Text("Close All", color = RoseRuby, fontWeight = FontWeight.Bold)
                        }
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
                .padding(16.dp)
        ) {
            if (openNotebooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No tabs are currently open", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(openNotebooks, key = { it.id }) { notebook ->
                        val isActive = notebook.id == activeNotebookId
                        val gradient = when (notebook.coverPreset) {
                            "quantum" -> listOf(Color(0xFF064E3B), Color(0xFF134E4A))
                            "solar" -> listOf(Color(0xFF78350F), Color(0xFF451A03))
                            else -> listOf(Color(0xFF1E1B4B), Color(0xFF2E1065))
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) Color(0xFF312E81).copy(alpha = 0.5f) else CosmicSlate,
                            border = BorderStroke(1.dp, if (isActive) IndigoGlow else BorderSlate),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Switch tab and immediately transition to workspace
                                    onSelectTab(notebook.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Book miniature preview
                                    Box(
                                        modifier = Modifier
                                            .size(width = 32.dp, height = 44.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Brush.verticalGradient(gradient)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = notebook.title.take(1).uppercase(),
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = notebook.title,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(EmeraldSync)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = notebook.tagsCsv.ifBlank { "General" },
                                            color = if (isActive) IndigoGlow else TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onCloseTab(notebook.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
