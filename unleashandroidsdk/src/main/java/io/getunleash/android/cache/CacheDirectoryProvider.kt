package io.getunleash.android.cache

import java.io.File
import java.nio.file.Files

class CacheDirectoryProvider {

    @Suppress("NewApi")
    fun getCacheDirectory(): File = try {
        Files.createTempDirectory("unleash_toggles").toFile()
    } catch (e: NoClassDefFoundError) {
        val file = getCacheDirectoryFile()
        createDirectoryIfNotExists(file)
        file
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