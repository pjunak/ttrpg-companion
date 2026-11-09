# R8 rules for release builds. OkHttp, Coil and Hilt ship their own consumer
# rules; kotlinx.serialization needs its generated serializers kept.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class eu.junak.ttrpg.companion.** {
    *** Companion;
}
-keepclasseswithmembers class eu.junak.ttrpg.companion.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class eu.junak.ttrpg.companion.**$$serializer { *; }
