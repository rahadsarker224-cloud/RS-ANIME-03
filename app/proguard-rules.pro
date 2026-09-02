# WebView JS interface must be kept
-keepclassmembers class com.rsanime.MainActivity$AndroidBridge {
   public *;
}
-keep class com.rsanime.HtmlVault { *; }
