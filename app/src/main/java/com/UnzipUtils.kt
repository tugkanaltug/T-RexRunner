package com

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object UnzipUtils {
    fun unzip(context: Context, zipFileResId: Int, destDirectory: String) {
        val destDir = File(destDirectory)
        if (destDir.exists()) {
            return // Already unzipped
        }
        destDir.mkdirs()

        ZipInputStream(context.resources.openRawResource(zipFileResId)).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    FileOutputStream(newFile).use { fileOutputStream ->
                        zipInputStream.copyTo(fileOutputStream)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
    }
}
