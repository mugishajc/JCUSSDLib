# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep public API
-keep public class com.jcussdlib.USSDController { *; }
-keep public interface com.jcussdlib.USSDApi { *; }
-keep public class com.jcussdlib.service.** { *; }

# Keep accessibility service
-keep class com.jcussdlib.service.USSDService { *; }
-keep class com.jcussdlib.service.SplashLoadingService { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
