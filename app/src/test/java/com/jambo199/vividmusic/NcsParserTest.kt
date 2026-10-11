package com.jambo199.vividmusic

import com.jambo199.vividmusic.online.ChartArtist
import com.jambo199.vividmusic.online.ChartEntry
import com.jambo199.vividmusic.online.ChartProgress
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsArtistItem
import com.jambo199.vividmusic.online.NcsArtistRef
import com.jambo199.vividmusic.online.NcsChart
import com.jambo199.vividmusic.online.NcsTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ncs.io music search, cut down from a real page. */
class NcsParserTest {
    private fun row(tid: String, title: String, artists: String, file: String, slug: String, date: String) = """
        <tr>     <td style="width:80px;"><a class="player-play" data-url="https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/$file.mp3" data-artist="<a href='/artist/1276/gabriawll' style='color:#191d24;'>gabriawll</a>, <a href='/artist/1512/ada-petcu' style='color:#191d24;'>Ada Petcu</a>" data-artistraw="$artists" data-track="$title" data-cover="https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/100x100/$file.png" data-preview="0" data-tid="$tid" data-versions="Regular, Instrumental" data-genre="Drum &amp; Bass"><i class="far fa-play-circle"></i></a></td>
        <td style="width:40px;"><span class="genre" style="background:#fff605" title="House"></span></td>
        <td style="width:74px;"><a href="/$slug">              <img src="https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/100x100/$file.png" alt="$title">          </a></td>
        <td style="width:25%;">         <a href="/$slug">             <p>$title</p></a></td>
        <td style="width:15%;"><a class="tag" style="background-color:#fff605" href="/music-search?genre=10">House</a><a class="tag" style="background-color:#d49613" href="/music-search?mood=32">powerful</a><a class="tag" href="/music-search?mood=6">energetic</a></td>
        <td style="width:15%;">$date</td>
        <td style="width:15%;">Regular</td>
        <td style="width:15%;"><a class="panel-btn btn black" data-panel="#download" data-tid="$tid"><i class="far fa-arrow-to-bottom"></i>Download</a></td>     </tr>
    """

    private val page = """
        <form><select name="genre" class="form-control"><option value="">Genre</option><option value="3">Drum &amp; Bass</option><option value="10">House</option></select>
        <select name="mood"><option value="">Mood</option><option value="6">energetic</option><option value="40">Angry</option><option value="41">angry</option></select></form>
        <table class="tablesorter"><thead><tr><th>Title</th></tr></thead><tbody>
        ${row("d06186d5-dda6", "Desire", "gabriawll, Ada Petcu", "desire-1791504076", "g_desire", "9 Oct 2026")}
        ${row("8ff25563-7d92", "Don&#039;t Stop", "Zack &amp; Co", "dont-stop-1791504079", "z_dontstop", "1 Sep 2026")}
        </tbody></table>
        <ul class="pagination"><li><a href="https://ncs.io/music-search?q=&amp;genre=&amp;mood=&amp;page=2">2</a></li>
        <li><a href="https://ncs.io/music-search?q=&amp;genre=&amp;mood=&amp;page=100">100</a></li></ul>
    """

    @Test
    fun readsTracks() {
        val result = Ncs.parse(page, 1)
        assertEquals(2, result.tracks.size)
        val first = result.tracks[0]
        assertEquals("d06186d5-dda6", first.id)
        assertEquals("Desire", first.title)
        assertEquals("gabriawll, Ada Petcu", first.artists)
        assertEquals("Drum & Bass", first.genre)
        assertEquals("9 Oct 2026", first.released)
        assertEquals("https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/desire-1791504076.mp3", first.url)
        assertEquals("https://ncs.io/g_desire", first.page)
        assertEquals("https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/325x325/desire-1791504076.png", first.cover)
        assertEquals("Don't Stop", result.tracks[1].title)
        assertEquals("Zack & Co", result.tracks[1].artists)
        assertEquals("NCS/Zack & Co - Don't Stop.mp3", result.tracks[1].downloadPath)
        assertEquals(listOf("Drum & Bass", "House"), first.genres)
        assertEquals(listOf("Powerful", "Energetic"), first.moods)
        assertEquals("Regular, Instrumental", first.versions)
        assertEquals(2026, first.year)
        assertEquals(
            listOf(NcsArtistRef("gabriawll", "/artist/1276/gabriawll"), NcsArtistRef("Ada Petcu", "/artist/1512/ada-petcu")),
            first.artistLinks,
        )
    }

