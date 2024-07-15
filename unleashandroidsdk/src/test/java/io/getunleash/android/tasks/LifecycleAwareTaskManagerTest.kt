package io.getunleash.android.tasks

import android.net.Network
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.getunleash.android.BaseTest
import io.getunleash.android.data.DataStrategy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class LifecycleAwareTaskManagerTest: BaseTest() {
    private val testDispatcher = StandardTestDispatcher()
    private val alwaysRunStrategy = DataStrategy(pauseOnBackground = false)

    private var foregroundCount = 0
    private var alwaysCount = 0
    private val foregroundJob = DataJob(
        id = "foregroundJob",
        strategy = DataStrategy(),
        action = suspend {
            if (foregroundCount > 10) throw Exception("Test exception")
            println("foreground count is now: ${++ foregroundCount} ${Thread.currentThread().name}")
        }
    )
    private val alwaysJob = DataJob(
        id = "alwaysJob",
        strategy = alwaysRunStrategy,
        action = suspend {
            if (alwaysCount > 10) throw Exception("Test exception")
            println("always count is now: ${++ alwaysCount} ${Thread.currentThread().name}")
        }
    )

    @Before
    fun init() {
        foregroundCount = 0
        alwaysCount = 0
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testLifecycleAwareTaskManager() = runTest(testDispatcher) {
        val manager = LifecycleAwareTaskManager(
            dataJobs = listOf(foregroundJob, alwaysJob),
            scope = this,
            ioContext = this.coroutineContext
        )

        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(0 , 0))

        // when it starts two workers are active
        manager.startForegroundJobs()
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(2)
        runCurrent()
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(1 , 1))

        // on pause only one worker remains active
        manager.onStateChanged(mock(LifecycleOwner::class.java), Lifecycle.Event.ON_PAUSE)
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(1)
        advanceTimeBy(alwaysRunStrategy.interval + 1)
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(1 , 2))

        // on resume the foreground worker kicks in immediately
        manager.onStateChanged(mock(LifecycleOwner::class.java), Lifecycle.Event.ON_RESUME)
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(2)
        runCurrent()
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(2 , 2))
        manager.stop()
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)
    }

    @Test
    fun `when network is unavailable it won't start workers`() = runTest(testDispatcher) {
        val manager = LifecycleAwareTaskManager(
            dataJobs = listOf(foregroundJob, alwaysJob),
            networkAvailable = false,
            scope = this,
            ioContext = this.coroutineContext // override Dispatchers.IO
        )

        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(0 , 0))

        // when it starts two workers are active
        manager.startForegroundJobs()
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when network becomes available it will start workers`() = runTest(testDispatcher) {
        val manager = LifecycleAwareTaskManager(
            dataJobs = listOf(foregroundJob, alwaysJob),
            networkAvailable = false,
            scope = this,
            ioContext = this.coroutineContext
        )

        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(0 , 0))

        // when it starts without network workers are not active
        manager.startForegroundJobs()
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)

        // when network becomes available workers become active
        manager.onAvailable()
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(2)

        runCurrent()
        assertThat(Pair(foregroundCount, alwaysCount)).isEqualTo(Pair(1 , 1))
        manager.stop()
        assertThat(manager.foregroundWorkers.filter { it.value.isActive }.size).isEqualTo(0)
    }
}