package io.getunleash.android.polling

import io.getunleash.android.data.Toggle

/**
 * We use this out from the fetcher to not have to do convert the proxyresponse to a map we can work on everytime we want answer user calls
 * @param status Status of the request
 * @param toggles The parsed feature toggles map from the unleash proxy
 */
data class ToggleResponse(
    val status: Status,
    val toggles: Map<String, Toggle> = emptyMap(),
    val error: Exception? = null) {
}