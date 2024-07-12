package io.getunleash.android.backup

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.android.cache.CacheDirectoryProvider
import io.getunleash.android.data.Parser
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.unleashScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

class LocalBackup(
    private val localDir: File = CacheDirectoryProvider().getCacheDirectory("unleash_backup")
) {
    companion object {
        private const val TAG = "LocalBackup"
        private const val PREFIX = "context_backup_"
    }
    fun subscribeTo(state: Flow<UnleashState>) {
        unleashScope.launch {
            state.distinctUntilChanged { old, new -> old != new }.collect {
                writeToDisc(it)
            }
        }
    }

    private fun writeToDisc(state: UnleashState) {
        try {
            val contextBackup = File(localDir.absolutePath, PREFIX + state.context.hashCode())
            Parser.jackson.writeValueAsBytes(state.toggles)
            contextBackup.writeBytes(state.toString().toByteArray())
        } catch (e: Exception) {
            Log.d(TAG, "Error writing to disc", e)
        }
    }

    fun loadFromDisc(context: UnleashContext): UnleashState? {
        val contextBackup = File(localDir.absolutePath, PREFIX + context.hashCode())
        try {
            if (!contextBackup.exists()) {
                return UnleashState(context, Parser.jackson.readValue(contextBackup.readBytes()))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error loading from disc ${contextBackup.absolutePath}", e)
        }
        return null
    }
}