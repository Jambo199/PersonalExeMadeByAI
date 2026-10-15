package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.LocalTags
import com.jambo199.vividmusic.data.TagEdits
import com.jambo199.vividmusic.data.TagLookup
import com.jambo199.vividmusic.data.TagMatch
import com.jambo199.vividmusic.data.TagQuery
import com.jambo199.vividmusic.data.TagScore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding tags for a song: from its file and folder names, and from the online catalogues' answers. */
class TagLookupTest {
    private fun guess(path: String) = LocalTags.guess(path.substringAfterLast('/'), LocalTags.folders(path))

    @Test
    fun readsFileAndFolderNames() {
        val a = guess("/storage/emulated/0/Music/Daft Punk/Discovery (2001)/01 - One More Time.mp3")!!
        assertEquals("One More Time", a.title)
        assertEquals("Daft Punk", a.artist)
        assertEquals("Discovery", a.album)
        assertEquals("2001", a.year)
        assertEquals("1", a.track)

        val b = guess("/storage/emulated/0/Download/Rick Astley - Never Gonna Give You Up (Official Music Video).mp3")!!
        assertEquals("Rick Astley", b.artist)
        assertEquals("Never Gonna Give You Up", b.title)
        assertEquals("", b.album)

        val c = guess("/sdcard/Music/Pink Floyd - The Wall/1-03 Another Brick in the Wall.flac")!!
        assertEquals("Another Brick in the Wall", c.title)
        assertEquals("The Wall", c.album)
        assertEquals("Pink Floyd", c.artist)
        assertEquals("3", c.track)

        val d = guess("/sdcard/Music/07. Artist Name - Song_Title.mp3")!!
        assertEquals("7", d.track)
        assertEquals("Artist Name", d.artist)
        assertEquals("Song Title", d.title)
    }

    @Test
    fun aBandWithANumberIsntATrackNumber() {
        val g = guess("/sdcard/Download/10 Years - Wasteland.mp3")!!
        assertEquals("10 Years", g.artist)
        assertEquals("Wasteland", g.title)
        assertEquals("", g.track)
        assertEquals("2 Unlimited", guess("/sdcard/Download/2 Unlimited - No Limit.mp3")!!.artist)
    }

    @Test
    fun saysNothingWhenTheNameSaysNothing() {
        // Only a title, in a folder that says nothing.
        assertNull(guess("/sdcard/Download/Some Song.mp3"))
        assertNull(guess("/sdcard/Music/voice note.m4a"))
    }

    @Test
    fun comparesNames() {
        assertEquals(1.0, TagScore.similarity("One More Time", "one more time"), 0.001)
        assertTrue(TagScore.similarity("Beyoncé", "Beyonce") > 0.99)
        assertTrue(TagScore.similarity("One More Time", "One More Time (Radio Edit)") > 0.99)
        assertTrue(TagScore.similarity("Stairway to Heaven", "Stairway To Heaven - Remaster") >= 0.85)
        assertTrue(TagScore.similarity("Time", "One More Time") < 0.6)
        assertTrue(TagScore.similarity("Hello", "Goodbye") < 0.4)
        assertTrue(TagScore.artistSimilarity("Daft Punk", "Daft Punk, Romanthony") >= 0.9)
        assertTrue(TagScore.artistSimilarity("Calvin Harris feat. Rihanna", "Calvin Harris") >= 0.95)
    }

    @Test
    fun scoresMatches() {
        val q = TagQuery("Never Gonna Give You Up", "Rick Astley", "", 213_000)
        val right = TagMatch("iTunes", "Never Gonna Give You Up", "Rick Astley", "Whenever You Need Somebody", durationMs = 213_573)
        val live = right.copy(title = "Never Gonna Give You Up (Live)", durationMs = 290_000)
        val cover = right.copy(artist = "Some Cover Band")
        assertTrue("the right one: ${TagScore.score(q, right)}", TagScore.score(q, right) >= 95)
        assertTrue("a live version: ${TagScore.score(q, live)}", TagScore.score(q, live) <= 60)
        assertTrue("another artist: ${TagScore.score(q, cover)}", TagScore.score(q, cover) < 85)
        // No artist known: a matching title alone is never sure enough to pick by itself.
        val noArtist = TagQuery("Intro", "", "", 60_000)
        assertTrue(TagScore.score(noArtist, TagMatch("Deezer", "Intro", "The xx", durationMs = 60_000)) <= 75)
    }

    @Test
    fun ranksAndPicksTheBest() {
        val q = TagQuery("One More Time", "Daft Punk", "", 320_000)
        val ranked = TagLookup.rank(
            q,
            listOf(
                TagMatch("MusicBrainz", "One More Time", "Daft Punk", "Discovery", durationMs = 320_000),
                TagMatch("iTunes", "One More Time", "Daft Punk", "Discovery", genre = "Electronic", year = "2001", track = "1", durationMs = 320_357, artworkUrl = "x"),
                TagMatch("Deezer", "One More Time (Live)", "Daft Punk", "Alive 2007", durationMs = 600_000),
            ),
        )
        // The same song from two places becomes one, the better-filled one.
        assertEquals(2, ranked.size)
        assertEquals("iTunes", ranked[0].source)
        assertEquals("Alive 2007", ranked[1].album)
        assertEquals(ranked[0], TagLookup.best(ranked, 85))
        assertNull(TagLookup.best(ranked.drop(1), 85))
    }

