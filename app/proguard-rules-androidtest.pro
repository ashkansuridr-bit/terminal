# Keep rules that apply ONLY when the instrumentation suite is pointed at a minified
# build (-Pterminal.testBuildType=preview). They are not part of a shipped preview.
#
# Why this file exists: androidTest code lives in a separate, unminified APK that
# addresses the app under test by its original class names. R8 renames those names,
# so without these keeps the test APK fails at runtime with NoClassDefFoundError and
# the minified Ed25519 path can never be proven.
#
# Scope is deliberately narrow — only the types KeyGenerationTest names directly.
# Everything else stays subject to normal shrinking, so the R8 shape being exercised
# stays as close as possible to the real preview/release build.
# The `$**` half matters: Kotlin puts supported()/default() on KeyAlgorithm$Companion, and
# keeping only the enum leaves the test calling supported$default() on a renamed companion.
-keep class app.terminalssh.secure.security.KeyAlgorithm { *; }
-keep class app.terminalssh.secure.security.KeyAlgorithm$** { *; }
-keep class app.terminalssh.secure.security.KeyGeneration { *; }
-keep class app.terminalssh.secure.security.KeyGeneration$** { *; }
-keep class app.terminalssh.secure.security.GeneratedKey { *; }
-keep class app.terminalssh.secure.security.GeneratedKey$** { *; }
-keep class app.terminalssh.secure.security.PrivateKeyFormat { *; }
-keep class app.terminalssh.secure.security.PrivateKeyFormat$** { *; }

# The androidTest APK resolves its own infrastructure out of the APP APK rather than
# packaging a second copy. Nothing in the app calls these, so R8 drops them and the
# instrumentation process dies in AndroidJUnitRunner.onCreate before any test starts
# (androidx.tracing.Trace first, then kotlin.LazyKt from TestDirCalculator, and so on).
# Keeping them changes only what survives shrinking, never how the security classes
# under test are compiled — the Ed25519/JSch/BC path is shrunk exactly as it ships.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-dontwarn kotlin.**
