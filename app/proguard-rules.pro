# Media3, Coil and Compose ship their own consumer rules.
# Keep the playback service and receiver names referenced from the manifest (AAPT does this too).
-keep class com.jambo199.vividmusic.playback.PlaybackService { *; }

# jaudiotagger (tag editor) builds tag frames by class name, and has desktop-only image code we
# never call (it switches to Android image handling at startup).
-keep class org.jaudiotagger.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn javax.sound.**
