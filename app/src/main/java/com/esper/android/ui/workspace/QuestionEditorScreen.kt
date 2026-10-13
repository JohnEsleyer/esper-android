package com.esper.android.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.lexical.ComposeLexicalEditor
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorScreen(
    question: QuestionEntity,
    onNavigateBack: () -> Unit,
    onSaveAnswer: (questionId: String, answer: String) -> Unit,
    onUpdateTitle: (questionId: String, title: String) -> Unit
) {
    var title by remember(question.title) { mutableStateOf(question.title) }
    var answerContent by remember(question.answer) { mutableStateOf(question.answer) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Question & Review Solution",
                            style = MaterialTheme.typography.labelMedium,
                            color = IndigoGlow
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSaveAnswer(question.id, answerContent)
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = EmeraldSync
                        )
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
            // Edit Question prompt
            Surface(
                color = SurfaceSlate,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onUpdateTitle(question.id, it)
                    },
                    label = { Text("Question Prompt / Title", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = IndigoAccent,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            // Full-screen KaTeX & Markdown Editor for the Answer
            ComposeLexicalEditor(
                initialMarkdown = answerContent,
                onContentChanged = { updated ->
                    answerContent = updated
                    onSaveAnswer(question.id, updated)
                },
                onTimestampClicked = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
