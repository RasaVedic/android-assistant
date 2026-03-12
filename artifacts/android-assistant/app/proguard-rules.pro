# Add project specific ProGuard rules here.
# Keep OkHttp and Gson classes when minifying
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
