package com.t34400.questcamera.io

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataDirectoryManager (
    private var context: Context,
    directoryName: String? = null
) {
    private var directory = createTimestampedDirectory(context, directoryName)

    fun getDirectoryPath() : String {
        return directory?.absolutePath ?: ""
    }

    fun getFile(fileName: String) : File? {
        return directory?.let { dir ->
            val file = File(dir, fileName)
            file.parentFile?.mkdirs()

            return file
        }
    }

    fun recreateDirectory(directoryName: String? = null) {
        directory = createTimestampedDirectory(context, directoryName)
    }

    fun registerContext(context: Context) {
        this.context = context
    }

    companion object {
        fun createTimestampedDirectory(context: Context, directoryName: String? = null): File? {
            val dirName = if (directoryName?.isNotEmpty() == true) {
                directoryName
            } else {
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            }

            val baseDir = context.getExternalFilesDir(null)
            val folder = File(baseDir, dirName)

            if (!folder.exists()) {
                val created = folder.mkdirs()
                if (!created) {
                    return null
                }
            }

            return folder
        }
    }
}