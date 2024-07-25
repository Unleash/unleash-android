package io.getunleash.android.cache

import android.content.Context
import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import io.getunleash.android.backup.LocalStorageConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

class CacheDirectoryProviderTest : BaseTest() {

    @Test
    fun `should return the correct cache directory`() {
        val storageConfig = getStorageConfig(createTempDirectory("test1").pathString)
        val cacheDirectoryProvider = CacheDirectoryProvider(storageConfig, mock(Context::class.java))
        val cacheDirectory = cacheDirectoryProvider.getCacheDirectory("backup-dir")
        assertThat(cacheDirectory.isDirectory).isTrue()
        assertThat(cacheDirectory.exists()).isTrue()
    }
    @Test
    fun `should return the same cache directory if it already exists`() {
        val storageConfig = getStorageConfig(createTempDirectory("test2").pathString)
        val cacheDirectoryProvider = CacheDirectoryProvider(storageConfig, mock(Context::class.java))
        val cacheDirectory1 = cacheDirectoryProvider.getCacheDirectory("backup-dir")
        val cacheDirectory2 = cacheDirectoryProvider.getCacheDirectory("backup-dir")
        assertThat(cacheDirectory1.isDirectory).isTrue()
        assertThat(cacheDirectory1.exists()).isTrue()
        assertThat(cacheDirectory1).isEqualTo(cacheDirectory2)
    }

    @Test
    fun `should add a shutdown hook`() {
        val shutdownHookCaptor = ArgumentCaptor.captor<Thread>()
        val mockRuntime = mock(Runtime::class.java)
        val storageConfig = getStorageConfig(createTempDirectory("test3").pathString)
        val cacheDirectoryProvider = CacheDirectoryProvider(storageConfig, mock(Context::class.java), mockRuntime)
        val cacheDirectory = cacheDirectoryProvider.getCacheDirectory("backup-dir", true)
        assertThat(cacheDirectory.isDirectory).isTrue()
        assertThat(cacheDirectory.exists()).isTrue()
        verify(mockRuntime).addShutdownHook(shutdownHookCaptor.capture())

        shutdownHookCaptor.value.run()
        assertThat(cacheDirectory.exists()).isFalse()
    }

    @Test
    fun `if no dir in config uses cacheDir from context`() {
        val storageConfigWithoutDir = getStorageConfig(null)
        val context = mock(Context::class.java)
        val tempDirectory = createTempDirectory("test4").toFile()
        `when`(context.cacheDir).thenReturn(tempDirectory)
        val cacheDirectoryProvider = CacheDirectoryProvider(storageConfigWithoutDir, context)
        val cacheDirectory = cacheDirectoryProvider.getCacheDirectory("backup-dir")
        assertThat(cacheDirectory.isDirectory).isTrue()
        assertThat(cacheDirectory.exists()).isTrue()
        // check that the cache directory is a child of the context cache directory
        assertThat(cacheDirectory.parent).isEqualTo(tempDirectory.path)
    }

    private fun getStorageConfig(directory: String?): LocalStorageConfig {
        val builder = UnleashConfig.newBuilder("test")
            .pollingStrategy.enabled(false)
            .metricsStrategy.enabled(false)
        if (directory != null) {
            builder.localStorageConfig.dir(directory)
        }
        return builder.build().localStorageConfig
    }
}