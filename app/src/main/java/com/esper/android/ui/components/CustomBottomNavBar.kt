package com.esper.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.*

@Composable
fun CustomBottomNavBar(
    currentPage: Int,
    totalPages: Int,
    totalBooksCount: Int,
    onPageSelected: (Int) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenCreateBook: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReadOnly = LocalReadOnlyMode.current
    var pageInputText by remember(currentPage) { mutableStateOf(currentPage.toString()) }

    Surface(
        color = CosmicSlate,
        border = BorderStroke(1.dp, BorderSlate),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (currentPage > 1) onPageSelected(currentPage - 1)
                    },
                    enabled = currentPage > 1,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SurfaceSlate,
                        disabledContainerColor = CosmicSlate
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous Page",
                        tint = if (currentPage > 1) TextPrimary else TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Page", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SpaceBlack,
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .width(46.dp)
                            .height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BasicTextField(
                                value = pageInputText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(3)
                                    pageInputText = filtered
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = IndigoGlow,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val num = pageInputText.toIntOrNull() ?: 1
                                        val clamped = num.coerceIn(1, maxOf(1, totalPages))
                                        pageInputText = clamped.toString()
                                        onPageSelected(clamped)
                                    }
                                ),
                                cursorBrush = SolidColor(IndigoAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text("of $totalPages ($totalBooksCount books)", color = TextMuted, fontSize = 12.sp)
                }

                FilledTonalIconButton(
                    onClick = {
                        if (currentPage < totalPages) onPageSelected(currentPage + 1)
                    },
                    enabled = currentPage < totalPages,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SurfaceSlate,
                        disabledContainerColor = CosmicSlate
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next Page",
                        tint = if (currentPage < totalPages) TextPrimary else TextMuted
                    )
                }
            }

            HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f), thickness = 1.dp)

            NavigationBar(
                containerColor = CosmicSlate,
                modifier = Modifier.height(58.dp)
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = onOpenLibrary,
                    icon = { Icon(Icons.Default.Book, contentDescription = "Library", modifier = Modifier.size(19.dp)) },
                    label = { Text("Library", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TextPrimary,
                        indicatorColor = IndigoAccent,
                        unselectedIconColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onOpenJournal,
                    icon = { Icon(Icons.Default.EditNote, contentDescription = "Journal", modifier = Modifier.size(21.dp)) },
                    label = { Text("Journal", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = IndigoGlow,
                        unselectedTextColor = IndigoGlow
                    )
                )

                NavigationBarItem(
                    selected = false,
                    enabled = !isReadOnly,
                    onClick = { if (!isReadOnly) onOpenCreateBook() },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isReadOnly) TextMuted else IndigoAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    label = { Text("New", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = IndigoAccent,
                        unselectedTextColor = IndigoGlow,
                        disabledIconColor = TextMuted,
                        disabledTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onOpenTabs,
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Tabs", modifier = Modifier.size(19.dp)) },
                    label = { Text("Tabs", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = ElectricViolet,
                        unselectedTextColor = ElectricViolet
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(19.dp)) },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    }
}
