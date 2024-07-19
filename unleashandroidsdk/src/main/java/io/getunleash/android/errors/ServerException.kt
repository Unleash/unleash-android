package io.getunleash.android.errors

class ServerException(statusCode: Int) : Exception("Unleash responded with $statusCode") {
}
