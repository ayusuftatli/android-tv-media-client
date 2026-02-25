# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class tv.ororo.app.**$$serializer { *; }
-keepclassmembers class tv.ororo.app.** { *** Companion; }
-keepclasseswithmembers class tv.ororo.app.** { kotlinx.serialization.KSerializer serializer(...); }
