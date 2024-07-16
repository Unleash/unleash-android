package io.getunleash.android

import java.util.Date

object UnleashStats {
    var readySince: Date? = null
    var lastSuccessfulFetch: Date? = null
    var lastSuccessfulSentMetrics: Date? = null
}