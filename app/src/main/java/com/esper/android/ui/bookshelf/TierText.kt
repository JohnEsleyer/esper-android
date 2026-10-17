package com.esper.android.ui.bookshelf

/**
 * Pure tier-display helpers for SettingsScreen (unit-tested).
 * `inactivityDays == null` means the server reports immunity (admin tier);
 * unknown/offline state is represented by a null [memberType].
 */
fun tierBadgeText(memberType: String?, signedIn: Boolean): String = when {
    !signedIn -> "OFFLINE LOCAL"
    memberType != null -> "${memberType.uppercase()} PLAN"
    else -> "SYNCING…"
}

fun retentionText(memberType: String?, inactivityDays: Long?): String = when {
    memberType == "admin" -> "• Inactivity retention: never pruned (admin)"
    inactivityDays != null -> "• Inactivity retention: $inactivityDays days cloud backup"
    else -> "• Inactivity retention: …"
}
