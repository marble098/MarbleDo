# Keep the AndroidX lifecycle SavedState/ViewModel signatures used by Compose.
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class **$$serializer { *; }
-keep class com.marbledo.core.data.settings.** { *; }
-dontwarn javax.annotation.**
