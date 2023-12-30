package io.github.sauvio.ocr.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.UUID

object ImageFileProvider {

    fun authority(context: Context): String {
        return String.format("%s.image-choose.provider", context.packageName)
    }

    fun filesDir(context: Context): File? {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if ((picturesDir != null) && !picturesDir.exists()) picturesDir.mkdirs()
        return picturesDir!!
    }

    fun file(context: Context, extension: String): File? {
        return try {
            File.createTempFile(UUID.randomUUID().toString(), extension, filesDir(context))
        } catch (ignored: Exception) {
            null
        }
    }
}