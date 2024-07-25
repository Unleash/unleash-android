package io.getunleash.android.http

import android.util.Log
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min


class Throttler(
    private val intervalLengthInSeconds: Long,
    longestAcceptableIntervalSeconds: Long,
    private val target: String
) {
    companion object {
        private const val TAG = "Throttler"
    }
    private val maxSkips = max(
        longestAcceptableIntervalSeconds / max(
            intervalLengthInSeconds, 1
        ), 1
    )

    private val skips = AtomicLong(0)
    private val failures = AtomicLong(0)

    /**
     * We've had one successful call, so if we had 10 failures in a row, this will reduce the skips
     * down to 9, so that we gradually start polling more often, instead of doing max load
     * immediately after a sequence of errors.
     */
    internal fun decrementFailureCountAndResetSkips() {
        if (failures.get() > 0) {
            skips.set(max(failures.decrementAndGet(), 0L))
        }
    }

    /**
     * We've gotten the message to back off (usually a 429 or a 50x). If we have successive
     * failures, failure count here will be incremented higher and higher which will handle
     * increasing our backoff, since we set the skip count to the failure count after every reset
     */
    private fun increaseSkipCount() {
        skips.set(min(failures.incrementAndGet(), maxSkips))
    }

    /**
     * We've received an error code that we don't expect to change, which means we've already logged
     * an ERROR. To avoid hammering the server that just told us we did something wrong and to avoid
     * flooding the logs, we'll increase our skip count to maximum
     */
    private fun maximizeSkips() {
        skips.set(maxSkips)
        failures.incrementAndGet()
    }

    fun performAction(): Boolean {
        return skips.get() <= 0
    }

    fun skipped() {
        skips.decrementAndGet()
    }

    internal fun handleHttpErrorCodes(responseCode: Int) {
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
            || responseCode == HttpURLConnection.HTTP_FORBIDDEN
        ) {
            maximizeSkips()
            Log.e(TAG,
                "Client was not authorized to talk to the Unleash API at $target. Backing off to $maxSkips times our poll interval (of $intervalLengthInSeconds seconds) to avoid overloading server",
            )
        }
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            maximizeSkips()
            Log.e(TAG,
                "Server said that the endpoint at $target does not exist. Backing off to $maxSkips times our poll interval (of $intervalLengthInSeconds seconds) to avoid overloading server",
            )
        } else if (responseCode == 429) {
            increaseSkipCount()
            Log.i(TAG,
                "RATE LIMITED for the ${failures.get()}. time. Further backing off. Current backoff at ${skips.get()} times our interval (of $intervalLengthInSeconds seconds)",
            )
        } else if (responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            increaseSkipCount()
            Log.i(TAG,
                "Server failed with a $responseCode status code. Backing off. Current backoff at ${skips.get()} times our poll interval (of $intervalLengthInSeconds seconds)",
            )
        }
    }

    fun getSkips(): Long {
        return skips.get()
    }

    fun getFailures(): Long {
        return failures.get()
    }

    fun handle(statusCode: Int) {
        if (statusCode in 200..399) {
            decrementFailureCountAndResetSkips()
        }
        if (statusCode >= 400) {
            handleHttpErrorCodes(statusCode)
        }
    }

}
