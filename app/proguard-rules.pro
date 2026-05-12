-keepattributes Signature
-keepattributes *Annotation*
-dontwarn javax.annotation.**
-keepnames class dagger.internal.codegen.ComponentProcessor
-keep class * extends dagger.internal.Binding
-keep class * extends dagger.internal.ModuleAdapter
-keep class * extends dagger.internal.StaticInjection
-dontwarn kotlin.Unit
-keepclassmembers class ** {
    @dagger.Provides *;
}