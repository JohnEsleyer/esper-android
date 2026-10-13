package com.esper.android.ui.bookshelf

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
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
    onSyncWithRemote: () -> Unit,
    onUpdateDisplayName: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var showCreateBookDialog by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showAllTagsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var totalDragY by remember { mutableFloatStateOf(0f) }
    var isSyncingAnimated by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isSyncingAnimated) 720f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "syncRotation"
    )

    var customTags by remember {
        mutableStateOf(
            listOf("Research", "Astrodynamics", "Physics", "Lectures", "Drafts")
        )
    }

    val allTags = remember(notebooks, customTags) {
        val extracted = notebooks.flatMap { it.tagsCsv.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        listOf("All") + (customTags + extracted).distinct()
    }

    val filtered = notebooks.filter { book ->
        val tagMatches = selectedTag == "All" || book.tagsCsv.contains(selectedTag, ignoreCase = true)
        val searchMatches = searchQuery.isBlank() || book.title.contains(searchQuery, ignoreCase = true)
        val shelfMatches = selectedShelfId == null || shelves.find { it.id == selectedShelfId }?.notebookIdsCsv?.contains(book.id) == true
        tagMatches && searchMatches && shelfMatches
    }

    fun triggerSwipeRefresh() {
        if (!isSyncingAnimated) {
            isSyncingAnimated = true
            onSyncWithRemote()
            coroutineScope.launch {
                delay(800)
                isSyncingAnimated = false
            }
        }
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
                            .clickable {
                                showSettingsDialog = true
                                coroutineScope.launch { drawerState.close() }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(IndigoAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (currentUser?.displayName?.firstOrNull() ?: currentUser?.email?.firstOrNull() ?: "U").toString().uppercase(),
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
                                text = "Tap for Settings & Profile",
                                color = TextMuted,
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
                        label = { Text("Sign Out / Exit", color = RoseRuby) },
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
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search notebooks, tags...", color = TextMuted, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SpaceBlack,
                                unfocusedContainerColor = SpaceBlack,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = IndigoAccent,
                                unfocusedBorderColor = BorderSlate
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(SpaceBlack)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragY += dragAmount.y
                                if (totalDragY < -180f) {
                                    triggerSwipeRefresh()
                                    totalDragY = 0f
                                }
                            },
                            onDragEnd = { totalDragY = 0f },
                            onDragCancel = { totalDragY = 0f }
                        )
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Surface(
                                onClick = { showAllTagsDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                color = SurfaceSlate,
                                border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tag, contentDescription = null, tint = IndigoGlow, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("All Tags (${allTags.size - 1})", color = IndigoGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(14.dp),
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

                if (isSyncingAnimated) {
                    Surface(
                        shape = CircleShape,
                        color = IndigoAccent,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 30.dp)
                            .size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Syncing",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAllTagsDialog) {
        AllTagsDialog(
            allTags = allTags,
            notebooks = notebooks,
            selectedTag = selectedTag,
            onSelectTag = { selectedTag = it },
            onCreateTag = { newTag ->
                if (!customTags.contains(newTag)) {
                    customTags = customTags + newTag
                }
            },
            onRenameTag = { oldTag, newTag ->
                customTags = customTags.map { if (it == oldTag) newTag else it }
                if (selectedTag == oldTag) selectedTag = newTag
            },
            onDeleteTag = { tag ->
                customTags = customTags.filter { it != tag }
                if (selectedTag == tag) selectedTag = "All"
            },
            onDismiss = { showAllTagsDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentUser = currentUser,
            currentDisplayName = currentUser?.displayName ?: "Esper User",
            notebookCount = notebooks.size,
            shelfCount = shelves.size,
            onUpdateDisplayName = { newName ->
                onUpdateDisplayName(newName)
                Toast.makeText(context, "Display name updated to: $newName", Toast.LENGTH_SHORT).show()
            },
            onExportZip = {
                val fileName = ZipExporter.exportToDownloads(context, allDetails)
                Toast.makeText(context, "Exported: $fileName", Toast.LENGTH_LONG).show()
            },
            onSignOut = onSignOut,
            onDismiss = { showSettingsDialog = false }
        )
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
