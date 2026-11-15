package com.auratv.launcher

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val published: String
) {
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

data class MatchItem(
    val home: String,
    val away: String,
    val utc: String,
    val competition: String
)

data class WeatherItem(val temperature: Int, val code: Int)
