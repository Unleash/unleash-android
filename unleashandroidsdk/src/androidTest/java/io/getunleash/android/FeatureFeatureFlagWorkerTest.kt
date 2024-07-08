package io.getunleash.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)

class FeatureTogglesFetcherTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before
    fun setup() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        // Initialize WorkManager for instrumentation tests.
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun testFeatureToggleWorker() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            this::class.java.classLoader?.getResource("edgeresponse.json")!!.readText()))
        val input = workDataOf("proxyUrl" to server.url("").toString(), "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<CoroutineWorker>(context)
            .setInputData(input)
            .build()
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(ListenableWorker.Result.success())
        }
    }

    @Test
    fun testFeatureToggleWorker_withInvalidBody_resultsInFailure() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("Invalid json"))
        val input = workDataOf("proxyUrl" to server.url("").toString(), "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<CoroutineWorker>(context)
            .setInputData(input)
            .build()
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(ListenableWorker.Result.failure())
        }
    }

    @Test
    fun testFeatureToggleWorker_notifiesFeatureUpdateListeners() {
        var numberOfToggles = 0

        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            this::class.java.classLoader?.getResource("edgeresponse.json")!!.readText()))
        val input = workDataOf("proxyUrl" to server.url("").
            toString(), "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<CoroutineWorker>(context)
            .setInputData(input)
            .build()
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(ListenableWorker.Result.success())

            // wait for 50ms until numberOfToggles is greater than zero
            Thread.sleep(50)
            assertThat(numberOfToggles).isGreaterThan(0)
        }

    }
}
