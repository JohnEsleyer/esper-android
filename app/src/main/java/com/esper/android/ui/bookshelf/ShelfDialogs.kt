package com.esper.android.ui.bookshelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.data.model.NotebookEntity
import com.esper.android.data.model.ShelfEntity
import com.esper.android.ui.theme.*

val PRESET_SHELF_COLORS = listOf(
    "#6366F1", // Indigo
    "#10B981", // Emerald
    "#F59E0B", // Amber
    "#F43F5E", // Rose
    "#3B82F6", // Blue
    "#8B5CF6", // Purple
    "#06B6D4"  // Cyan
)

@Composable
fun CreateShelfDialog(
    onConfirm: (title: String, description: String, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PRESET_SHELF_COLORS[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Bookshelf",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bookshelf Name *") },
                    placeholder = { Text("e.g. Astrophysics, Research") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Short note on this collection") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Accent Color",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PRESET_SHELF_COLORS.forEach { hex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (_: Exception) { IndigoAccent }
                            val isSelected = selectedColor.equals(hex, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = hex }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, Color.White, CircleShape)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), description.trim(), selectedColor)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CosmicSlate,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SetBookToShelfDialog(
    book: NotebookEntity,
    shelves: List<ShelfEntity>,
    onToggleShelf: (shelfId: String) -> Unit,
    onCreateShelfClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = IndigoGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set Bookshelf",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Assign \u201C${book.title}\u201D to bookshelves",
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                if (shelves.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No bookshelves created yet",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onCreateShelfClick,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Bookshelf", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(shelves, key = { it.id }) { shelf ->
                            val isAssigned = BookshelfFiltering.shelfBookIds(shelf.notebookIdsCsv).contains(book.id)
                            val shelfColor = try {
                                Color(android.graphics.Color.parseColor(shelf.color))
                            } catch (_: Exception) { IndigoAccent }
                            val bookCount = BookshelfFiltering.shelfBookIds(shelf.notebookIdsCsv).size

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAssigned) SurfaceSlate else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAssigned) IndigoGlow.copy(alpha = 0.5f) else BorderSlate.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleShelf(shelf.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(shelfColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = shelf.title,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "$bookCount ${if (bookCount == 1) "book" else "books"}",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = isAssigned,
                                        onCheckedChange = { onToggleShelf(shelf.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = IndigoAccent,
                                            uncheckedColor = TextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onCreateShelfClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoGlow),
                        border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Bookshelf", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CosmicSlate,
        shape = RoundedCornerShape(16.dp)
    )
}
