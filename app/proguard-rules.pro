# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.jarm.app.**$$serializer { *; }
-keepclassmembers class com.jarm.app.** { *** Companion; }
-keepclasseswithmembers class com.jarm.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Phone entry points are loaded by Shizuku/app_process by name.
-keep class com.jarm.app.phone.PhoneUserService { public <init>(); *; }
-keep class com.jarm.app.phone.PhoneRootPaste { public static void main(java.lang.String[]); }
-keep class com.jarm.app.phone.IPhoneUserService** { *; }
# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }

# JSch (SSH/SFTP)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# JLaTeXMath creates environment macro handlers and resolves executeMacro by name.
-keep class org.scilab.forge.jlatexmath.NewCommandMacro {
    public <init>();
    public java.lang.String executeMacro(org.scilab.forge.jlatexmath.TeXParser, java.lang.String[]);
}

# Compose
-dontwarn androidx.compose.**

# JNI (llama): native code reads these classes' fields and constructors, and calls the
# callback methods, by name.
-keep class com.jarm.app.api.NativeChatCallback { *; }
-keep class * implements com.jarm.app.api.NativeChatCallback { *; }
-keep class com.jarm.app.api.ChatTemplateToolCall { *; }
-keep class com.jarm.app.api.ChatTemplateMessage { *; }
-keep class com.jarm.app.api.ChatTemplateTool { *; }
-keep class com.jarm.app.api.LlamaChatTemplateRequest { *; }
-keep class com.jarm.app.api.ChatTemplateGrammarTrigger { *; }
-keep class com.jarm.app.api.LlamaChatTemplateResult { *; }
-keepclasseswithmembernames class com.jarm.app.** { native <methods>; }
# Reflection: flavor sandbox factories (AppContainer) and the screenshot fixture (MainActivity).
-keep class com.jarm.app.sandbox.FdroidSandboxManagerFactory { <init>(...); }
-keep class com.jarm.app.sandbox.PlaySandboxManagerFactory { <init>(...); }
-keep class com.jarm.app.screenshot.ScreenshotFixture { *; }# Ktor server: JVM-only debug detection (DevelopmentMode), absent on Android.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
