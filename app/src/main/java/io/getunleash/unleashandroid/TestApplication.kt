package io.getunleash.unleashandroid

import android.app.Application
import io.getunleash.android.DefaultUnleash
import io.getunleash.android.Unleash
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.events.UnleashEventListener

const val initialFlagValue = "flag-1"
const val initialUserId = "123"

class TestApplication: Application() {
    val unleash: Unleash by lazy {
        DefaultUnleash(
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
            unleashContext = UnleashContext(userId = initialUserId),
            eventListener = object : UnleashEventListener {
                override fun onReady() {
                    println("Unleash is ready")
                }
            }
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        unleash.close()
    }
}

