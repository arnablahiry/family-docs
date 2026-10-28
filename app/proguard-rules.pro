# SQLCipher
-keep class net.zetetic.database.** { *; }
# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
# ML Kit text recognition (on-device OCR): keep it whole so release shrinking can't break the scan
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-dontwarn com.google.mlkit.**
