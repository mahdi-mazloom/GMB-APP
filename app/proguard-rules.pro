# ==============================================================================
# GMB NET Production ProGuard / R8 Configuration
# Optimized for high security, code shrinking, and anti-reverse engineering
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Anti-Reverse Engineering & Obfuscation Enhancements
# ------------------------------------------------------------------------------
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# Strip original source file names from stack traces while keeping line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve necessary annotations, generic signatures, and exceptions for reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ------------------------------------------------------------------------------
# 2. Security: Strip Sensitive Debug Logging (Prevent Information Leakage in Logcat)
# ------------------------------------------------------------------------------
# Strips verbose and debug logging calls in release builds to ensure passwords,
# tokens, server IPs, and encryption keys never leak into Android system logs.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 3. JSch (SSH Tunneling Engine) Rules
# CRITICAL: JSch dynamically instantiates ciphers, key exchanges (KEX), MACs,
# and key algorithms using reflection (Class.forName). Stripping them breaks SSH!
# ------------------------------------------------------------------------------
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Optional compression library for JSch (JZlib)
-keep class com.jcraft.jzlib.** { *; }
-dontwarn com.jcraft.jzlib.**

# BouncyCastle / JCE algorithm providers if used by JSch
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**

# ------------------------------------------------------------------------------
# 4. Retrofit 2 & OkHttp
# ------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions, InnerClasses, Signature, *Annotation*

# Retain Retrofit service API interfaces and method annotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# ------------------------------------------------------------------------------
# 5. Moshi (JSON Serialization / Deserialization)
# ------------------------------------------------------------------------------
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }

# Keep annotated Moshi data models and their fields to prevent serialization mismatches
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
-keep class * extends com.squareup.moshi.JsonAdapter {
    <init>(...);
}

# Keep our project's data models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.remote.** { *; }

# ------------------------------------------------------------------------------
# 6. Room Database & SQLite
# ------------------------------------------------------------------------------
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.Insert *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
    @androidx.room.Query *;
    @androidx.room.RawQuery *;
}

# ------------------------------------------------------------------------------
# 7. Kotlin Coroutines & Flow
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# 8. Jetpack Compose Rules
# ------------------------------------------------------------------------------
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ------------------------------------------------------------------------------
# 9. VpnService & Android Native Components
# ------------------------------------------------------------------------------
-keep class com.example.vpn.SshVpnService { *; }
-keep class * extends android.net.VpnService { *; }

