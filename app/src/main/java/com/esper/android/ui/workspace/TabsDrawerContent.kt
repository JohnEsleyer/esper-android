package com.esper.android.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.NotebookEntity
import com.esper.android.ui.theme.*

@Composable
fun TabsDrawerContent(
    openNotebooks: List<NotebookEntity>,
    activeNotebookId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAllTabs: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = CosmicSlate,
        drawerContentColor = TextPrimary,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Tabs (${openNotebooks.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }
                if (openNotebooks.isNotEmpty()) {
                    TextButton(
                        onClick = onCloseAllTabs,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Close All", color = RoseRuby, fontSize = 11.sp)
                    }
                }
            }

            Text(
                text = "Switch between recently opened notebooks",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(color = BorderSlate, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            if (openNotebooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tabs open.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(openNotebooks, key = { it.id }) { notebook ->
                        val isActive = notebook.id == activeNotebookId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) Color(0xFF312E81) else SurfaceSlate.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isActive) IndigoGlow else BorderSlate
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTab(notebook.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notebook.title,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = notebook.tagsCsv.split(",").firstOrNull()?.trim()?.ifBlank { "General" } ?: "General",
                                        color = if (isActive) IndigoGlow else TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onCloseTab(notebook.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = TextMuted,
                                        modifier = Modifier.size(15.dp)
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
