# WebView JS interface must be kept
-keepclassmembers class com.nh.jarvis.MainActivity$AndroidBridge {
   public *;
}
-keep class com.nh.jarvis.HtmlVault { *; }
