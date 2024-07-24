package io.getunleash.android.polling

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

