package com.esper.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.esper.android.data.model.NotebookEntity
import com.esper.android.ui.theme.*

@Composable
fun BookCard(
    notebook: NotebookEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val isReadOnly = LocalReadOnlyMode.current
    val theme = remember(notebook.coverPreset, notebook.coverImage) {
        CoverThemeEngine.getTheme(notebook.coverPreset, notebook.coverImage)
    }
    val gradient = theme.gradientColors

    val coverModel = remember(notebook.coverImage) {
        CoverImageHelper.resolveCoverModel(notebook.coverImage)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSlate),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // Book cover container with standard paperback book ratio (1:1.45)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.69f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Brush.linearGradient(gradient))
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = notebook.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Ambient contrast vignette (matches web `bg-black/25`)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.22f))
                    )
                }

                // App-rendered book spine (subtle translucent, artwork stays full-bleed)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(theme.spineColor.copy(alpha = 0.55f))
                        .align(Alignment.CenterStart)
                )


                // Top action icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (notebook.isPinned) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Pinned",
                                tint = AmberGlow,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(15.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(18.dp))
                    }

                    if (!isReadOnly) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                IconButton(
                                    onClick = onEdit,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Configure Book",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                IconButton(
                                    onClick = onDelete,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Render the centered title only when there is no cover artwork
                // (matches the web contract). When artwork exists it is drawn
                // over the gradient straight away, so a recycled grid item never
                // flashes the title on top of the cover.
                if (coverModel == null) {
                    Text(
                        text = notebook.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 4,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp)
                    )
                }

                // Category pill at the bottom
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = notebook.tagsCsv.split(",").firstOrNull()?.trim()?.ifBlank { "General" } ?: "General",
                        color = IndigoGlow,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Bottom title metadata: max 2 lines with ellipsis
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = notebook.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
