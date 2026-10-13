package com.esper.android.ui.bookshelf

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.export.ZipExporter
import com.esper.android.data.local.NotebookWithDetails
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.ShelfEntity
import com.esper.android.ui.components.BookCard
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    notebooks: List<NotebookEntity>,
    shelves: List<ShelfEntity>,
    allDetails: List<NotebookWithDetails>,
    onOpenNotebook: (String) -> Unit,
    onCreateNotebook: (title: String, description: String, tags: String, preset: String) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    onCreateShelf: (title: String, description: String) -> Unit,
    onDeleteShelf: (String) -> Unit,
    onSyncWithRemote: () -> Unit,
    onConfigureAuth: (token: String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }
    var isShelvesSheetOpen by remember { mutableStateOf(false) }
    var showCreateBookDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var authTokenInput by remember { mutableStateOf("") }

    // Dynamic Tag extraction
    val allTags = remember(notebooks) {
        val extracted = notebooks.flatMap { it.tagsCsv.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        listOf("All") + extracted
    }

    val filtered = notebooks.filter { book ->
        (selectedTag == "All" || book.tagsCsv.contains(selectedTag, ignoreCase = true)) &&
        (searchQuery.isBlank() || book.title.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Esper", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndigoAccent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "Live API",
                                color = IndigoGlow,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isShelvesSheetOpen = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Shelves", tint = IndigoGlow)
                    }
                    IconButton(onClick = onSyncWithRemote) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Cloud", tint = IndigoAccent)
                    }
                    IconButton(onClick = { showAuthDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Auth Token", tint = AmberGlow)
                    }
                    IconButton(onClick = {
                        val fileName = ZipExporter.exportToDownloads(context, allDetails)
                        Toast.makeText(context, "Exported: $fileName", Toast.LENGTH_LONG).show()
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export ZIP", tint = EmeraldSync)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateBookDialog = true },
                containerColor = IndigoAccent,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Book")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SpaceBlack)
        ) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notebooks, tags, topics...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CosmicSlate,
                    unfocusedContainerColor = CosmicSlate,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = IndigoAccent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Dynamic Category / Tag Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allTags) { tag ->
                    val isSelected = tag == selectedTag
                    Surface(
                        onClick = { selectedTag = tag },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) IndigoAccent else CosmicSlate
                    ) {
                        Text(
                            text = tag,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Bookshelf Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { book ->
                    BookCard(
                        notebook = book,
                        onClick = { onOpenNotebook(book.id) },
                        onDelete = { onDeleteNotebook(book.id) }
                    )
                }
            }
        }
    }

    // Create Notebook Dialog
    if (showCreateBookDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }
        var newTags by remember { mutableStateOf("") }
        var selectedPreset by remember { mutableStateOf("celestial") }

        AlertDialog(
            onDismissRequest = { showCreateBookDialog = false },
            title = { Text("Create New Notebook", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Notebook Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTags,
                        onValueChange = { newTags = it },
                        label = { Text("Tags (comma-separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("celestial", "quantum", "solar").forEach { p ->
                            FilterChip(
                                selected = selectedPreset == p,
                                onClick = { selectedPreset = p },
                                label = { Text(p.capitalize()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onCreateNotebook(newTitle.trim(), newDesc.trim(), newTags.trim(), selectedPreset)
                            showCreateBookDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBookDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CosmicSlate
        )
    }

    // Shelves Sheet (Create, List, Delete Shelves)
    if (isShelvesSheetOpen) {
        ShelvesSheet(
            shelves = shelves,
            onCreateShelf = onCreateShelf,
            onDeleteShelf = onDeleteShelf,
            onDismiss = { isShelvesSheetOpen = false }
        )
    }

    // Auth Token Dialog for Clerk Integration
    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = { Text("Clerk Session JWT Token", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Configures Bearer token for https://esper-api.johnesleyer.com/api/",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = authTokenInput,
                        onValueChange = { authTokenInput = it },
                        label = { Text("Paste JWT Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfigureAuth(authTokenInput.trim())
                        showAuthDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGlow)
                ) {
                    Text("Save Token")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CosmicSlate
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelvesSheet(
    shelves: List<ShelfEntity>,
    onCreateShelf: (title: String, desc: String) -> Unit,
    onDeleteShelf: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var shelfTitle by remember { mutableStateOf("") }
    var shelfDesc by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CosmicSlate,
        contentColor = TextPrimary
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shelves & Playlists (${shelves.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shelf", tint = IndigoGlow)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (shelves.isEmpty()) {
                Text("No shelves created yet.", color = TextMuted, fontSize = 13.sp)
            } else {
                shelves.forEach { s ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpaceBlack,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                if (s.description.isNotBlank()) {
                                    Text(s.description, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            IconButton(onClick = { onDeleteShelf(s.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseRuby)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create Shelf", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shelfTitle,
                        onValueChange = { shelfTitle = it },
                        label = { Text("Shelf Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shelfDesc,
                        onValueChange = { shelfDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (shelfTitle.isNotBlank()) {
                            onCreateShelf(shelfTitle.trim(), shelfDesc.trim())
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CosmicSlate
        )
    }
}