    @Test
    fun artistPage() {
        val html = """
            <div class="img" style="background-image: url('https://ncsmusic.s3.eu-west-1.amazonaws.com/artists/000/000/013/325x325/1680848518_PmrrHgs9Ro_x.png')"></div>
            <div class="col-lg-6"> <div class="info"> <h5>Alan Walker</h5> <span class="tags">House, Melodic Dubstep, </span> <p></p> </div> </div>
            <h5>Popular Tracks</h5><table><thead><tr><th>Title</th></tr></thead><tbody>
            ${row("af3e020d", "Dreamer", "Alan Walker", "dreamer-1680825645", "dreamer", "7 Apr 2023")}
            </tbody></table>
        """
        val artist = Ncs.parseArtist(html, "/artist/13/alan-walker")
        assertEquals("Alan Walker", artist.name)
        assertEquals("House, Melodic Dubstep", artist.tags)
        assertEquals("https://ncsmusic.s3.eu-west-1.amazonaws.com/artists/000/000/013/325x325/1680848518_PmrrHgs9Ro_x.png", artist.image)
        assertEquals(listOf("Dreamer"), artist.tracks.map { it.title })
    }

    @Test
    fun trackPageCredit() {
        val html = """<div class="attribution"><p class="p-copy" id="panel-copy2">Song: Gabriawll - Desire ft Ada Petcu<br /> Music provided by NoCopyrightSounds<br /> Free Download/Stream: http://ncs.io/g_desire<br /> Watch: https://ncs.lnk.to/g_desireAT/youtube</p></div>"""
        val details = Ncs.parseDetails(html)
        assertEquals(
            "Song: Gabriawll - Desire ft Ada Petcu\nMusic provided by NoCopyrightSounds\nFree Download/Stream: http://ncs.io/g_desire\nWatch: https://ncs.lnk.to/g_desireAT/youtube",
            details.credit,
        )
        assertEquals("https://ncs.lnk.to/g_desireAT/youtube", details.video)
        assertEquals("", Ncs.parseDetails("<html></html>").credit)
    }

    @Test
    fun matchingOtherServicesNames() {
        assertEquals("Heroes Tonight", Ncs.baseTitle("Heroes Tonight (feat. Johnning)"))
        assertEquals("Royalty", Ncs.baseTitle("Royalty feat. Neoni"))
        assertEquals("Sky High (Carpe Remix)", Ncs.baseTitle("Sky High (Carpe Remix)"))
        val track = Ncs.parse(page, 1).tracks[0] // "Desire" by gabriawll, Ada Petcu
        assertTrue(Ncs.matches(track, "Desire", "Ada Petcu"))
        assertTrue(Ncs.matches(track, "Desire (feat. Ada Petcu)", "gabriawll"))
        assertFalse(Ncs.matches(track, "Desire (Slowed)", "gabriawll"))
        assertFalse(Ncs.matches(track, "Desire", "Someone Else"))
        // Only whole words of the artist names count.
        assertFalse(Ncs.matches(track, "Desire", "Petc"))
    }

    @Test
    fun deezerChart() {
        val json = """{"data":[{"id":1,"title":"Cradles","duration":209,"rank":456128,"artist":{"name":"Sub Urban"}},
            {"id":2,"title":"On & On","duration":207,"artist":{"name":" Cartoon"}}],"total":300,"next":"https://api.deezer.com/search?index=25"}"""
        val page = NcsChart.parse(json)
        assertEquals(listOf(ChartEntry("Cradles", "Sub Urban", 209_000), ChartEntry("On & On", "Cartoon", 207_000)), page.entries)
        assertTrue(page.hasMore)
        assertFalse(NcsChart.parse("""{"data":[],"total":300}""").hasMore)
        try {
            NcsChart.parse("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""")
            throw AssertionError("expected an error")
        } catch (e: java.io.IOException) {
            assertEquals("Quota limit exceeded", e.message)
        }
    }

    @Test
    fun keepsTracksBetweenRuns() {
        val track = Ncs.parse(page, 1).tracks[0].copy(durationMs = 209_000, rank = 3)
        assertEquals(track, NcsTrack.fromJson(track.toJson()))
        val file = java.io.File.createTempFile("chart", ".json")
        try {
            file.writeText(ChartProgress(listOf(track), nextIndex = 25, hasMore = true, savedAt = 1_000).toJson())
            assertEquals(listOf(track), ChartProgress.load(file, now = 2_000)?.tracks)
            // Older than three days: fetched again.
            assertNull(ChartProgress.load(file, now = 1_000 + ChartProgress.MAX_AGE_MS + 1))
        } finally {
            file.delete()
        }
    }

