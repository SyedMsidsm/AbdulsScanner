# ProGuard & R8 rules for Abdul's Scanner

# PdfBox-Android rules
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Tesseract4Android rules
-keep class cz.adaptech.tesseract4android.** { *; }
-dontwarn cz.adaptech.tesseract4android.**

# ONNX Runtime rules
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.vision.** { *; }

# OpenCV rules
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
