package com.auratv.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Loads original YouTube CDN preview images at the highest available resolution.
 * Protects against reusing images on recycled/updated view targets.
 * Uses 32-bit ARGB pixels instead of the former RGB_565 color reduction.
 */
class ImageLoader {
    private val threads = Executors.newFixedThreadPool(3)
    private val memory = object : LruCache<String, Bitmap>(64 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun load(view: ImageView, url: String) {
        view.tag = url
        val cached = memory.get(url)
        if (cached != null) { view.setImageBitmap(cached); return }
        view.setImageDrawable(null)
        threads.execute {
            val bitmap = findImage(url)
            if (bitmap != null) {
                memory.put(url, bitmap)
                view.post { if (view.tag == url) view.setImageBitmap(bitmap) }
            }
        }
    }

    private fun findImage(url: String): Bitmap? {
        val candidates = if (url.contains("/maxresdefault.jpg")) listOf(
            url,
            url.replace("/maxresdefault.jpg", "/sddefault.jpg"),
            url.replace("/maxresdefault.jpg", "/hqdefault.jpg")
        ) else listOf(url)

        for (candidate in candidates) {
            val bitmap = try {
                if (!candidate.startsWith("https://i.ytimg.com/")) null
                else {
                    val connection = URL(candidate).openConnection() as HttpURLConnection
                    connection.connectTimeout = 6500
                    connection.readTimeout = 8500
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    try {
                        if (connection.responseCode != 200) null
                        else {
                            val bytes = connection.inputStream.use { it.readBytes() }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            })
                        }
                    } finally { connection.disconnect() }
                }
            } catch (_: Exception) { null }
            if (bitmap != null && bitmap.width >= 480) return bitmap
        }
        return null
    }

    fun shutdown() = threads.shutdownNow()
}
