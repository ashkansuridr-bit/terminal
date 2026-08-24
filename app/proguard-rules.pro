# JSch resolves algorithm implementations by class name at runtime.
-keep class com.jcraft.jsch.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

# termlib uses JNI callbacks into libvterm.
-keep class org.connectbot.terminal.** { *; }
