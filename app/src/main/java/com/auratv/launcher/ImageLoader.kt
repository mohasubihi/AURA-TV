package com.auratv.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Bounded in-memory cache; original YouTube CDN image files, never generated posters. */
class ImageLoader {
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(view: ImageView, url: String) {
        view.tag = url
        cache.get(url)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        pool.execute {
            val bitmap = try {
                if (!url.startsWith("https://")) null else {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 6500
                    connection.readTimeout = 8000
                    try {
                        if (connection.responseCode == 200) {
                            val bytes = connection.inputStream.use { it.readBytes() }
                            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        } else null
                    } finally { connection.disconnect() }
                }
            } catch (_: Exception) { null }
            if (bitmap != null) {
                cache.put(url, bitmap)
                view.post { if (view.tag == url) view.setImageBitmap(bitmap) }
            }
        }
    }

    fun shutdown() = pool.shutdownNow()
}
