package com.esper.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.AmberGlow
import com.esper.android.ui.theme.CosmicSlate

val LocalReadOnlyMode = compositionLocalOf { false }

@Composable
fun OfflineNoticeBanner(
    modifier: Modifier = Modifier,
    message: String = "Offline Mode Active — Working with local data.",
    onToggleOnline: (() -> Unit)? = null
) {
    Surface(
        color = CosmicSlate,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AmberGlow.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = AmberGlow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = AmberGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }
            if (onToggleOnline != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onToggleOnline,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Go Online",
                        color = AmberGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
