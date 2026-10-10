# Media3, Coil and Compose ship their own consumer rules.
# Keep the playback service and receiver names referenced from the manifest (AAPT does this too).
-keep class com.jambo199.vividmusic.playback.PlaybackService { *; }
