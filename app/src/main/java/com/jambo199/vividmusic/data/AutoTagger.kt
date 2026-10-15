package com.jambo199.vividmusic.data

import android.content.Context

/** One song through auto-tag: looked up, the tags it should have worked out, and written. */
object AutoTagger {
    sealed interface Outcome {
        data class Tagged(val source: String) : Outcome
        data object AlreadyRight : Outcome
        data object NotSure : Outcome
        data class Failed(val why: String) : Outcome
    }

    /** Blocking file and network work inside; call it off the main thread. */
    suspend fun tag(context: Context, song: Song, settings: Settings, lookup: TagLookup, artwork: ArtworkLoader): Outcome {
        val now = TagEdits.of(song)
        val found: TagMatch? = if (settings.tagOnline) {
            val search = lookup.search(TagQuery.of(song), batch = true)
            if (search.matches.isEmpty() && search.unreachable.size >= 3) return Outcome.Failed("couldn't reach the music catalogues")
            TagLookup.best(search.matches, settings.tagMinMatch)?.let { lookup.complete(it) }
        } else {
            LocalTags.guess(song.fileName, LocalTags.folders(song.path))
        }
        val match = found ?: return Outcome.NotSure
        val edits = now.with(match, settings.tagFillOnly, song.fileName.substringBeforeLast('.'))
        val wantsCover = settings.tagOnline && settings.tagCovers && match.artworkUrl != null &&
            (!settings.tagFillOnly || artwork.embeddedBytes(song.uri) == null)
        val cover = if (wantsCover) lookup.artwork(match) else null
        if (edits == now && cover == null) return Outcome.AlreadyRight
        return when (val result = TagEditor.write(context, song, edits, cover)) {
            TagEditor.Result.Done -> Outcome.Tagged(match.source)
            is TagEditor.Result.NeedsPermission -> Outcome.Failed("Android didn't allow changing it")
            is TagEditor.Result.Failed -> Outcome.Failed(result.message)
        }
    }
}
