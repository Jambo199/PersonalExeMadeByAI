package com.jambo199.vividmusic.online

import com.jambo199.vividmusic.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small blocking HTTP helper for the online features. Call it off the main thread. */
object Http {
    val USER_AGENT = "VividMusic/${BuildConfig.VERSION_NAME} (Android; https://github.com/Jambo199/PersonalExeMadeByAI)"

    /** GET returning the body; follows redirects (also http to https). Throws IOException on failure. */
    fun get(url: String, accept: String = "application/json", readTimeoutMs: Int = 20_000): String {
        var target = url
        repeat(MAX_REDIRECTS) {
            val conn = URL(target).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = readTimeoutMs
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", accept)
                when (val code = conn.responseCode) {
                    in 200..299 -> return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    301, 302, 303, 307, 308 -> {
                        val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                        target = URL(URL(target), location).toString()
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** Encodes each part of a path, keeping the slashes. */
    fun encodePath(path: String): String = path.split('/').joinToString("/") { encode(it).replace("+", "%20") }

    private const val MAX_REDIRECTS = 6
}

/** A list that's being fetched, has arrived, or couldn't be fetched. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

fun Throwable.friendlyMessage(): String = when (this) {
    is java.net.UnknownHostException -> "No internet connection"
    is java.net.SocketTimeoutException -> "The server took too long to answer"
    is IOException -> "Couldn't connect (${message ?: "network error"})"
    else -> "Something went wrong (${javaClass.simpleName})"
}