    @Test
    fun readsFiltersAndPages() {
        val result = Ncs.parse(page, 1)
        assertEquals(listOf("3" to "Drum & Bass", "10" to "House"), result.genres.map { it.id to it.name })
        assertEquals(listOf("Energetic", "Angry"), result.moods.map { it.name })
        assertEquals("40", result.moods[1].id)
        assertTrue(result.hasMore)
        // Page 10 must not take the link to page 100 for a link to page 11.
        assertFalse(Ncs.parse(page.replace("page=2\"", "page=9\""), 10).hasMore)
    }

    @Test
    fun emptyPage() {
        val result = Ncs.parse("<html><body><div class=\"waveform\" id=\"player\" data-url=\"\"></div></body></html>", 2)
        assertTrue(result.tracks.isEmpty())
        assertFalse(result.hasMore)
        assertTrue(result.genres.isEmpty())
    }

    @Test
    fun entities() {
        assertEquals("Tom & Jerry's \"song\" <3 é", Ncs.unescape("Tom &amp; Jerry&#039;s &quot;song&quot; &lt;3 &#xe9;"))
        assertEquals("&unknown;", Ncs.unescape("&unknown;"))
        assertNull(Ncs.parse(page, 1).tracks.firstOrNull { it.title.contains("&") })
    }

    @Test
    fun artistDirectory() {
        val html = """
            <article class="module artists"> <div class="container-fluid"> <div class="row">
            <div class="col-lg-2 item"> <a href="/artist/210/jim-yosef"> <div class="inner"> <div class="img" style="background-image: url('https://ncsmusic.s3.eu-west-1.amazonaws.com/artists/000/000/210/325x325/jim.png')"></div> </div>
            <div class="bottom"> <p><strong>Jim Yosef</strong></p> <span class="tags">Electro House, House, </span> </div> </a> </div>
            <div class="col-lg-2 item"> <a href="/artist/1511/zack-griffin"> <div class="inner"> <div class="img" style="background-image: url('/static/web/img/no-artist.png?_m=1693299337')"></div> </div>
            <div class="bottom"> <p><strong>Zack Griffin &amp; Co</strong></p> <span class="tags">Tech House</span> </div> </a> </div>
            </div></div></article>
            <a href="https://ncs.io/artists?page=2">2</a>
        """
        val page = Ncs.parseArtists(html, 1)
        assertEquals(
            listOf(
                NcsArtistItem("Jim Yosef", "/artist/210/jim-yosef", "https://ncsmusic.s3.eu-west-1.amazonaws.com/artists/000/000/210/325x325/jim.png", "Electro House, House"),
                NcsArtistItem("Zack Griffin & Co", "/artist/1511/zack-griffin", null, "Tech House"),
            ),
            page.artists,
        )
        assertTrue(page.hasMore)
        assertFalse(Ncs.parseArtists(html, 2).hasMore)
        assertTrue(Ncs.parseArtists("<html></html>", 1).artists.isEmpty())
    }

    @Test
    fun artistsRankedByPlays() {
        val chart = listOf(
            ChartEntry("Cradles", "Sub Urban", 0, "https://pic/sub"),
            ChartEntry("Sky High", "Elektronomia", 0, "https://pic/elek"),
            ChartEntry("Firefly", "Jim Yosef", 0),
            ChartEntry("Link", "Jim Yosef", 0, "https://pic/jim"),
            ChartEntry("Energy", "Elektronomia", 0),
            ChartEntry("Untitled", "", 0),
        )
        val ranked = NcsChart.rankArtists(chart)
        // Two hits beat a single one placed just above them.
        assertEquals(listOf("Elektronomia", "Jim Yosef", "Sub Urban"), ranked.map { it.name })
        assertEquals(ChartArtist("Elektronomia", "https://pic/elek", songs = 2, topSong = "Sky High", bestPlace = 2), ranked[0])
        assertEquals("https://pic/jim", ranked[1].picture)
    }

    @Test
    fun chartKeepsArtistPictures() {
        val json = """{"data":[{"title":"Sky High","duration":236,"artist":{"name":"Elektronomia","picture_medium":"https://cdn/elek.jpg"}}]}"""
        assertEquals("https://cdn/elek.jpg", NcsChart.parse(json).entries.single().artistPicture)
    }
}
