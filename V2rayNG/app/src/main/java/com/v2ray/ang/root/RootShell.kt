package com.v2ray.ang.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal root command runner backed by the `su` binary.
 *
 * Scripts are written to the app's private root runtime dir and executed with
 * `su -c sh <file>` so shell quoting stays simple. stderr is merged into stdout to
 * avoid pipe-buffer deadlocks.
 */
object RootShell {

    data class Result(val code: Int, val output: String) {
        val success: Boolean get() = code == 0
    }

    /** Write [script] to `<filesDir>/root/<name>` and run it as root. */
    fun runScript(context: Context, name: String, script: String): Result {
        val dir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val file = File(dir, name).apply {
            writeText(script)
            setExecutable(true, false)
        }
        return exec("sh ${file.absolutePath}")
    }

    fun exec(command: String, timeoutSeconds: Long = 30): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val captured = StringBuilder()
            val reader = Thread({
                try {
                    process.inputStream.bufferedReader().use { input ->
                        val buffer = CharArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            synchronized(captured) {
                                if (captured.length < 65536) captured.append(buffer, 0, count.coerceAtMost(65536 - captured.length))
                            }
                        }
                    }
                } catch (_: Exception) { /* the timeout closes the pipe */ }
            }, "Root command output").apply { isDaemon = true; start() }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                reader.join(200)
                LogUtil.e(AppConfig.TAG, "RootShell: timed out: $command")
                return Result(-1, synchronized(captured) { captured.toString() })
            }
            reader.join(200)
            val result = Result(process.exitValue(), synchronized(captured) { captured.toString() })
            if (!result.success) {
                LogUtil.w(AppConfig.TAG, "RootShell: '$command' exited ${result.code}: ${result.output.trim()}")
            }
            result
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell: failed to run '$command'", e)
            Result(-1, e.message ?: e.javaClass.simpleName)
        }
    }
}
