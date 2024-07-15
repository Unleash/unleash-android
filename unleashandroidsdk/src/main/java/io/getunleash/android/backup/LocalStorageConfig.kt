package io.getunleash.android.backup

import io.getunleash.android.UnleashConfig

data class LocalStorageConfig(
    val enabled: Boolean = true,
    val dir: String? = null
) {
    fun newBuilder(parent: UnleashConfig.Builder): Builder = Builder(
        parent = parent,
        enabled = enabled,
        dir = dir
    )

    class Builder(
        val parent: UnleashConfig.Builder,
        var enabled: Boolean,
        var dir: String?
    ) {
        fun build(): LocalStorageConfig {
            return LocalStorageConfig(
                enabled = enabled,
                dir = dir
            )
        }

        fun enabled(enabled: Boolean): UnleashConfig.Builder {
            this.enabled = enabled
            return parent
        }

        fun dir(dir: String): UnleashConfig.Builder {
            this.dir = dir
            return parent
        }
    }
}