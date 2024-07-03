package io.getunleash.unleashandroid

import android.app.Application
import io.getunleash.android.DefaultUnleash
import io.getunleash.android.Unleash
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.UnleashContext

const val initialFlagValue = "flag-1"
const val initialUserId = "123"

class TestApplication: Application() {
    val unleash: Unleash by lazy {
        DefaultUnleash(
            UnleashConfig(
                proxyUrl = "https://sandbox.getunleash.io/enterprise/api/frontend",
                clientKey = "gaston:development.8c5d8ce0fd7233c268b74da276eb3c110caf8d2c67eb8dc5b29b4644",
                pollingIntervalInMs = 3000 // this is just for testing purposes
            ),
            UnleashContext(userId = initialUserId, appName = "test-android-app")
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        unleash.close()
    }
}

