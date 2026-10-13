package com.esper.android.ui.bookshelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotebookScreen(
    onSaveNotebook: (title: String, description: String, tags: String, preset: String, coverImage: String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newTags by remember { mutableStateOf("") }
    var newCoverImageUrl by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf("celestial") }

    val gradient = when (selectedPreset) {
        "quantum" -> listOf(Color(0xFF064E3B), Color(0xFF0F172A), Color(0xFF134E4A))
        "solar" -> listOf(Color(0xFF78350F), Color(0xFF451A03), Color(0xFF0F172A))
        else -> listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF2E1065))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Notebook", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
                                    newCoverImageUrl.trim().ifBlank { null }
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
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (newTitle.isNotBlank()) newTitle else "Notebook Preview",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("Notebook Title *") },
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
                label = { Text("Tags (comma separated, e.g. Physics, Astrodynamics)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newCoverImageUrl,
                onValueChange = { newCoverImageUrl = it },
                label = { Text("Cover Image URL (optional)") },
                placeholder = { Text("https://...") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Select Theme Preset:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("celestial", "quantum", "solar").forEach { p ->
                    FilterChip(
                        selected = selectedPreset == p,
                        onClick = { selectedPreset = p },
                        label = { Text(p.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        onSaveNotebook(
                            newTitle.trim(),
                            newDesc.trim(),
                            newTags.trim(),
                            selectedPreset,
                            newCoverImageUrl.trim().ifBlank { null }
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
