package com.jambo199.vividmusic

import com.jambo199.vividmusic.online.Ncs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ncs.io music search, cut down from a real page. */
class NcsParserTest {
    private fun row(tid: String, title: String, artists: String, file: String, slug: String, date: String) = """
        <tr>     <td style="width:80px;"><a class="player-play" data-url="https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/$file.mp3" data-artist="<a href='/artist/1276/x' style='color:#191d24;'>x</a>" data-artistraw="$artists" data-track="$title" data-cover="https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/100x100/$file.png" data-preview="0" data-tid="$tid" data-versions="Regular" data-genre="Drum &amp; Bass"><i class="far fa-play-circle"></i></a></td>
        <td style="width:40px;"><span class="genre" style="background:#fff605" title="House"></span></td>
        <td style="width:74px;"><a href="/$slug">              <img src="https://ncsmusic.s3.eu-west-1.amazonaws.com/tracks/000/002/198/100x100/$file.png" alt="$title">          </a></td>
        <td style="width:25%;">         <a href="/$slug">             <p>$title</p></a></td>
        <td style="width:15%;"><a class="tag" href="/music-search?genre=10">House</a></td>
        <td style="width:15%;">$date</td>
        <td style="width:15%;">Regular</td>
        <td style="width:15%;"><a class="panel-btn btn black" data-panel="#download" data-tid="$tid"><i class="far fa-arrow-to-bottom"></i>Download</a></td>     </tr>
    """

    private val page = """
        <form><select name="genre" class="form-control"><option value="">Genre</option><option value="3">Drum &amp; Bass</option><option value="10">House</option></select>
        <select name="mood"><option value="">Mood</option><option value="6">energetic</option></select></form>
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
    }

    @Test
    fun readsFiltersAndPages() {
        val result = Ncs.parse(page, 1)
        assertEquals(listOf("3" to "Drum & Bass", "10" to "House"), result.genres.map { it.id to it.name })
        assertEquals(listOf("energetic"), result.moods.map { it.name })
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
}
