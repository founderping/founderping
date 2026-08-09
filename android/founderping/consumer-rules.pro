# The page reaches these methods by name over the JavaScript bridge, so R8 sees
# no call sites and strips them. The failure only shows up in release builds,
# as a widget that never starts.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
