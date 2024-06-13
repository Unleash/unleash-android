package io.getunleash.android.data


data class DataStrategy(
    val interval: Int,
    val delay: Int,
    val respectHibernation: Boolean,
    val respectBattery: Boolean,
    val onlyOnWifi: Boolean
);

// new DataStrategy().fetchAfter(1).thenPoll(15).respectHibernation().respectBatteryLevel().onlyPollOnWifi();
// new MetricStrategy().sendAfter(1).thenPoll(15).respectHibernation().respectBatteryLevel().onlySendOnWifi();


