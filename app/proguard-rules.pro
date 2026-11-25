# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to the default
# ProGuard rules configuration that Gradle introduces.

-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
