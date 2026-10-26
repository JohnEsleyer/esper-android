package com.esper.android.ui.workspace

import androidx.activity.compose.BackHandler
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
import com.esper.android.ui.components.LocalReadOnlyMode
import com.esper.android.ui.components.UnsavedChangesDialog
import com.esper.android.ui.components.UnsavedChangesGuard
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
    val isReadOnly = LocalReadOnlyMode.current
    var title by remember(question.title) { mutableStateOf(question.title) }
    var answerContent by remember(question.answer) { mutableStateOf(question.answer) }
    val unsavedGuard = remember { UnsavedChangesGuard() }

    fun guardNavigation(action: () -> Unit) {
        if (isReadOnly) action() else unsavedGuard.requestNavigation(action)
    }

    BackHandler {
        if (unsavedGuard.isDialogVisible) {
            unsavedGuard.cancel()
        } else {
            guardNavigation { onNavigateBack() }
        }
    }

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
                    IconButton(onClick = { guardNavigation { onNavigateBack() } }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (!isReadOnly) {
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
                        if (isReadOnly) return@OutlinedTextField
                        title = it
                        onUpdateTitle(question.id, it)
                    },
                    readOnly = isReadOnly,
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
                onContentChanged = { updated -> answerContent = updated },
                onHasUnsavedChangesChanged = { unsavedGuard.setDirty(it) },
                onTimestampClicked = {},
                modifier = Modifier.fillMaxSize(),
                readOnly = isReadOnly
            )
        }
    }

    if (unsavedGuard.isDialogVisible) {
        UnsavedChangesDialog(
            title = "Unsaved Changes",
            message = "You have unsaved changes to this answer. Do you want to save them before leaving?",
            onSaveAndContinue = {
                onSaveAnswer(question.id, answerContent)
                unsavedGuard.completeAfterSave()
            },
            onDiscardAndContinue = { unsavedGuard.discardAndContinue() },
            onCancel = { unsavedGuard.cancel() }
        )
    }
}
