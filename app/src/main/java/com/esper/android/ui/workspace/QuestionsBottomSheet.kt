package com.esper.android.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.QuestionEntity
import com.esper.android.ui.components.ConfirmDeleteDialog
import com.esper.android.ui.components.LocalReadOnlyMode
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsBottomSheet(
    questions: List<QuestionEntity>,
    onDismiss: () -> Unit,
    onCreateQuestion: (String) -> Unit = {},
    onDeleteQuestion: (String) -> Unit = {},
    onOpenQuestionEditor: (QuestionEntity) -> Unit = {}
) {
    val isReadOnly = LocalReadOnlyMode.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var questionToDelete by remember { mutableStateOf<QuestionEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CosmicSlate,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Study & Review Questions (${questions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (!isReadOnly) {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Question", tint = AmberGlow)
                    }
                }
            }

            Text(
                text = "Same discussion-style sheet: tap to reveal, edit opens full KaTeX editor.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (questions.isEmpty()) {
                Text(
                    text = "No study questions attached to this notebook.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                        var isRevealed by remember(q.id) { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SpaceBlack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { isRevealed = !isRevealed }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Q${idx + 1}: ${q.title}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!isReadOnly) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onOpenQuestionEditor(q) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = IndigoGlow, modifier = Modifier.size(14.dp))
                                            }
                                            IconButton(
                                                onClick = { questionToDelete = q },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseRuby, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                                if (isRevealed) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = q.answer.ifBlank { "No solution yet — tap edit to add KaTeX." },
                                        color = IndigoGlow,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                } else {
                                    Text(
                                        text = "Tap to reveal answer",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Add Study Question", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Question Prompt") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onCreateQuestion(newTitle.trim())
                            newTitle = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGlow)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CosmicSlate
        )
    }

    questionToDelete?.let { q ->
        ConfirmDeleteDialog(
            title = "Delete Question",
            message = "Are you sure you want to delete \u201C${q.title}\u201D?",
            onConfirm = {
                onDeleteQuestion(q.id)
                questionToDelete = null
            },
            onDismiss = { questionToDelete = null }
        )
    }
}
