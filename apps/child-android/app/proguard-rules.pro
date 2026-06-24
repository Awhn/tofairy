# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class app.tofairy.child.** {
    *** Companion;
}
-keepclasseswithmembers,allowshrinking class app.tofairy.child.** {
    kotlinx.serialization.KSerializer serializer(...);
}
