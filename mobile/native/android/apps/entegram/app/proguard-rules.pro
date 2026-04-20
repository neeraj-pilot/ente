# enteGram ProGuard / R8 rules

# ── kotlinx-serialization ────────────────────────────────────────
# Keep @Serializable classes and their generated serializer companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.ente.entegram.**$$serializer { *; }
-keepclassmembers class io.ente.entegram.** {
    *** Companion;
}
-keepclasseswithmembers class io.ente.entegram.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── OkHttp ───────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Coil ─────────────────────────────────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**

# ── Room ─────────────────────────────────────────────────────────
# Room generates code that R8 handles well by default; this rule
# keeps DAO interface methods from being removed.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *

# ── UniFFI / JNA ────────────────────────────────────────────────
-keep class uniffi.ente_ffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window

# ── AndroidX Security / Tink ────────────────────────────────────
# Tink references Error Prone annotations that are compile-time only.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
