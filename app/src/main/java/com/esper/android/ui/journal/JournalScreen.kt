package com.esper.android.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.PageEntity
import com.esper.android.ui.components.ConfirmDeleteDialog
import com.esper.android.ui.components.LocalReadOnlyMode
import com.esper.android.ui.components.OfflineNoticeBanner
import com.esper.android.ui.lexical.ComposeLexicalEditor
import com.esper.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    pages: List<PageEntity>,
    onSavePageNotes: (pageId: String, notes: String) -> Unit,
    onCreateNewPage: (title: String) -> Unit,
    onDeletePage: (pageId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isReadOnly = LocalReadOnlyMode.current
    BackHandler { onNavigateBack() }

    // Always open directly on the latest created page; keep the user's place
    // across syncs — a remote re-emission must not teleport the entry index.
    var activePageIndex by rememberSaveable {
        mutableIntStateOf((pages.size - 1).coerceAtLeast(0))
    }
    // Clamp when entries are added/removed from another device.
    LaunchedEffect(pages.size) {
        if (pages.isEmpty()) activePageIndex = 0
        else if (activePageIndex >= pages.size) activePageIndex = pages.size - 1
    }

    val activePage = pages.getOrNull(activePageIndex)
    var showDeleteEntryConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                if (isReadOnly) {
                    OfflineNoticeBanner()
                }
                TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                tint = IndigoGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Personal Journal",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = activePage?.title ?: "Quick dumping ground",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (!isReadOnly) {
                        IconButton(onClick = {
                            val dateTitle = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()).format(Date())
                            onCreateNewPage(dateTitle)
                            // Jump to the newly appended latest entry.
                            activePageIndex = pages.size
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "New Entry", tint = IndigoAccent)
                        }
                        if (pages.size > 1 && activePage != null) {
                            IconButton(onClick = { showDeleteEntryConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Entry", tint = RoseRuby)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicSlate)
                )
            }
        },
        // Thumb-reachable page stepper: Previous / counter / Next.
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    color = CosmicSlate,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (activePageIndex > 0) activePageIndex-- },
                            enabled = activePageIndex > 0,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SurfaceSlate,
                                disabledContainerColor = CosmicSlate
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous Entry",
                                tint = if (activePageIndex > 0) TextPrimary else TextMuted
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SpaceBlack,
                            border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Entry ${activePageIndex + 1} of ${pages.size}",
                                color = IndigoGlow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { if (activePageIndex < pages.size - 1) activePageIndex++ },
                            enabled = activePageIndex < pages.size - 1,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SurfaceSlate,
                                disabledContainerColor = CosmicSlate
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next Entry",
                                tint = if (activePageIndex < pages.size - 1) TextPrimary else TextMuted
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SpaceBlack)
        ) {
            if (activePage != null) {
                key(activePage.id) {
                    ComposeLexicalEditor(
                        initialMarkdown = activePage.notes,
                        onContentChanged = { updatedNotes ->
                            onSavePageNotes(activePage.id, updatedNotes)
                        },
                        onTimestampClicked = {},
                        modifier = Modifier.fillMaxSize(),
                        readOnly = isReadOnly,
                        tapToEditAnywhere = true
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your journal is empty. Tap + to begin.", color = TextMuted)
                }
            }
        }
    }

    if (showDeleteEntryConfirm && activePage != null) {
        ConfirmDeleteDialog(
            title = "Delete Journal Entry",
            message = "Are you sure you want to delete entry \u201C${activePage.title}\u201D?",
            onConfirm = {
                val idToDelete = activePage.id
                activePageIndex = (activePageIndex - 1).coerceAtLeast(0)
                onDeletePage(idToDelete)
                showDeleteEntryConfirm = false
            },
            onDismiss = { showDeleteEntryConfirm = false }
        )
    }
}
