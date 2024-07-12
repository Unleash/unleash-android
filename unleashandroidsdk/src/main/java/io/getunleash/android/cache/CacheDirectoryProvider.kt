package io.getunleash.android.cache

import android.os.Environment
import java.io.File
import java.io.IOException


class CacheDirectoryProvider {

    fun getCacheDirectory(): File = try {
        createTempDirectory()
    } catch (e: NoClassDefFoundError) {
        val file = getCacheDirectoryFile()
        createDirectoryIfNotExists(file)
        file
    }

    private fun createTempDirectory(): File {
        val storageDir: File = Environment.getDataDirectory() // Use internal storage directory
        val tempDirName = "unleash_toggles_" + System.currentTimeMillis() // Unique directory name
        val tempDir = File(storageDir, tempDirName)
        if (!tempDir.mkdir()) {
            throw IOException("Failed to create temporary directory: " + tempDir.absolutePath)
        }
        return tempDir
    }

    private fun getCacheDirectoryFile() = File("unleash_toggles")

    private fun createDirectoryIfNotExists(file: File) {
        if (!file.exists())
            createDirectoryAndAddShutdownHook(file)
    }

    private fun createDirectoryAndAddShutdownHook(file: File) {
        file.mkdirs()
        Runtime.getRuntime().addShutdownHook(getShutdownHook(file))
    }

    private fun getShutdownHook(file: File): Thread {
        return DeleteFileShutdownHook(file)
    }

    private class DeleteFileShutdownHook(file: File) : Thread(Runnable {
        file.deleteRecursively()
    })
}