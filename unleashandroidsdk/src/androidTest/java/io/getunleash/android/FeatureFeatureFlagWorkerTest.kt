package io.getunleash.android

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.getunleash.android.polling.TogglesReceivedListener
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)

class FeatureToggleWorkerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Events.clear()
    }

    private fun load(resource: String): String {
        return this::class.java.classLoader?.getResource(resource)!!.readText()
    }

    @Test
    fun testFeatureToggleWorker() = testScope.runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(load("edgeresponse.json")))
        val input = workDataOf("proxyUrl" to server.url("").toString(), "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<FeatureToggleWorker>(getApplicationContext())
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun testFeatureToggleWorker_withInvalidBody_resultsInFailure() = testScope.runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("Invalid json"))
        val input = workDataOf("proxyUrl" to server.url("").toString(), "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<FeatureToggleWorker>(getApplicationContext())
            .setInputData(input)
            .build()
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(ListenableWorker.Result.failure())
        }
    }

    @Test
    fun testFeatureToggleWorker_notifiesFeatureUpdateListeners() = testScope.runTest {
        var numberOfToggles = 0
        val listener = TogglesReceivedListener { toggles ->
            // Implementation here
            println("Toggles have been updated (${toggles.size}): $toggles")
            numberOfToggles = toggles.size
        }
        Events.addTogglesReceivedListener(listener)

        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(load("edgeresponse.json")))
        val input = workDataOf("proxyUrl" to server.url("").
            toString(), "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<FeatureToggleWorker>(getApplicationContext())
            .setInputData(input)
            .build()

        val result = worker.doWork()

        // Check the result
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(numberOfToggles).isGreaterThan(0)
    }
}
