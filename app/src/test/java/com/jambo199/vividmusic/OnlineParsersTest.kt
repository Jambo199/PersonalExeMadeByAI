package com.jambo199.vividmusic

import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Http
import com.jambo199.vividmusic.online.InternetArchive
import com.jambo199.vividmusic.online.Podcasts
import com.jambo199.vividmusic.online.RadioBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineParsersTest {
    @Test
    fun radioStationsSkipBrokenAndDuplicateStreams() {
        val json = """[
            {"stationuuid":"a","name":" Jazz FM ","url":"http://x/jazz.pls","url_resolved":"https://stream.example/jazz",
             "favicon":"https://example/jazz.png","country":"United Kingdom","countrycode":"GB","tags":"jazz, smooth jazz,",
             "codec":"MP3","bitrate":128,"hls":0},
            {"stationuuid":"b","name":"Jazz FM mirror","url_resolved":"https://stream.example/jazz","codec":"MP3"},
            {"stationuuid":"c","name":"No stream","url_resolved":"","url":"ftp://nope"},
            {"stationuuid":"d","name":"News","url_resolved":"https://news.example/live/master.m3u8","codec":"UNKNOWN","hls":0}
        ]"""
        val stations = RadioBrowser.parseStations(json)
        assertEquals(listOf("a", "d"), stations.map { it.id })
        val jazz = stations[0]
        assertEquals("Jazz FM", jazz.name)
        assertEquals(listOf("jazz", "smooth jazz"), jazz.tags)
        assertEquals("Jazz, Smooth jazz · United Kingdom · MP3 128 kbps", jazz.subtitle)
        assertFalse(jazz.hls)
        // HLS spotted from the address even when the directory doesn't flag it; "UNKNOWN" codec hidden.
        assertTrue(stations[1].hls)
        assertEquals("", stations[1].codec)
    }

    @Test
    fun archiveSearchHandlesListsAndStrings() {
        val json = """{"response":{"docs":[
            {"identifier":"gd1977-05-08","title":"Grateful Dead Live at Barton Hall","creator":"Grateful Dead","date":"1977-05-08T00:00:00Z"},
            {"identifier":"netlabel-1","title":["Album"],"creator":["A","B"]},
            {"title":"no id"}
        ]}}"""
        val items = InternetArchive.parseSearch(json)
        assertEquals(2, items.size)
        assertEquals("1977", items[0].year)
        assertEquals("A, B", items[1].creator)
        assertEquals("", items[1].year)
        assertEquals("https://archive.org/services/img/gd1977-05-08", items[0].cover)
    }

    @Test
    fun archiveAlbumPicksOneFormatInTrackOrder() {
        val json = """{"metadata":{"title":"Live 2001","creator":"Some Band","date":"2001-06-01",
              "licenseurl":"https://creativecommons.org/licenses/by-nc-sa/4.0/","description":"<p>Great&nbsp;show</p><br/>Set 1"},
            "files":[
              {"name":"sb2001d1t02.mp3","format":"VBR MP3","title":"Second","track":"02","length":"125.5"},
              {"name":"sb2001d1t01.mp3","format":"VBR MP3","title":"First","track":"1/12","length":"03:05"},
              {"name":"sb2001d1t01.flac","format":"Flac","title":"First"},
              {"name":"cover.jpg","format":"JPEG"},
              {"name":"disc 2/encore.mp3","format":"VBR MP3"}
            ]}"""
        val album = InternetArchive.parseAlbum("sb2001", json)
        assertEquals(listOf("First", "Second", "encore"), album.tracks.map { it.title })
        assertEquals(185_000L, album.tracks[0].durationMs)
        assertEquals(125_500L, album.tracks[1].durationMs)
        assertEquals("https://archive.org/download/sb2001/disc%202/encore.mp3", album.tracks[2].url)
        assertEquals("Creative Commons BY-NC-SA", InternetArchive.licenseName(album.license))
        assertEquals("Great show\n\nSet 1", album.description)
        assertEquals("Vivid Music/Some Band/Live 2001/01 - First.mp3", album.downloadPath(album.tracks[0]))
    }

    @Test
    fun licenceNames() {
        assertEquals("Public domain", InternetArchive.licenseName("http://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("See licence", InternetArchive.licenseName("https://example.com/terms"))
        assertNull(InternetArchive.licenseName(""))
    }

    @Test
    fun podcastDirectorySkipsShowsWithoutFeeds() {
        val json = """{"results":[
            {"collectionName":"Show A","artistName":"Host","feedUrl":"https://a/feed.xml","artworkUrl600":"https://a/600.jpg"},
            {"collectionName":"No feed","artistName":"x"}
        ]}"""
        val shows = Podcasts.parseDirectory(json)
        assertEquals(1, shows.size)
        assertEquals("https://a/600.jpg", shows[0].artwork)
    }

    @Test
    fun podcastDurationsAndDates() {
        assertEquals(3_723_000L, Podcasts.parseDuration("1:02:03"))
        assertEquals(3_723_000L, Podcasts.parseDuration("62:03"))
        assertEquals(3_600_000L, Podcasts.parseDuration("3600"))
        assertEquals(0L, Podcasts.parseDuration("about an hour"))
        assertEquals(1_700_000_000_000L, Podcasts.parseDate("Tue, 14 Nov 2023 22:13:20 +0000"))
        assertEquals(0L, Podcasts.parseDate("yesterday"))
    }

    @Test
    fun safeFileNamesAndPaths() {
        assertEquals("AC_DC_ Live", Downloads.safeName("AC/DC: Live"))
        assertEquals("Untitled", Downloads.safeName("..."))
        assertEquals("a%20b/c%26d.mp3", Http.encodePath("a b/c&d.mp3"))
    }
}
