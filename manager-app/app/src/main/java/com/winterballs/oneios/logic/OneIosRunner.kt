package com.winterballs.oneios.logic

import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * Обёртка над вызовами oneios_root.sh через su.
 * Пути и команды повторяют Manager.java (original project).
 */
object OneIosRunner {

    // Главный switcher сессии (README.md:66, docs/a56-guide.md:67)
    const val SWITCH_SCRIPT = "/data/local/tmp/oneios/oneios_switch.sh"
    // Возврат в One UI: в OneiOS нет отдельного oneios_return.sh.
    // In-place возврат делает oneios_switch.sh --watch (внутренний режим),
    // аппаратный fallback — Volume Up + Volume Down (3 сек).
    // Надёжный программный путь из APK — перезагрузка (README: "Power + Volume Down").
    // Стейдж-скрипт и образ (docs/building.md:485)
    const val STAGE_SCRIPT = "/data/local/tmp/oneios/stage_ios16.sh"
    const val IMAGE_DIR = "/data/local/tmp/oneios/image"

    data class Result(val ok: Boolean, val output: String)

    /** Выполняет произвольную shell-команду через su. */
    suspend fun runShell(command: String): Result = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()
            Result(code == 0, output)
        } catch (e: Exception) {
            Result(false, "Ошибка выполнения: ${e.message}")
        }
    }

    /** Автоматическая установка (распаковка, патчи, хэши). */
    suspend fun runInstaller(context: android.content.Context): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Копируем скрипт из assets во временную папку
            val tempScript = File(context.cacheDir, "install_oneios.sh")
            context.assets.open("install_oneios.sh").use { input ->
                tempScript.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempScript.setExecutable(true)

            // 2. Копируем его в /data/local/tmp (через su)
            val copyCmd = "cp ${tempScript.absolutePath} /data/local/tmp/install_oneios.sh && chmod 755 /data/local/tmp/install_oneios.sh"
            val copyProcess = ProcessBuilder("su", "-c", copyCmd).redirectErrorStream(true).start()
            copyProcess.waitFor()

            // 3. Запускаем установку через su
            val process = ProcessBuilder("su", "-c", "/data/local/tmp/install_oneios.sh")
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()

            Result(code == 0, out)
        } catch (e: Exception) {
            Result(false, "Ошибка установки: ${e.message}")
        }
    }

    /** Проверка готовности системы (preflight only, ничего не меняет). */
    suspend fun checkStatus(): Result {
        return runShell("$SWITCH_SCRIPT --check")
    }

    /** Запуск iOS-сессии (oneios_switch.sh без аргументов). */
    suspend fun startSession(): Result {
        return runShell(SWITCH_SCRIPT)
    }

    /** Возврат в One UI: перезагрузка (OneiOS не даёт отдельного return-скрипта). */
    suspend fun stopSession(): Result {
        return runShell("reboot")
    }
}
