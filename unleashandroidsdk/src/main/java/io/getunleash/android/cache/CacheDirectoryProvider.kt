package io.getunleash.android.cache

import android.content.Context
import android.util.Log
import java.io.File

class CacheDirectoryProvider(private val context: Context) {

    companion object {
        private const val TAG = "CacheDirProvider"
    }
    fun getCacheDirectory(tempDirName: String, deleteOnShutdown: Boolean = false): File {
        return getTempDirectory(tempDirName, deleteOnShutdown)
    }

    private fun getTempDirectory(tempDirName: String, deleteOnShutdown: Boolean = false): File {
        val tempStorageDir: File = try {
                context.cacheDir
        } catch (e: NoClassDefFoundError) {
            File.createTempFile("unleash_toggles", null)
        } catch (e: RuntimeException) {
            File("unleash_toggles")
        }
        val tempDir = File(tempStorageDir, tempDirName)
        if (!createDirectoryIfNotExists(tempDir)) {
            Log.w(TAG, "Failed to create directory ${tempDir.absolutePath}")
        } else {
            if (deleteOnShutdown) addShutdownHook(tempDir)
        }
        return tempDir
    }

    private fun createDirectoryIfNotExists(file: File): Boolean {
        if (file.exists()) {
            Log.d(TAG, "Directory ${file.absolutePath} already exists")
            return true
        }
        if (file.mkdirs()) {
            Log.d(TAG, "Created directory ${file.absolutePath}")
            return true
        }
        Log.w(TAG, "Failed to create directory ${file.absolutePath}")
        return false
    }

    private fun addShutdownHook(file: File) {
        Runtime.getRuntime().addShutdownHook(DeleteFileShutdownHook(file))
    }

    private class DeleteFileShutdownHook(file: File) : Thread(Runnable {
        file.deleteRecursively()
    })
}