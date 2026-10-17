package com.esper.android.ui.bookshelf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.esper.android.ui.theme.*

fun sanitizeCoverInput(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("<svg", ignoreCase = true) && trimmed.endsWith("</svg>", ignoreCase = true)) {
        return "data:image/svg+xml;utf8," + Uri.encode(trimmed)
    }
    return trimmed
}

/**
 * Shared visual preset carousel: miniature 3D book covers with gradients,
 * spine stripes, and accent selection rings. Used by Create and Configure.
 */
@Composable
internal fun PresetCoverCarousel(
    selectedPresetId: String,
    onPresetSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Cover Preset Selections (${CoverThemeEngine.PRESETS.size})",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(CoverThemeEngine.PRESETS) { preset ->
                val isSelected = selectedPresetId.equals(preset.id, ignoreCase = true)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clickable { onPresetSelected(preset.id) }
                ) {
                    // Mini 3D Book Thumbnail Preview
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) preset.theme.accentColor else Color(0x33FFFFFF)
                        ),
                        shadowElevation = if (isSelected) 6.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.69f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(preset.theme.gradientColors)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Left spine stripe (subtle translucent)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(preset.theme.spineColor.copy(alpha = 0.55f))
                                    .align(Alignment.CenterStart)
                            )

                            Text(
                                text = preset.title.take(1),
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = preset.title,
                        color = if (isSelected) preset.theme.accentColor else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotebookScreen(
    onSaveNotebook: (title: String, description: String, tags: String, preset: String, coverImage: String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newTags by remember { mutableStateOf("Physics") }
    var rawCoverInput by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf("celestial") }
    var isCopiedPrompt by remember { mutableStateOf(false) }

    val sanitizedCover = remember(rawCoverInput) { sanitizeCoverInput(rawCoverInput) }
    val currentTheme = CoverThemeEngine.getTheme(selectedPreset, sanitizedCover)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Notebook", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                onSaveNotebook(
                                    newTitle.trim(),
                                    newDesc.trim(),
                                    newTags.trim(),
                                    selectedPreset,
                                    sanitizedCover
                                )
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Create", tint = EmeraldSync)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Small Portrait Book Preview (1:1.44 aspect ratio)
            val previewModel = remember(sanitizedCover) {
                CoverImageHelper.resolveCoverModel(sanitizedCover)
            }
            var isPreviewLoaded by remember(previewModel) { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, currentTheme.accentColor.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .width(124.dp)
                        .aspectRatio(0.69f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(currentTheme.gradientColors))
                    ) {
                        if (previewModel != null) {
                            AsyncImage(
                                model = previewModel,
                                contentDescription = newTitle,
                                contentScale = ContentScale.Crop,
                                onSuccess = { isPreviewLoaded = true },
                                onError = { isPreviewLoaded = false },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isPreviewLoaded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.22f))
                                )
                            }
                        }

                        if (previewModel == null || !isPreviewLoaded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = newTags.split(",").firstOrNull()?.trim()?.take(10)?.ifBlank { "GENERAL" } ?: "GENERAL",
                                        color = currentTheme.accentColor,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = if (newTitle.isNotBlank()) newTitle else "Notebook Title",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 3,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

                                Text(
                                    text = "ESPER NOTEBOOK",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Spine matching the cover (subtle translucent)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(6.dp)
                                .background(currentTheme.spineColor.copy(alpha = 0.55f))
                                .align(Alignment.CenterStart)
                        )
                    }
                }
            }

            // Input Fields
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("Notebook Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newDesc,
                onValueChange = { newDesc = it },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newTags,
                onValueChange = { newTags = it },
                label = { Text("Tags / Categories (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Preset Cover Selection Carousel with Visual Previews
            PresetCoverCarousel(
                selectedPresetId = selectedPreset,
                onPresetSelected = { presetId ->
                    selectedPreset = presetId
                    rawCoverInput = ""
                }
            )

            // Custom SVG / Image URL Section with AI Prompt button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CosmicSlate,
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom Image or SVG Code", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Esper Cover Prompt", LLM_COVER_PROMPT_TEMPLATE)
                                clipboard.setPrimaryClip(clip)
                                isCopiedPrompt = true
                                Toast.makeText(context, "AI Cover Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                if (isCopiedPrompt) Icons.Default.Check else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isCopiedPrompt) EmeraldSync else IndigoGlow,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCopiedPrompt) "Copied!" else "AI Prompt",
                                color = if (isCopiedPrompt) EmeraldSync else IndigoGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = rawCoverInput,
                        onValueChange = { rawCoverInput = it },
                        label = { Text("Image URL or raw <svg>...</svg>") },
                        placeholder = { Text("Paste https://... or <svg viewBox=\"0 0 1000 1440\"...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (rawCoverInput.isNotBlank()) {
                        TextButton(
                            onClick = { rawCoverInput = "" },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear Custom Cover", color = RoseRuby, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        onSaveNotebook(
                            newTitle.trim(),
                            newDesc.trim(),
                            newTags.trim(),
                            selectedPreset,
                            sanitizedCover
                        )
                        onNavigateBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Create Notebook", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
