# MedTraveler — ProGuard / R8 rules
#
# For the first Play Store submission minifyEnabled is OFF, so these rules
# are not applied yet. They are kept here so flipping minifyEnabled=true later
# (smaller AAB) does not silently strip reflection-heavy deps.

# --- Java 11 sources of line numbers for crash triage ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Model classes used by Room / JSON parsing (field names accessed by name) ---
-keep class com.davinci.medtraveler.model.** { *; }
-keep class com.davinci.medtraveler.data.local.** { *; }

# --- Glide uses generated API stubs; keep the Generated API. ---
-keep public class com.bumptech.glide.GeneratedAppGlideModule { *; }

# --- Firebase Auth / Firestore SDK ship their own consumer rules; keep model. ---
-keep class com.google.firebase.auth.** { *; }

# --- OkHttp / Kotlin metadata (defensive) ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**