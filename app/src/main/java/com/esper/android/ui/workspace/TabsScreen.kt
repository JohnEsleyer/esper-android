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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.PageEntity
import com.esper.android.ui.theme.*

data class TabItemData(
    val notebook: NotebookEntity,
    val pageNumber: Int = 1,
    val pageId: String? = null
)

data class RecentPageItem(
    val notebook: NotebookEntity,
    val page: PageEntity,
    val pageNumber: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    openTabs: List<TabItemData>,
    recentPages: List<RecentPageItem> = emptyList(),
    activeNotebookId: String,
    onSelectTab: (docId: String, pageIndex: Int) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAllTabs: () -> Unit,
    onNewTabClick: () -> Unit, // Directs to Bookshelf to pick a new book
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reading Tabs", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text("${openTabs.size} books active", color = IndigoGlow, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = onNewTabClick,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Tab", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (openTabs.isNotEmpty()) {
                        TextButton(onClick = onCloseAllTabs) {
                            Text("Close All", color = RoseRuby, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Active Tabs
                item {
                    Text(
                        text = "ACTIVE TABS (${openTabs.size})",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (openTabs.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CosmicSlate,
                            border = BorderStroke(1.dp, BorderSlate),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No open tabs", color = TextSecondary, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = onNewTabClick, colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent)) {
                                    Text("Pick a book from library")
                                }
                            }
                        }
                    }
                } else {
                    items(openTabs, key = { it.notebook.id }) { tab ->
                        val notebook = tab.notebook
                        val isActive = notebook.id == activeNotebookId
                        val (accentColor, gradient) = when (notebook.coverPreset) {
                            "quantum" -> Color(0xFF34D399) to listOf(Color(0xFF064E3B), Color(0xFF134E4A))
                            "solar"   -> Color(0xFFFBBF24) to listOf(Color(0xFF78350F), Color(0xFF451A03))
                            "midnight"-> Color(0xFF60A5FA) to listOf(Color(0xFF1E293B), Color(0xFF1E3A8A))
                            "crimson" -> Color(0xFFFB7185) to listOf(Color(0xFF881337), Color(0xFF4C0519))
                            "nebula"  -> Color(0xFFC084FC) to listOf(Color(0xFF581C87), Color(0xFF3B0764))
                            else      -> Color(0xFF818CF8) to listOf(Color(0xFF1E1B4B), Color(0xFF2E1065))
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) accentColor.copy(alpha = 0.12f) else CosmicSlate,
                            border = BorderStroke(1.dp, if (isActive) accentColor else accentColor.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTab(notebook.id, tab.pageNumber - 1)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 34.dp, height = 46.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Brush.verticalGradient(gradient)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = notebook.title.take(1).uppercase(),
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
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
                                                        .background(accentColor)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = accentColor.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "Chapter ${tab.pageNumber}",
                                                    color = accentColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = notebook.tagsCsv.split(",").firstOrNull()?.trim()?.ifBlank { "General" } ?: "General",
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Recently Viewed Pages
                if (recentPages.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RECENTLY VIEWED PAGES",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    items(recentPages) { recent ->
                        val accentColor = when (recent.notebook.coverPreset) {
                            "quantum" -> Color(0xFF34D399)
                            "solar"   -> Color(0xFFFBBF24)
                            "midnight"-> Color(0xFF60A5FA)
                            "crimson" -> Color(0xFFFB7185)
                            "nebula"  -> Color(0xFFC084FC)
                            else      -> Color(0xFF818CF8)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CosmicSlate.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTab(recent.notebook.id, recent.pageNumber - 1)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recent.page.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(recent.notebook.title, color = accentColor, fontSize = 11.sp, maxLines = 1)
                                        Text(" · Ch.${recent.pageNumber}", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                Text("Jump →", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
