-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve Room models, DAO entities and DTOs
-keep class com.esper.android.data.model.** { *; }
-keep class com.esper.android.data.local.** { *; }
-keep class com.esper.android.data.remote.** { *; }
-keep class com.esper.android.data.sync.** { *; }

# Gson reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-keepattributes EnclosingMethod, InnerClasses
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# YouTube Player
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-dontwarn com.pierfrancescosoffritti.androidyoutubeplayer.**
