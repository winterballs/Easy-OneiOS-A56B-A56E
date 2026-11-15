package com.winterballs.oneios.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивает образ iOS 16.5 (2 части) с GitHub Releases.
 * Пишет прогресс через Flow. Сохраняет в /sdcard/Download/OneiOS/.
 */
object ImageDownloader {

    private const val BASE_URL =
        "https://github.com/Musicalista/OneiOS-A56/releases/download/v1.0.0"

    private const val PART00 = "ios16.5-a56-image.tar.gz.part00"
    private const val PART01 = "ios16.5-a56-image.tar.gz.part01"

    val downloadDir: File
        get() = File("/sdcard/Download/OneiOS").apply { mkdirs() }

    data class Progress(
        val part: Int,          // 1 или 2
        val bytesRead: Long,
        val totalBytes: Long,
        val done: Boolean = false,
        val error: String? = null,
    )

    /** Скачивает обе части последовательно, отдаёт прогресс. */
    fun downloadAll(): Flow<Progress> = flow {
        val parts = listOf(PART00, PART01)
        for ((idx, name) in parts.withIndex()) {
            val url = URL("$BASE_URL/$name")
            val target = File(downloadDir, name)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                conn.connect()
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                conn.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            emit(Progress(part = idx + 1, bytesRead = downloaded, totalBytes = total))
                        }
                    }
                }
            } catch (e: Exception) {
                emit(Progress(part = idx + 1, bytesRead = 0, totalBytes = 0, error = e.message))
                return@flow
            } finally {
                conn.disconnect()
            }
        }
        emit(Progress(part = 2, bytesRead = 0, totalBytes = 0, done = true))
    }.flowOn(Dispatchers.IO)
}
