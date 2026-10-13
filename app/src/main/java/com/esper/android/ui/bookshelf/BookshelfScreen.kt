package com.esper.android.ui.bookshelf

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    notebooks: List<NotebookEntity>,
    shelves: List<ShelfEntity>,
    allDetails: List<NotebookWithDetails>,
    currentUser: FirebaseUser?,
    onSignOut: () -> Unit,
    onOpenNotebook: (String) -> Unit,
    onCreateNotebook: (title: String, description: String, tags: String, preset: String, coverImage: String?) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    onCreateShelf: (title: String, description: String, color: String) -> Unit,
    onDeleteShelf: (String) -> Unit,
    onSyncWithRemote: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var showCreateBookDialog by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }

    val isSignedIn = currentUser != null

    val allTags = remember(notebooks) {
        val extracted = notebooks.flatMap { it.tagsCsv.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        listOf("All") + extracted
    }

    val filtered = notebooks.filter { book ->
        val tagMatches = selectedTag == "All" || book.tagsCsv.contains(selectedTag, ignoreCase = true)
        val searchMatches = searchQuery.isBlank() || book.title.contains(searchQuery, ignoreCase = true)
        val shelfMatches = selectedShelfId == null || shelves.find { it.id == selectedShelfId }?.notebookIdsCsv?.contains(book.id) == true
        tagMatches && searchMatches && shelfMatches
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CosmicSlate,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(320.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSignedIn) IndigoAccent else TextMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSignedIn) {
                                    (currentUser?.displayName?.firstOrNull() ?: currentUser?.email?.firstOrNull() ?: "E").toString().uppercase()
                                } else "OF",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: currentUser?.email ?: "Offline Workspace",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isSignedIn) "Live Cloud Sync Active" else "Local-only storage",
                                color = if (isSignedIn) EmeraldSync else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = BorderSlate, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shelves & Collections (${shelves.size})",
                            color = IndigoGlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        IconButton(onClick = { showCreateShelfDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Shelf", tint = IndigoAccent)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedShelfId == null) SurfaceSlate else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedShelfId = null
                                        coroutineScope.launch { drawerState.close() }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("All Notebooks", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        items(shelves) { s ->
                            val isSelected = s.id == selectedShelfId
                            val shelfColor = try {
                                Color(android.graphics.Color.parseColor(s.color))
                            } catch (_: Exception) {
                                IndigoAccent
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SurfaceSlate else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedShelfId = s.id
                                        coroutineScope.launch { drawerState.close() }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(shelfColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(s.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            if (s.description.isNotBlank()) {
                                                Text(s.description, color = TextMuted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteShelf(s.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseRuby, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSlate, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = RoseRuby) },
                        label = { Text(if (isSignedIn) "Sign out" else "Exit Offline Mode", color = RoseRuby) },
                        selected = false,
                        onClick = onSignOut,
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Esper", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSignedIn) IndigoAccent.copy(alpha = 0.2f) else SurfaceSlate
                            ) {
                                Text(
                                    text = if (isSignedIn) "Live Cloud" else "Offline",
                                    color = if (isSignedIn) IndigoGlow else TextMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Shelves Menu", tint = TextPrimary)
                        }
                    },
                    actions = {
                        if (isSignedIn) {
                            IconButton(onClick = onSyncWithRemote) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync Cloud", tint = IndigoAccent)
                            }
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
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

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

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
    }

    if (showCreateBookDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }
        var newTags by remember { mutableStateOf("") }
        var newCoverImageUrl by remember { mutableStateOf("") }
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
                    OutlinedTextField(
                        value = newCoverImageUrl,
                        onValueChange = { newCoverImageUrl = it },
                        label = { Text("Book Cover Image URL (optional)") },
                        placeholder = { Text("https://example.com/cover.jpg") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("celestial", "quantum", "solar").forEach { p ->
                            FilterChip(
                                selected = selectedPreset == p,
                                onClick = { selectedPreset = p },
                                label = { Text(p.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onCreateNotebook(
                                newTitle.trim(),
                                newDesc.trim(),
                                newTags.trim(),
                                selectedPreset,
                                newCoverImageUrl.trim().ifBlank { null }
                            )
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

    if (showCreateShelfDialog) {
        var shelfTitle by remember { mutableStateOf("") }
        var shelfDesc by remember { mutableStateOf("") }
        var selectedColor by remember { mutableStateOf("#6366F1") }

        AlertDialog(
            onDismissRequest = { showCreateShelfDialog = false },
            title = { Text("Create Shelf Collection", color = TextPrimary) },
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
                    Text("Color Code:", fontSize = 12.sp, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#6366F1", "#10B981", "#F59E0B", "#F43F5E", "#8B5CF6", "#06B6D4").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (shelfTitle.isNotBlank()) {
                            onCreateShelf(shelfTitle.trim(), shelfDesc.trim(), selectedColor)
                            showCreateShelfDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateShelfDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CosmicSlate
        )
    }
}
