package io.getunleash.android.polling

import io.getunleash.android.data.Toggle

/**
 * Holder for parsing response from proxy
 */
data class ProxyResponse(val toggles: List<Toggle>)
