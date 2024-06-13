package io.getunleash.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)

class FeatureToggleWorkerTest {
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
    fun testSleepWorker() {
        val input = workDataOf("proxyUrl" to "http://localhost:4242", "clientKey" to "2")
        val worker = TestListenableWorkerBuilder<FeatureToggleWorker>(context)
            .setInputData(input)
            .build()
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success("s"))
        }
    }


    @Test
    @Throws(Exception::class)
    fun testSimpleEchoWorker() {
        // Define input data
        val input = workDataOf("KEY_1" to 1, "KEY_2" to 2)

        // Create request
        val request = OneTimeWorkRequestBuilder<FeatureToggleWorker>()
            .setInputData(input)
            .build()

        val workManager = WorkManager.getInstance(context)
        // Enqueue and wait for result. This also runs the Worker synchronously
        // because we are using a SynchronousExecutor.
        workManager.enqueue(request).result.get()
        // Get WorkInfo and outputData
        val workInfo = workManager.getWorkInfoById(request.id).get()
        val outputData = workInfo.outputData

        // Assert
        //assertThat(workInfo.state, `is`(WorkInfo.State.SUCCEEDED))
        //assertThat(outputData, `is`(input))
        assertThat(workInfo).isNull();
    }
}
