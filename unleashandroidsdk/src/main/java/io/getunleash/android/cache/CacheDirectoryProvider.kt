package io.getunleash.android.cache

import android.os.Environment
import android.util.Log
import java.io.File

class CacheDirectoryProvider {

    companion object {
        private const val TAG = "CacheDirProvider"
    }
    fun getCacheDirectory(tempDirName: String, deleteOnShutdown: Boolean = false): File {
        return getTempDirectory(tempDirName, deleteOnShutdown)
    }

    private fun getTempDirectory(tempDirName: String, deleteOnShutdown: Boolean = false): File {
        val storageDir: File = try {
            Environment.getDataDirectory()
        } catch (e: NoClassDefFoundError) {
            File.createTempFile("unleash_toggles", null)
        } catch (e: RuntimeException) {
            File("unleash_toggles")
        }
        val tempDir = File(storageDir, tempDirName)
        Log.d(TAG, "Using temp storage directory: $tempDirName")
        createDirectoryIfNotExists(tempDir) || throw RuntimeException("Failed to create directory ${tempDir.absolutePath}")
        if (deleteOnShutdown) addShutdownHook(tempDir)
        return tempDir
    }

    private fun createDirectoryIfNotExists(file: File): Boolean {
        return file.exists() || file.mkdirs()
    }

    private fun addShutdownHook(file: File) {
        Runtime.getRuntime().addShutdownHook(DeleteFileShutdownHook(file))
    }

    private class DeleteFileShutdownHook(file: File) : Thread(Runnable {
        file.deleteRecursively()
    })
}