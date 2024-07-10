package io.getunleash.android.data

/**
 *
 * Allows the consumer to set the properties that will determine whether a flag is on or off for complex strategies and constraints
 * Note that this SDK does not support the current time since this will not work correctly with a proxy SDK. The environment property is no
 * longer exposed, as of v1.0 of this SDK, it's not calculated through the API key.
 * [See the documentation](https://docs.getunleash.io/docs/user_guide/unleash_context)
 * @property userId Whatever implementation of userId you use, for stickiness and/or gradual rollouts to work correctly you'll need to set either this or sessionId (can be both)
 * @property sessionId Whatever implementation of sessionId you use, for stickiness and/or gradual rollouts to work correctly you'll need to set either this or userId  (can be both)
 * @property remoteAddress the Ip address of the client. If your feature uses the remoteAddress strategy
 * you'll need to set this
 * @property properties - Other properties for constraints.
 */
data class UnleashContext(
    val userId: String? = null,
    val sessionId: String? = null,
    val remoteAddress: String? = null,
    val properties: Map<String, String> = emptyMap(),
) {
    /**
     * Used to get a new builder with current state
     */
    fun newBuilder(): Builder = Builder(
        userId = userId,
        sessionId = sessionId,
        remoteAddress = remoteAddress,
        properties = properties.toMutableMap(),
    )

    companion object {
        /**
         * Used to get a Builder with no fields set, hopefully simplifying constructing contexts
         */
        fun newBuilder(): Builder = Builder()
    }

    data class Builder(
        var userId: String? = null,
        var sessionId: String? = null,
        var remoteAddress: String? = null,
        var properties: MutableMap<String, String> = mutableMapOf(),
    ) {

        fun userId(userId: String) = apply { this.userId = userId }

        fun sessionId(sessionId: String) = apply { this.sessionId = sessionId }

        fun remoteAddress(address: String) = apply { this.remoteAddress = address }

        fun addProperty(key: String, value: String) = apply { this.properties[key] = value }

        fun properties(map: MutableMap<String, String>) = apply { this.properties = map }

        fun build(): UnleashContext {
            return UnleashContext(
                userId = userId,
                sessionId = sessionId,
                remoteAddress = remoteAddress,
                properties = properties.toMap(),
            )
        }
    }
}