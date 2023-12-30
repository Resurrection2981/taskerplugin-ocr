package io.github.sauvio.ocr.utils

import android.content.Context
import android.text.TextUtils
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashUtils(context: Context, crashReportSavePath: String) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val EXCEPTION_SUFFIX = "_exception"
        private const val CRASH_SUFFIX = "_crash"
        private const val FILE_EXTENSION = ".txt"
        private const val CRASH_REPORT_DIR = "crashReports"
        private const val TAG = "CrashUtils"
    }

    private val exceptionHandler: Thread.UncaughtExceptionHandler =
        Thread.getDefaultUncaughtExceptionHandler()
    private val applicationContext: Context = context
    private val crashReportPath: String = crashReportSavePath

    init {
        if (Thread.getDefaultUncaughtExceptionHandler() !is CrashUtils) {
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        saveCrashReport(throwable)
        exceptionHandler.uncaughtException(thread, throwable)
    }

    private fun saveCrashReport(throwable: Throwable) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val filename = dateFormat.format(Date()) + CRASH_SUFFIX + FILE_EXTENSION
        writeToFile(crashReportPath, filename, getStackTrace(throwable))
    }

    private fun writeToFile(crashReportPath: String, filename: String, crashLog: String) {
        var path = crashReportPath
        if (TextUtils.isEmpty(path)) {
            path = getDefaultPath()
        }
        val crashDir = File(path)
        if (!crashDir.exists() || !crashDir.isDirectory) {
            path = getDefaultPath()
            Log.e(
                TAG,
                "Path provided doesn't exist: $crashDir\nSaving crash report at: ${getDefaultPath()}"
            )
        }
        try {
            BufferedWriter(FileWriter("$path/$filename")).use { bufferedWriter ->
                bufferedWriter.write(crashLog)
                bufferedWriter.flush()
            }
            Log.d(TAG, "Crash report saved in: $path")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getStackTrace(e: Throwable): String {
        val result = StringWriter()
        PrintWriter(result).use { printWriter ->
            e.printStackTrace(printWriter)
        }
        return result.toString()
    }

    private fun getDefaultPath(): String {
        val defaultPath =
            "${applicationContext.getExternalFilesDir(null)?.absolutePath}/$CRASH_REPORT_DIR"
        val file = File(defaultPath)
        file.mkdirs()
        return defaultPath
    }

    fun logException(exception: Exception) {
        Thread {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val filename = dateFormat.format(Date()) + EXCEPTION_SUFFIX + FILE_EXTENSION
            writeToFile(crashReportPath, filename, getStackTrace(exception))
        }.start()
    }
}
