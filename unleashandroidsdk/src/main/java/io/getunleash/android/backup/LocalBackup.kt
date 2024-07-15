package io.getunleash.android.backup

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.android.cache.CacheDirectoryProvider
import io.getunleash.android.data.Parser
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.unleashScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupState(val contextId: String, val toggles: Map<String, Toggle>)

/**
 * Local backup of the last state of the Unleash SDK.
 *
 * Because it only keeps the last state, it also saves the context id, and uses it to verify the context
 * is the same when loading the state from disc.
 */
class LocalBackup(
    androidContext: Context,
    private val localDir: File = CacheDirectoryProvider(androidContext).getCacheDirectory("unleash_backup")
) {
    companion object {
        private const val TAG = "LocalBackup"
        private const val STATE_BACKUP_FILE = "unleash_state.json"
    }

    private var lastContext: UnleashContext? = null

    fun subscribeTo(state: Flow<UnleashState>) {
        unleashScope.launch {
            withContext(Dispatchers.IO) {
                state.collect {
                    if (it.context != lastContext) {
                        lastContext = it.context
                        writeToDisc(it)
                    } else {
                        Log.d(TAG, "Context unchanged, not writing to disc")
                    }
                }
            }
        }
    }

    private fun writeToDisc(state: UnleashState) {
        try {
            // write only the last state
            val contextBackup = File(localDir.absolutePath, STATE_BACKUP_FILE)
            contextBackup.writeBytes(Parser.jackson.writeValueAsBytes(BackupState(id(state.context), state.toggles)))
            Log.d(TAG, "Written state to ${contextBackup.absolutePath}")
        } catch (e: Exception) {
            Log.i(TAG, "Error writing to disc", e)
        }
    }

    fun loadFromDisc(context: UnleashContext): UnleashState? {
        val stateBackup = File(localDir.absolutePath, STATE_BACKUP_FILE)
        try {
            if (stateBackup.exists()) {
                val backupState = Parser.jackson.readValue<BackupState>(stateBackup.readBytes())
                if (backupState.contextId != id(context)) {
                    Log.i(TAG, "Context id mismatch, ignoring backup")
                    return null
                }
                return UnleashState(context, backupState.toggles)
            } else {
                Log.d(TAG, "No backup found at ${stateBackup.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading from disc ${stateBackup.absolutePath}", e)
        }
        return null
    }

    private fun id(context: UnleashContext): String {
        return context.hashCode().toString()
    }
}