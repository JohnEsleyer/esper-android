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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    onNewTabClick: () -> Unit,
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
                        val theme = CoverThemeEngine.getTheme(notebook.coverPreset, notebook.coverImage)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CosmicSlate,
                            border = BorderStroke(1.dp, if (isActive) theme.accentColor else theme.accentColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectTab(notebook.id, tab.pageNumber - 1)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.horizontalGradient(listOf(theme.gradientColors.first().copy(alpha = 0.4f), Color(0xFF0F172A))))
                            ) {
                                // Book Spine matching the cover (subtle translucent)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(6.dp)
                                        .background(theme.spineColor.copy(alpha = 0.55f))
                                        .align(Alignment.CenterStart)
                                )

                                Row(
                                    modifier = Modifier
                                        .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Miniature book cover with image or gradient
                                        val coverModel = remember(notebook.coverImage) {
                                            CoverImageHelper.resolveCoverModel(notebook.coverImage)
                                        }
                                        var isImageLoaded by remember(coverModel) { mutableStateOf(false) }

                                        Box(
                                            modifier = Modifier
                                                .size(width = 34.dp, height = 46.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Brush.verticalGradient(theme.gradientColors)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverModel != null) {
                                                AsyncImage(
                                                    model = coverModel,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    onSuccess = { isImageLoaded = true },
                                                    onError = { isImageLoaded = false },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                if (isImageLoaded) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.22f))
                                                    )
                                                }
                                            }

                                            if (coverModel == null || !isImageLoaded) {
                                                Text(
                                                    text = notebook.title.take(1).uppercase(),
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
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
                                                            .background(theme.accentColor)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = theme.tagBgColor
                                                ) {
                                                    Text(
                                                        text = "Chapter ${tab.pageNumber}",
                                                        color = theme.accentColor,
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
                        val theme = CoverThemeEngine.getTheme(recent.notebook.coverPreset, recent.notebook.coverImage)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CosmicSlate.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectTab(recent.notebook.id, recent.pageNumber - 1)
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini Left spine matching the parent book's cover (subtle translucent)
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(44.dp)
                                        .background(theme.spineColor.copy(alpha = 0.55f))
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                            Text(recent.notebook.title, color = theme.accentColor, fontSize = 11.sp, maxLines = 1)
                                            Text(" · Ch.${recent.pageNumber}", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        "Jump →",
                                        color = theme.accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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
