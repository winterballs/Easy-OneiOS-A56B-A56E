package com.winterballs.oneios.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Собирает образ из частей и раскатывает его в /data/local/tmp/oneios/image/.
 * Повторяет логику Manager.java#extractAndStage.
 */
object ImageStager {

    private const val DOWNLOAD_DIR = "/sdcard/Download/OneiOS"
    private const val STAGE = "/data/local/tmp/oneios"
    private const val IMAGE_DIR = "$STAGE/image"
    private const val MERGED = "$DOWNLOAD_DIR/ios16.5-a56-image.tar.gz"

    data class Result(val ok: Boolean, val output: String)

    /** Конкатенация частей + tar -xf + stage_ios16.sh (всё через su). */
    suspend fun stage(): Result = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("set -e; ")
            append("mkdir -p $IMAGE_DIR; ")
            append("if [ -f $DOWNLOAD_DIR/ios16.5-a56-image.tar.gz.part00 ] && [ ! -s $MERGED ]; then ")
            append("  cat $DOWNLOAD_DIR/ios16.5-a56-image.tar.gz.part* > $MERGED; ")
            append("fi; ")
            append("tar -xf $MERGED -C $IMAGE_DIR; ")
            append("chown -R 0:0 $IMAGE_DIR; ")
            append("chmod 644 $IMAGE_DIR/*; ")
            append("if [ -f $STAGE/stage_ios16.sh ]; then $STAGE/stage_ios16.sh; ")
            append("elif [ -f $STAGE/tools/stage_ios16.sh ]; then $STAGE/tools/stage_ios16.sh; fi")
        }
        try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            Result(code == 0, out)
        } catch (e: Exception) {
            Result(false, "Ошибка стейджинга: ${e.message}")
        }
    }
}
