# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.openclaw.assistant.**$$serializer { *; }
-keepclassmembers class com.openclaw.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.openclaw.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Camera
-keep class androidx.camera.** { *; }

# Keep data models
-keep class com.openclaw.assistant.domain.models.** { *; }
