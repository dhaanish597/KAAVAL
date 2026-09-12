# VAAKKU release ProGuard rules.
#
# NOTE: R8 is OFF for release (app/build.gradle.kts, build plan §6.1), so nothing
# here is currently applied. The file exists so that the release build type has a
# valid proguardFiles() target, and so that if R8 is ever switched on in a later
# phase the keeps are already recorded rather than rediscovered the hard way.
#
# Keeps that would be needed if minify is enabled:
#   - sherpa-onnx JNI entry points (com.k2fsa.sherpa.onnx.**)
#   - LiteRT / Qualcomm dispatch native bridges
#   - kotlinx.serialization generated serializers

-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
