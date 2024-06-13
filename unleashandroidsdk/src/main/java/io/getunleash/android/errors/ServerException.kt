package io.getunleash.errors

class ServerException(statusCode: Int) : Exception("Unleash responded with $statusCode") {
}