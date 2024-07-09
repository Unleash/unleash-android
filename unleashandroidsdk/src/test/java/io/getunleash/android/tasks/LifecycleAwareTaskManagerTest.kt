package io.getunleash.android.tasks

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.getunleash.android.ShadowLog
import io.getunleash.android.data.DataStrategy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [21], shadows = [ShadowLog::class])
class LifecycleAwareTaskManagerTest {
    private val testDispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testLifecycleAwareTaskManager() = runTest(testDispatcher) {
        var foregroundCount = 0
        var alwaysCount = 0
        val foregroundOnlyStrategy = DataStrategy()
        val alwaysRunStrategy = DataStrategy(pauseOnBackground=false)


        val manager = LifecycleAwareTaskManager(
            dataJobs = listOf(
                DataJob(
                    id = "foregroundOnlyJob",
                    strategy = foregroundOnlyStrategy,
                    action = suspend {
                        println("foreground count is now: ${++ foregroundCount}")
                    }
                ),
                DataJob(
                    id = "alwaysJob",
                    strategy = alwaysRunStrategy,
                    action = suspend {
                        println("always count is now: ${++ alwaysCount}")
                    }
                )
            ),
            this,
            this.backgroundScope.coroutineContext // override Dispatchers.IO
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
}