package com.esper.android.ui.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.NotebookEntity
import com.esper.android.ui.theme.*

@Composable
fun AllTagsDialog(
    allTags: List<String>,
    notebooks: List<NotebookEntity>,
    selectedTag: String,
    onSelectTag: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (oldTag: String, newTag: String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    var editingTag by remember { mutableStateOf<String?>(null) }
    var renameBuffer by remember { mutableStateOf("") }

    val tagCounts = remember(allTags, notebooks) {
        allTags.associateWith { tag ->
            if (tag == "All") notebooks.size
            else notebooks.count { it.tagsCsv.split(",").map { t -> t.trim().lowercase() }.contains(tag.lowercase()) }
        }
    }

    val filteredTags = remember(allTags, searchQuery) {
        if (searchQuery.isBlank()) allTags
        else allTags.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = IndigoGlow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Tags & Categories", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter tags...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text("New tag name...", fontSize = 12.sp, color = TextMuted) },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newTagInput.isNotBlank()) {
                                onCreateTag(newTagInput.trim())
                                newTagInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Add")
                    }
                }

                HorizontalDivider(color = BorderSlate, thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTags) { tag ->
                        val isSelected = tag == selectedTag
                        val count = tagCounts[tag] ?: 0

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF312E81) else SurfaceSlate.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTag(tag)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Tag, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (editingTag == tag) {
                                        OutlinedTextField(
                                            value = renameBuffer,
                                            onValueChange = { renameBuffer = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (renameBuffer.isNotBlank() && renameBuffer != tag) {
                                                    onRenameTag(tag, renameBuffer.trim())
                                                }
                                                editingTag = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Save", tint = EmeraldSync)
                                        }
                                    } else {
                                        Text(tag, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CosmicSlate
                                    ) {
                                        Text(
                                            text = "$count",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (tag != "All" && editingTag != tag) {
                                        IconButton(
                                            onClick = {
                                                editingTag = tag
                                                renameBuffer = tag
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextMuted, modifier = Modifier.size(13.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteTag(tag) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseRuby, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)
            ) {
                Text("Done")
            }
        },
        containerColor = CosmicSlate
    )
}
