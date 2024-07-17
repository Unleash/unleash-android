package io.getunleash.unleashandroid

import android.app.Application
import io.getunleash.android.DefaultUnleash
import io.getunleash.android.Unleash
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.events.UnleashAllEventsListener
import io.getunleash.android.events.UnleashReadyListener
import io.getunleash.android.events.UnleashStateListener
import java.util.Date

const val initialFlagValue = "flag-1"
const val initialUserId = "123"
object UnleashStats {
    var readySince: Date? = null
    var lastStateUpdate: Date? = null
}
class TestApplication: Application() {
    val unleashContext: UnleashContext = UnleashContext(userId = initialUserId)
    val unleash: Unleash by lazy {
        val instance = DefaultUnleash(
            androidContext = this,
            unleashConfig = UnleashConfig(
                appName = "test-android-app",
                proxyUrl = "https://sandbox.getunleash.io/enterprise/api/frontend",
                clientKey = "gaston:development.8c5d8ce0fd7233c268b74da276eb3c110caf8d2c67eb8dc5b29b4644",
                pollingStrategy = DataStrategy(
                    interval = 3000, // this is just for testing purposes
                    pauseOnBackground = true
                ),
                metricsStrategy = DataStrategy(
                    interval = 5000, // this is just for testing purposes
                    pauseOnBackground = true
                )
            ),
            unleashContext = unleashContext
        )
        instance.start(listOf(object: UnleashReadyListener, UnleashStateListener {
            override fun onReady() {
                println("Unleash is ready")
                UnleashStats.readySince = Date()
            }

            override fun onStateChanged() {
                UnleashStats.lastStateUpdate = Date()
            }
        }))
        instance
    }

    override fun onTerminate() {
        super.onTerminate()
        unleash.close()
    }
}

