package com.esper.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.*

/**
 * State machine behind [UnsavedChangesDialog].
 *
 * The editor reports dirty/clean transitions through [setDirty]; every
 * navigation vector calls [requestNavigation]. When the document is dirty the
 * action is parked until the user chooses Save, Discard, or Cancel. Keeping the
 * decision logic here (instead of inline in each screen) makes the behaviour
 * identical across the workspace, journal, and question editor.
 */
@Stable
class UnsavedChangesGuard {
    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
        private set

    val isDialogVisible: Boolean
        get() = pendingAction != null

    fun setDirty(dirty: Boolean) {
        hasUnsavedChanges = dirty
    }

    /**
     * Runs [action] immediately when the document is clean; otherwise parks it
     * behind the confirmation dialog. Returns true when the action was deferred.
     */
    fun requestNavigation(action: () -> Unit): Boolean {
        if (!hasUnsavedChanges) {
            action()
            return false
        }
        pendingAction = action
        return true
    }

    /** Save succeeded: clear the dirty flag and continue the parked navigation. */
    fun completeAfterSave() {
        val action = pendingAction
        hasUnsavedChanges = false
        pendingAction = null
        action?.invoke()
    }

    /** Throw the in-memory edits away and continue the parked navigation. */
    fun discardAndContinue() {
        val action = pendingAction
        hasUnsavedChanges = false
        pendingAction = null
        action?.invoke()
    }

    /** Keep the user in the editor; the parked action is dropped. */
    fun cancel() {
        pendingAction = null
    }

    /** Drop all state (used when the editor is deliberately reset). */
    fun reset() {
        hasUnsavedChanges = false
        pendingAction = null
    }
}

@Composable
fun UnsavedChangesDialog(
    title: String = "Unsaved Changes",
    message: String = "You have unsaved changes. Do you want to save them before leaving?",
    onSaveAndContinue: () -> Unit,
    onDiscardAndContinue: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AmberGlow,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Text(
                text = message,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onSaveAndContinue,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSync),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDiscardAndContinue,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseRuby),
                    border = BorderStroke(1.dp, RoseRuby.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Discard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                }
            }
        },
        containerColor = CosmicSlate,
        shape = RoundedCornerShape(16.dp)
    )
}
