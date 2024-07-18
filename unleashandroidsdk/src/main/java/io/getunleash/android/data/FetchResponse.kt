package io.getunleash.android.data

/**
 * Modelling the fetch action
 * @param status Status of the request
 * @param config The response from the proxy parsed to a data class
 */
data class FetchResponse(
    val status: Status,
    val config: ProxyResponse? = null,
    val error: Exception? = null) {
    fun isSuccess() = status.isSuccess()
    fun isNotModified() = status.isNotModified()
    fun isFailed() = status.isFailed()
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
    fun isFetched() = status.isSuccess()
    fun isNotModified() = status.isNotModified()
    fun isFailed() = status.isFailed()
}
enum class Status {
    SUCCESS,
    NOT_MODIFIED,
    FAILED,
    THROTTLED;

    fun isSuccess() = this == SUCCESS

    fun isNotModified() = this == NOT_MODIFIED
    fun isFailed() = this == FAILED || this == THROTTLED
}
