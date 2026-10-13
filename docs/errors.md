# Errors & Resolution Log

### Error 1: Kotlin String Template Interpolation Error in MainActivity.kt
- **Status:** [SOLVED]
- **File:** `app/src/main/java/com/esper/android/MainActivity.kt:108`
- **Error:** `Unresolved reference 'r'`
- **Cause:** In the default seed page notes string literal, `$r(\theta)...$` was evaluated as a Kotlin string template variable `$r` rather than a raw character `$`.
- **Resolution:** Escaped `$r` as `\$r` (and closing `$` as `\$`). Verified with `./gradlew assembleDebug` (build successful).
