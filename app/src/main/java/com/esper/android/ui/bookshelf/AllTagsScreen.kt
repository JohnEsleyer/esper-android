package com.esper.android.ui.bookshelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.NotebookEntity
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTagsScreen(
    allTags: List<String>,
    notebooks: List<NotebookEntity>,
    selectedTag: String,
    onSelectTag: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (oldTag: String, newTag: String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onNavigateBack: () -> Unit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tags & Categories", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${allTags.size - 1} categories active", color = IndigoGlow, fontSize = 11.sp)
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CosmicSlate,
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text("Filter tags...", color = TextMuted, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(IndigoAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceSlate.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (newTagInput.isEmpty()) {
                            Text("Create new category tag...", color = TextMuted, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(IndigoAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = {
                            if (newTagInput.isNotBlank()) {
                                onCreateTag(newTagInput.trim())
                                newTagInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("All Categories", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTags) { tag ->
                    val isSelected = tag == selectedTag
                    val count = tagCounts[tag] ?: 0

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF312E81) else CosmicSlate,
                        border = BorderStroke(1.dp, if (isSelected) IndigoGlow else BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Tag, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                if (editingTag == tag) {
                                    OutlinedTextField(
                                        value = renameBuffer,
                                        onValueChange = { renameBuffer = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = {
                                            if (renameBuffer.isNotBlank() && renameBuffer != tag) {
                                                onRenameTag(tag, renameBuffer.trim())
                                            }
                                            editingTag = null
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Save", tint = EmeraldSync)
                                    }
                                } else {
                                    Text(
                                        tag,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceSlate,
                                    onClick = {
                                        onSelectTag(tag)
                                        onNavigateBack()
                                    }
                                ) {
                                    Text(
                                        text = "$count books",
                                        color = IndigoGlow,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                if (tag != "All" && editingTag != tag) {
                                    IconButton(
                                        onClick = {
                                            editingTag = tag
                                            renameBuffer = tag
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextMuted, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteTag(tag) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseRuby, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
