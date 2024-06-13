package io.getunleash.android

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.polling.UnleashFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.math.BigInteger
import java.security.MessageDigest

class FeatureToggleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    val TAG = "FeatureToggleWorker"
    val unleashFetcher = UnleashFetcher(
        proxyUrl = params.inputData.getString("proxyUrl")?.toHttpUrl()!!,
        clientKey = params.inputData.getString("clientKey")!!
    )
    val unleashContext = UnleashContext()
    /*var unleashFetcher: UnleashFetcher
    var unleashContext: UnleashContext
    val cache: ToggleCache
    val config: UnleashConfig
    private val cacheKey: String by lazy { sha256(CACHE_BASE.format(clientKey)) }
    private var inMemoryConfig: Map<String, Toggle> = emptyMap()

    companion object {
        const val CACHE_BASE = "android_${UnleashFetcher.TOGGLE_BACKUP_NAME}_%s"

        fun sha256(s: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(s.toByteArray(Charsets.UTF_8))
            val number = BigInteger(1, digest)
            return number.toString(16).padStart(32, '0')
        }
    }*/

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val response = unleashFetcher.getToggles(unleashContext)
                val cached = readToggleCache()
                if (response.isFetched() && cached != response.toggles) {
                    writeToggleCache(response.toggles)
                    // FIXME broadcastTogglesUpdated()
                } else if (response.isFailed()) {
                    // FIXME response?.error?.let(::broadcastTogglesErrored)
                    Result.failure()
                }

                Result.success()
            } catch (e: Exception) {
                // FIXME broadcastTogglesUpdated()
                Log.w(TAG, "Exception in AutoPollingCachePolicy", e)
                Result.failure()
            }
        }
    }

    fun readToggleCache(): Map<String, Toggle> {
        /*return try {
            this.cache.read(cacheKey)
        } catch (e: Exception) {
            inMemoryConfig
        }*/
        return emptyMap()
    }

    fun writeToggleCache(value: Map<String, Toggle>) {
/*        try {
            this.inMemoryConfig = value
            this.cache.write(cacheKey, value)
            // FIXME broadcastTogglesUpdated()
        } catch (e: Exception) {
        }*/
    }
}






