# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Common Android Rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ExoPlayer / Media3 Keep Rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Room Keep Rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Application Packages & Services
-keep class com.example.** { *; }
-dontwarn com.example.**

# Kotlin Serialization / Coroutines / Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Retrofit/OkHttp/Moshi Keep Rules (since we have network, retro, okhttp, moshi)
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
# Annotation processor / Moshi generated adapters
-keep class *JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

