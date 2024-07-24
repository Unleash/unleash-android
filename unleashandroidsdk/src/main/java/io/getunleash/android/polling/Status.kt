package io.getunleash.android.polling

enum class Status {
    SUCCESS,
    NOT_MODIFIED,
    FAILED,
    THROTTLED;

    fun isSuccess() = this == SUCCESS

    fun isNotModified() = this == NOT_MODIFIED
    fun isFailed() = this == FAILED || this == THROTTLED
}