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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.PageEntity
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
    BackHandler { onNavigateBack() }

    // Always open directly on the latest created page.
    var activePageIndex by remember(pages.size) {
        mutableIntStateOf((pages.size - 1).coerceAtLeast(0))
    }
    // Clamp when entries are added/removed from another device.
    LaunchedEffect(pages.size) {
        if (pages.isEmpty()) activePageIndex = 0
        else if (activePageIndex >= pages.size) activePageIndex = pages.size - 1
    }

    val activePage = pages.getOrNull(activePageIndex)

    Scaffold(
        topBar = {
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
                    IconButton(onClick = {
                        val dateTitle = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()).format(Date())
                        onCreateNewPage(dateTitle)
                        // Jump to the newly appended latest entry.
                        activePageIndex = pages.size
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Entry", tint = IndigoAccent)
                    }
                    if (pages.size > 1 && activePage != null) {
                        IconButton(onClick = {
                            val idToDelete = activePage.id
                            activePageIndex = (activePageIndex - 1).coerceAtLeast(0)
                            onDeletePage(idToDelete)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Entry", tint = RoseRuby)
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
        ) {
            if (pages.isNotEmpty()) {
                Surface(
                    color = CosmicSlate,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (activePageIndex > 0) activePageIndex-- },
                            enabled = activePageIndex > 0,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous Entry",
                                tint = if (activePageIndex > 0) IndigoGlow else TextMuted
                            )
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = SpaceBlack) {
                            Text(
                                text = "Entry ${activePageIndex + 1} of ${pages.size}",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (activePageIndex < pages.size - 1) activePageIndex++ },
                            enabled = activePageIndex < pages.size - 1,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next Entry",
                                tint = if (activePageIndex < pages.size - 1) IndigoGlow else TextMuted
                            )
                        }
                    }
                }
            }

            if (activePage != null) {
                key(activePage.id) {
                    ComposeLexicalEditor(
                        initialMarkdown = activePage.notes,
                        onContentChanged = { updatedNotes ->
                            onSavePageNotes(activePage.id, updatedNotes)
                        },
                        onTimestampClicked = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your journal is empty. Tap + to begin.", color = TextMuted)
                }
            }
        }
    }
}
