# Kept deliberately minimal — library consumer rules handle Room and Compose.
# Anything added here must say why, because a broad keep rule silently undoes R8.

# kotlinx.serialization parses Bravura's glyph metadata reflectively over generated
# serializers; R8's shrinker cannot see the link from the @Serializable class to it.
-keepclassmembers class com.dewijones92.primavista.** {
    *** Companion;
}
-keepclasseswithmembers class com.dewijones92.primavista.** {
    kotlinx.serialization.KSerializer serializer(...);
}
