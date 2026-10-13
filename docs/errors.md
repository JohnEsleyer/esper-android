# Errors & Resolution Log

### Error 1: Kotlin String Template Interpolation Error in MainActivity.kt
- **Status:** [SOLVED]
- **File:** `app/src/main/java/com/esper/android/MainActivity.kt:108`
- **Error:** `Unresolved reference 'r'`
- **Cause:** In the default seed page notes string literal, `$r(\theta)...$` was evaluated as a Kotlin string template variable `$r` rather than a raw character `$`.
- **Resolution:** Escaped `$r` as `\$r` (and closing `$` as `\$`). Verified with `./gradlew assembleDebug` (build successful).

### Error 2: Missing FontWeight Import in WorkspaceScreen.kt
- **Status:** [SOLVED]
- **File:** `app/src/main/java/com/esper/android/ui/workspace/WorkspaceScreen.kt:162`
- **Error:** `Unresolved reference 'FontWeight'`
- **Cause:** `FontWeight` was referenced in the floating HTML button label without importing `androidx.compose.ui.text.font.FontWeight`.
- **Resolution:** Added `import androidx.compose.ui.text.font.FontWeight`.
