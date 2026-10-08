# R8 full mode is on. Libraries here ship consumer rules (Retrofit, OkHttp, Room, Hilt, Firebase,
# kotlinx.serialization), so we only keep what reflection reaches that they can't see.

# kotlinx.serialization: keep generated serializers for OUR DTOs (names matter for JSON keys).
-keepclassmembers class com.spendwise.app.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.spendwise.app.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit generic return types (SyncResponseDto<ExpenseDto>) are read via reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Readable crash traces in Play Console / Crashlytics.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
