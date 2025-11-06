# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ========== LOGGING RULES ==========
# In release builds, strip non-error android.util.Log calls to avoid leaking
# sensitive information and reduce method count/size. Keep error logs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Strip VERBOSE and DEBUG Timber logs in release builds for security and performance
# This removes calls at compile time, making them completely absent from release APK
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}

# Keep Timber framework classes (but allow stripping of v/d calls above)
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }
-keep class timber.log.Timber$DebugTree { *; }
-keep class ** extends timber.log.Timber$Tree { *; }

# Keep custom DebugLogger utility
-keep class com.davy.util.DebugLogger { *; }
-keep class com.davy.util.DebugLogger$** { *; }

# Note: INFO, WARN, and ERROR logs are kept for production diagnostics

# Keep classes that use logging
-keepclasseswithmembers class * {
    void <init>(...);
}

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep debug information
-keepattributes *Annotation*

# Keep rules for ical4j
-keep class net.fortuna.ical4j.** { *; }
-dontwarn net.fortuna.ical4j.**

# Keep rules for ez-vcard
-keep class ezvcard.** { *; }
-dontwarn ezvcard.**

# Keep rules for Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep rules for Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}

# Keep rules for OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep rules for Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep rules for Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep rules for Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep rules for Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep data classes
-keep class com.davy.data.** { *; }
-keep class com.davy.domain.** { *; }

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== PERFORMANCE OPTIMIZATIONS ==========
# Enable aggressive R8 optimizations for release builds

# Enable method inlining for better performance
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Allow R8 to optimize and inline Kotlin coroutines
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
}

# Keep ProfileInstaller for baseline profiles (improves startup performance)
-keep class androidx.profileinstaller.** { *; }

# Optimize Compose for release builds
-keep,allowoptimization class androidx.compose.** { *; }

# ========== MISSING CLASSES FIXES ==========
# Ignore optional dependencies that are not used on Android

# Google Error Prone (compile-time only annotations)
-dontwarn com.google.errorprone.annotations.**

# Apache Ivy (optional Groovy dependency)
-dontwarn org.apache.ivy.**

# Jython (Python scripting - not used)
-dontwarn org.python.**

# Jaxen (XPath - not used)
-dontwarn org.jaxen.**

# Apache Xalan/XML (not used on Android)
-dontwarn org.apache.xml.utils.**
-dontwarn com.sun.org.apache.xml.internal.utils.**

# XStream (not used)
-dontwarn com.thoughtworks.xstream.**

# Java Beans (not available on Android)
-dontwarn java.beans.**

# RMI (not available on Android)
-dontwarn java.rmi.**

# Swing (not available on Android)
-dontwarn javax.swing.**

# JRebel (development only)
-dontwarn org.zeroturnaround.javarebel.**

# Freemarker optional dependencies
-dontwarn freemarker.**

# Groovy optional dependencies
-dontwarn org.codehaus.groovy.**
-dontwarn groovy.**

# ========== Auto-generated missing class silences from R8 (safe for optional deps) ==========
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn groovyjarjarantlr4.stringtemplate.StringTemplate
-dontwarn groovyjarjarasm.asm.util.ASMifierSupport
-dontwarn java.awt.BorderLayout
-dontwarn java.awt.Color
-dontwarn java.awt.Component
-dontwarn java.awt.Container
-dontwarn java.awt.Dimension
-dontwarn java.awt.FlowLayout
-dontwarn java.awt.Font
-dontwarn java.awt.Graphics2D
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.Insets
-dontwarn java.awt.LayoutManager
-dontwarn java.awt.Rectangle
-dontwarn java.awt.event.ActionListener
-dontwarn java.awt.event.MouseAdapter
-dontwarn java.awt.event.MouseListener
-dontwarn java.awt.event.WindowAdapter
-dontwarn java.awt.event.WindowListener
-dontwarn java.awt.font.FontRenderContext
-dontwarn java.awt.font.TextLayout
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.print.Printable
-dontwarn java.lang.invoke.SwitchPoint
-dontwarn javax.servlet.ServletContextListener
-dontwarn org.abego.treelayout.Configuration$Location
-dontwarn org.abego.treelayout.Configuration
-dontwarn org.abego.treelayout.NodeExtentProvider
-dontwarn org.abego.treelayout.TreeForTreeLayout
-dontwarn org.abego.treelayout.TreeLayout
-dontwarn org.abego.treelayout.util.DefaultConfiguration
-dontwarn org.apache.avalon.framework.logger.Logger
-dontwarn org.apache.log.Hierarchy
-dontwarn org.apache.log.Logger
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.joda.convert.ToString
-dontwarn org.joda.time.Instant
-dontwarn org.stringtemplate.v4.AttributeRenderer
-dontwarn org.stringtemplate.v4.AutoIndentWriter
-dontwarn org.stringtemplate.v4.NumberRenderer
-dontwarn org.stringtemplate.v4.ST
-dontwarn org.stringtemplate.v4.STErrorListener
-dontwarn org.stringtemplate.v4.STGroup
-dontwarn org.stringtemplate.v4.STGroupFile
-dontwarn org.stringtemplate.v4.STWriter
-dontwarn org.stringtemplate.v4.StringRenderer
-dontwarn org.stringtemplate.v4.compiler.CompiledST
-dontwarn org.stringtemplate.v4.gui.STViz
-dontwarn org.stringtemplate.v4.misc.ErrorBuffer
-dontwarn org.stringtemplate.v4.misc.MultiMap