    @Test
    fun fillsInOnlyWhatsMissing() {
        val now = TagEdits("Track 01", "", "My Album", "", "", "", "")
        val m = TagMatch("iTunes", "Real Title", "Real Artist", "Real Album", "Real Artist", "Pop", "1999", "4")
        val filled = now.with(m, fillOnly = true, fileBase = "Track 01")
        // The title was only the file's name, so it counts as missing; the album stays.
        assertEquals(TagEdits("Real Title", "Real Artist", "My Album", "Real Artist", "Pop", "1999", "4"), filled)
        val replaced = now.with(m, fillOnly = false)
        assertEquals("Real Album", replaced.album)
        assertEquals("Real Title", replaced.title)
        // A match missing a field never blanks one out.
        assertEquals("My Album", now.with(m.copy(album = ""), fillOnly = false).album)
        assertEquals("Kept", TagEdits("Kept", "<unknown>", "", "", "", "", "").with(m, fillOnly = true).title)
        assertEquals("Real Artist", TagEdits("Kept", "<unknown>", "", "", "", "", "").with(m, fillOnly = true).artist)
    }

    @Test
    fun readsITunes() {
        val body = """{"resultCount":1,"results":[{"wrapperType":"track","kind":"song","artistName":"Rick Astley",
            "collectionName":"Whenever You Need Somebody - Single","trackName":"Never Gonna Give You Up",
            "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/x/100x100bb.jpg","releaseDate":"1987-07-27T07:00:00Z",
            "trackTimeMillis":213573,"trackNumber":1,"primaryGenreName":"Pop"}]}"""
        val m = TagLookup.Parse.iTunes(body).single()
        assertEquals("Never Gonna Give You Up", m.title)
        assertEquals("Whenever You Need Somebody", m.album)
        assertEquals("Pop", m.genre)
        assertEquals("1987", m.year)
        assertEquals("1", m.track)
        assertEquals(213_573L, m.durationMs)
        assertTrue(m.artworkUrl!!.endsWith("1000x1000bb.jpg"))
        assertTrue(m.thumbUrl!!.endsWith("200x200bb.jpg"))
    }

    @Test
    fun readsDeezer() {
        val body = """{"data":[{"id":3135556,"title":"Harder, Better, Faster, Stronger","duration":224,
            "artist":{"id":27,"name":"Daft Punk"},
            "album":{"id":302127,"title":"Discovery","cover_medium":"https://e-cdns/m.jpg","cover_xl":"https://e-cdns/xl.jpg"}}],"total":1}"""
        val m = TagLookup.Parse.deezer(body).single()
        assertEquals("Harder, Better, Faster, Stronger", m.title)
        assertEquals("Daft Punk", m.artist)
        assertEquals("Discovery", m.album)
        assertEquals(224_000L, m.durationMs)
        assertEquals("https://e-cdns/xl.jpg", m.artworkUrl)
        assertEquals(3135556L, m.deezerTrack)
        val full = TagLookup.Parse.deezerDetails(
            m,
            JSONObject("""{"track_position":4,"disk_number":1,"release_date":"2001-03-07","album":{"id":302127}}"""),
            JSONObject("""{"genres":{"data":[{"id":113,"name":"Dance"}]},"release_date":"2001-03-07","artist":{"name":"Daft Punk"}}"""),
        )
        assertEquals("4", full.track)
        assertEquals("2001", full.year)
        assertEquals("Dance", full.genre)
        assertEquals("Daft Punk", full.albumArtist)
        assertTrue(runCatching { TagLookup.Parse.deezer("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""") }.isFailure)
    }

    @Test
    fun readsMusicBrainz() {
        val body = """{"recordings":[{"id":"r1","score":100,"title":"Wonderwall","length":258000,"first-release-date":"1995-10-02",
            "artist-credit":[{"name":"Oasis","artist":{"name":"Oasis"}}],
            "tags":[{"count":3,"name":"britpop"},{"count":1,"name":"rock"}],
            "releases":[
              {"id":"comp","title":"Now 32","status":"Official","date":"1995-11-20",
               "release-group":{"id":"g2","primary-type":"Album","secondary-types":["Compilation"]},
               "media":[{"position":1,"track":[{"number":"5","title":"Wonderwall"}],"track-count":40}]},
              {"id":"alb","title":"(What's the Story) Morning Glory?","status":"Official","date":"1995-10-02",
               "release-group":{"id":"g1","primary-type":"Album"},
               "media":[{"position":1,"track":[{"number":"3","title":"Wonderwall"}],"track-count":12}]}]}]}"""
        val m = TagLookup.Parse.musicBrainz(body).single()
        assertEquals("Wonderwall", m.title)
        assertEquals("Oasis", m.artist)
        // The band's own album, not the compilation.
        assertEquals("(What's the Story) Morning Glory?", m.album)
        assertEquals("3", m.track)
        assertEquals("1995", m.year)
        assertEquals("Britpop", m.genre)
        assertEquals("https://coverartarchive.org/release/alb/front-1200", m.artworkUrl)
        assertEquals("https://coverartarchive.org/release-group/g1/front-1200", m.artworkFallback)
    }

    @Test
    fun knowsAPictureWhenItSeesOne() {
        assertTrue(TagLookup.isImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 0, 0)))
        assertTrue(TagLookup.isImage(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0, 0)))
        assertFalse(TagLookup.isImage("<html>Not found</html>".toByteArray()))
    }

    @Test
    fun searchesWithoutTheClutter() {
        assertEquals("Never Gonna Give You Up", TagLookup.searchTitle("Never Gonna Give You Up (Official Music Video)"))
        assertEquals("Get Lucky", TagLookup.searchTitle("Get Lucky (feat. Pharrell Williams)"))
    }
}
