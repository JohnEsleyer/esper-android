package com.esper.android.ui.bookshelf

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.local.NotebookWithDetails
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.ShelfEntity
import com.esper.android.ui.components.BookCard
import com.esper.android.ui.components.PullToRefreshLayout
import com.esper.android.ui.theme.*
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    notebooks: List<NotebookEntity>,
    shelves: List<ShelfEntity>,
    allDetails: List<NotebookWithDetails>,
    currentUser: FirebaseUser?,
    selectedTagFilter: String,
    onSelectTagFilter: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenNotebook: (String) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    onOpenAllTagsScreen: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    onOpenCreateNotebookScreen: () -> Unit,
    onDeleteShelf: (String) -> Unit,
    onSyncWithRemote: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var searchQuery by remember { mutableStateOf("") }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val pageSize = 6
    var currentPage by remember { mutableIntStateOf(1) }
    var pageInputText by remember { mutableStateOf("1") }
    var isEditingPageNum by remember { mutableStateOf(false) }

    // Books are strictly ordered latest to oldest
    val sortedNotebooks = remember(notebooks) {
        notebooks.sortedByDescending { it.updatedAt }
    }

    val filtered = remember(sortedNotebooks, selectedTagFilter, searchQuery, selectedShelfId, shelves) {
        sortedNotebooks.filter { book ->
            val tagMatches = selectedTagFilter == "All" || book.tagsCsv.contains(selectedTagFilter, ignoreCase = true)
            val searchMatches = searchQuery.isBlank() || book.title.contains(searchQuery, ignoreCase = true)
            val shelfMatches = selectedShelfId == null || shelves.find { it.id == selectedShelfId }?.notebookIdsCsv?.contains(book.id) == true
            tagMatches && searchMatches && shelfMatches
        }
    }

    val totalPages = remember(filtered) {
        maxOf(1, ceil(filtered.size.toDouble() / pageSize).toInt())
    }

    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) {
            currentPage = totalPages
            pageInputText = totalPages.toString()
        }
    }

    LaunchedEffect(currentPage) {
        pageInputText = currentPage.toString()
    }

    val pagedBooks = remember(filtered, currentPage) {
        val startIndex = (currentPage - 1) * pageSize
        filtered.drop(startIndex).take(pageSize)
    }

    fun triggerRefresh() {
        if (!isRefreshing) {
            isRefreshing = true
            onSyncWithRemote()
            coroutineScope.launch {
                delay(900)
                isRefreshing = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CosmicSlate,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(310.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable {
                                onOpenSettingsScreen()
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
                                text = "Settings & Workspace",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = BorderSlate, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Shelves & Collections (${shelves.size})",
                        color = IndigoGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

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
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = SpaceBlack,
                            border = BorderStroke(1.dp, BorderSlate),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search notebooks, tags...",
                                            color = TextMuted,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            currentPage = 1
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        ),
                                        cursorBrush = SolidColor(IndigoAccent),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettingsScreen) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
                )
            },
            bottomBar = {
                Surface(
                    color = CosmicSlate,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (currentPage > 1) {
                                        currentPage--
                                    }
                                },
                                enabled = currentPage > 1,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = SurfaceSlate,
                                    disabledContainerColor = CosmicSlate
                                ),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = if (currentPage > 1) TextPrimary else TextMuted)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("Page", color = TextMuted, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SpaceBlack,
                                    border = BorderStroke(1.dp, if (isEditingPageNum) IndigoAccent else BorderSlate),
                                    modifier = Modifier.width(48.dp).height(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        BasicTextField(
                                            value = pageInputText,
                                            onValueChange = { input ->
                                                val filteredInput = input.filter { it.isDigit() }.take(3)
                                                pageInputText = filteredInput
                                                isEditingPageNum = true
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = IndigoGlow,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    val num = pageInputText.toIntOrNull() ?: 1
                                                    currentPage = num.coerceIn(1, totalPages)
                                                    pageInputText = currentPage.toString()
                                                    isEditingPageNum = false
                                                }
                                            ),
                                            cursorBrush = SolidColor(IndigoAccent),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))
                                Text("of $totalPages (${filtered.size} books)", color = TextMuted, fontSize = 12.sp)
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (currentPage < totalPages) {
                                        currentPage++
                                    }
                                },
                                enabled = currentPage < totalPages,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = SurfaceSlate,
                                    disabledContainerColor = CosmicSlate
                                ),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = if (currentPage < totalPages) TextPrimary else TextMuted)
                            }
                        }

                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f), thickness = 1.dp)

                        NavigationBar(
                            containerColor = CosmicSlate,
                            modifier = Modifier.height(60.dp)
                        ) {
                            NavigationBarItem(
                                selected = selectedTagFilter == "All",
                                onClick = { onSelectTagFilter("All") },
                                icon = { Icon(Icons.Default.Book, contentDescription = "Library", modifier = Modifier.size(20.dp)) },
                                label = { Text("Library", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrimary,
                                    indicatorColor = IndigoAccent,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )

                            NavigationBarItem(
                                selected = false,
                                onClick = onOpenAllTagsScreen,
                                icon = { Icon(Icons.Default.Tag, contentDescription = "Tags", modifier = Modifier.size(20.dp)) },
                                label = { Text("Tags", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )

                            NavigationBarItem(
                                selected = false,
                                onClick = onOpenCreateNotebookScreen,
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(IndigoAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "New Book", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                },
                                label = { Text("New Book", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    unselectedIconColor = IndigoAccent,
                                    unselectedTextColor = IndigoGlow
                                )
                            )

                            NavigationBarItem(
                                selected = false,
                                onClick = onOpenSettingsScreen,
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) },
                                label = { Text("Settings", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            PullToRefreshLayout(
                isRefreshing = isRefreshing,
                onRefresh = { triggerRefresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(SpaceBlack)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val quickTags = remember(notebooks) {
                        val extracted = notebooks.flatMap { it.tagsCsv.split(",") }
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .take(6)
                        listOf("All") + extracted
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Surface(
                                onClick = onOpenAllTagsScreen,
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
                                    Text("All Tags", color = IndigoGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        items(quickTags) { tag ->
                            val isSelected = tag == selectedTagFilter
                            Surface(
                                onClick = { onSelectTagFilter(tag) },
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

                    if (pagedBooks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No notebooks found", color = TextSecondary, fontSize = 14.sp)
                                Text("Pull down to refresh or tap + to create one", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pagedBooks, key = { it.id }) { book ->
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
        }
    }
}
