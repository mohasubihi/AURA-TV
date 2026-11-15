package com.auratv.launcher

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Pure network operations. Never run on Android's main/UI thread. */
object RemoteData {
    const val UNITED_CHANNEL = "UC6yW44UGJJBvYTlfC7CRg2Q"

    private fun get(url: String, token: String? = null): String {
        require(url.startsWith("https://"))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "AURATV/3.0 (Android TV)")
        if (!token.isNullOrBlank()) conn.setRequestProperty("X-Auth-Token", token)
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conn.disconnect() }
    }

    fun weather(): WeatherItem {
        val json = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=24.7667&longitude=46.6383&current=temperature_2m,weather_code&timezone=Asia%2FRiyadh"))
            .getJSONObject("current")
        return WeatherItem(json.getDouble("temperature_2m").toInt(), json.getInt("weather_code"))
    }

    /** Public RSS feeds, no access to a YouTube account and no API key needed. */
    fun youtube(channels: List<String>): List<VideoItem> {
        val results = linkedMapOf<String, VideoItem>()
        channels.take(4).forEach { id ->
            if (!id.matches(Regex("UC[a-zA-Z0-9_-]{22}"))) return@forEach
            try {
                val xml = get("https://www.youtube.com/feeds/videos.xml?channel_id=$id")
                val p = Xml.newPullParser()
                p.setInput(xml.reader())
                var event = p.eventType
                var inEntry = false
                var videoId = ""
                var title = ""
                var channelTitle = "YouTube"
                var published = ""
                var author = "YouTube"
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (p.name.substringAfter(':')) {
                            "entry" -> { inEntry = true; videoId = ""; title = ""; published = ""; author = channelTitle }
                            "videoId" -> if (inEntry) videoId = p.nextText()
                            "published" -> if (inEntry) published = p.nextText()
                            "title" -> {
                                val text = p.nextText()
                                if (inEntry) title = text else channelTitle = text
                            }
                            "name" -> if (inEntry) author = p.nextText()
                        }
                    } else if (event == XmlPullParser.END_TAG && p.name.substringAfter(':') == "entry") {
                        if (videoId.matches(Regex("[a-zA-Z0-9_-]{11}")) && title.isNotBlank()) {
                            results[videoId] = VideoItem(videoId, title, author, published)
                        }
                        inEntry = false
                    }
                    event = p.next()
                }
            } catch (_: Exception) { /* Other configured channels may still load. */ }
        }
        return results.values.sortedByDescending { it.published }.take(18)
    }

    /** football-data.org requires a personal free API token, stored locally only. */
    fun nextMatch(token: String): MatchItem? {
        if (token.isBlank()) return null
        val data = JSONObject(get("https://api.football-data.org/v4/teams/66/matches?status=SCHEDULED&limit=10", token))
        val matches: JSONArray = data.optJSONArray("matches") ?: return null
        val now = System.currentTimeMillis()
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        for (index in 0 until matches.length()) {
            val match = matches.optJSONObject(index) ?: continue
            val utc = match.optString("utcDate")
            val time = try { parser.parse(utc)?.time ?: 0L } catch (_: Exception) { 0L }
            if (time < now) continue
            return MatchItem(
                match.optJSONObject("homeTeam")?.optString("shortName") ?: "",
                match.optJSONObject("awayTeam")?.optString("shortName") ?: "",
                utc,
                match.optJSONObject("competition")?.optString("name") ?: ""
            )
        }
        return null
    }
}
