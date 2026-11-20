# Release 混淆规则(当前未开启 minify,预留)
-keepattributes *Annotation*
-keep class com.aichat.client.data.** { *; }


# PDF 解析库通过反射加载字体与资源,防止 R8 裁掉
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**

# WebView JS 桥:方法名被裁剪会导致页面调不到原生能力
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
