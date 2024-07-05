package io.getunleash.android.data


data class DataStrategy(
    val interval: Long,
    val delay: Long = 0,
    val respectHibernation: Boolean = false,
    val respectBattery: Boolean = false,
    val onlyOnWifi: Boolean = false,
    val enabled: Boolean = true
)

// new DataStrategy().fetchAfter(1).thenPoll(15).respectHibernation().respectBatteryLevel().onlyPollOnWifi();
// new MetricStrategy().sendAfter(1).thenPoll(15).respectHibernation().respectBatteryLevel().onlySendOnWifi();


