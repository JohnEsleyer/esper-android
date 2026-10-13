package com.esper.android.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    chapterTitle: String,
    onDismiss: () -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    val mockComments = remember {
        mutableStateListOf(
            "Dr. Eleanor Arroway" to "The perihelion acceleration demonstrates areal velocity conservation perfectly."
        )
    }

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
            Text(
                text = "Discussion & Notes: $chapterTitle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                mockComments.forEach { (author, text) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpaceBlack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = author, color = IndigoGlow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(text = text, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add to discussion...", fontSize = 12.sp, color = TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpaceBlack,
                        unfocusedContainerColor = SpaceBlack,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            mockComments.add("You" to commentText.trim())
                            commentText = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = IndigoAccent)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
