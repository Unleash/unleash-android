package io.getunleash.android.data

import java.lang.Exception

/**
 * Modelling the fetch action
 * @param status Status of the request
 * @param config The response from the proxy parsed to a data class
 */
data class FetchResponse(
    val status: Status,
    val config: ProxyResponse? = null,
    val error: Exception? = null) {
    fun isFetched() = status == Status.FETCHED
    fun isNotModified() = status == Status.NOTMODIFIED
    fun isFailed() = status == Status.FAILED
}

/**
 * We use this out from the fetcher to not have to do convert the proxyresponse to a map we can work on everytime we want answer user calls
 * @param status Status of the request
 * @param toggles The parsed feature toggles map from the unleash proxy
 */
data class ToggleResponse(
    val status: Status,
    val toggles: Map<String, Toggle> = emptyMap(),
    val error: Exception? = null) {
    fun isFetched() = status == Status.FETCHED
    fun isNotModified() = status == Status.NOTMODIFIED
    fun isFailed() = status == Status.FAILED
}
enum class Status {
    FETCHED,
    NOTMODIFIED,
    FAILED
}
