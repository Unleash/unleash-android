package io.getunleash.android.polling

fun interface TogglesErroredListener {
    fun onError(e: Exception)
}