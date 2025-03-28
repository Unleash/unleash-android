package io.getunleash.unleashandroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.getunleash.android.Unleash
import io.getunleash.android.events.UnleashStateListener
import java.util.Date
import java.util.Timer
import kotlin.concurrent.timerTask

val unleashStatsObserver = UnleashStatsObserver()

class MainActivity : ComponentActivity() {
    private var isEnabledViewModel: IsEnabledViewModel = IsEnabledViewModel(initialFlagValue)
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val unleash = (application as TestApplication).unleash
        var context = (application as TestApplication).unleashContext
        enableEdgeToEdge()
        Log.i("MAIN","MainActivity.onCreate | lifecycle ${lifecycle.currentState}")

        setContent {
            UnleashAndroidTheme {
                var userId by remember { mutableStateOf(initialUserId) }
                val flag by isEnabledViewModel.flagName.observeAsState()
                if (!initialized) {
                    isEnabledViewModel.initialize(unleash)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)) {
                        Greeting(
                            name = "Unleash",
                            modifier = Modifier.padding(innerPadding),
                            isEnabledViewModel = isEnabledViewModel,
                        )

                        OutlinedTextField(
                            value = userId,
                            onValueChange = { newText ->
                                userId = newText
                                context = context.copy(userId = newText)
                                unleash.setContext(context)
                            },
                            label = { Text("Enter the userId for the context") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 24.sp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = flag ?: "",
                            onValueChange = { newText ->
                                isEnabledViewModel.setFlagName(newText)
                            },
                            label = { Text("Change the feature flag") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 24.sp)
                        )

                    }
                }
            }
        }
        Log.i("MAIN","MainActivity.onCreate FINISH")
    }

    override fun onStart() {
        super.onStart()
        Log.i("MAIN","MainActivity.onStart")
        Timer().schedule(
            timerTask {
            unleashStatsObserver.checkForUpdates()
        }, 100L, 1000L)
    }
}

class IsEnabledViewModel(initialFlag: String): ViewModel() {
    private var unleash: Unleash? = null
    private val _flagName = MutableLiveData(initialFlag)
    fun getFlagName():String = _flagName.value ?: ""
    fun setFlagName(newFlagName: String) {
        _flagName.postValue(newFlagName)
        _isEnabled.postValue(unleash?.isEnabled(newFlagName) ?: false)
        _variant.postValue(unleash?.getVariant(newFlagName)?.name ?: "")
    }

    val flagName: LiveData<String>
        get() = _flagName

    private val _isEnabled = MutableLiveData(unleash?.isEnabled(initialFlag) ?: false)
    private val _variant = MutableLiveData(unleash?.getVariant(initialFlag)?.name ?: "")

    val variant: LiveData<String>
        get() = _variant

    val isEnabled: LiveData<Boolean>
        get() = _isEnabled

    fun initialize(unleash: Unleash) {
        val listener: UnleashStateListener = object: UnleashStateListener {
            override fun onStateChanged() {
                Log.i("MAIN", "Detected refresh event")
                val flag = _flagName.value
                if (flag == null) {
                    Log.i("MAIN", "Flag name is null")
                    return
                }
                val newValue = unleash.isEnabled(flag)
                if (newValue != _isEnabled.value) {
                    Log.i("MAIN", "Pushing value changed")
                    _isEnabled.postValue(newValue)
                }
                val newVariant = unleash.getVariant(flag)
                if (newVariant.name != _variant.value) {
                    Log.i("MAIN", "Pushing variant changed")
                    _variant.postValue(newVariant.name)
                }
            }
        }
        unleash.addUnleashEventListener(listener)
        this.unleash = unleash
    }
}

@Composable
fun Greeting(
    name: String,
    isEnabledViewModel: IsEnabledViewModel,
    modifier: Modifier = Modifier,
) {
    val isEnabled by isEnabledViewModel.isEnabled.observeAsState(false)
    val variant by isEnabledViewModel.variant.observeAsState("")
    Text(
        text = "Hello $name",
        fontSize = 44.sp,
        modifier = modifier,
        color = colorResource(id = R.color.purple_500)
    )
    Text(
        text = "${isEnabledViewModel.getFlagName()} is ${if (isEnabled) "enabled with variant $variant" else "disabled"}",
        fontSize = 38.sp,
        lineHeight = 38.sp,
        color = if (isEnabled) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
        modifier = modifier
    )

    val stats = unleashStatsObserver.latestStats.observeAsState()
    ElapsedTime(
        prefix = "Unleash ready since",
        diff = diff(stats.value?.stats?.readySince)
    )
    ElapsedTime(
        prefix = "Last state update",
        diff = diff(stats.value?.stats?.lastStateUpdate)
    )
}

@Composable
private fun diff(date: Date?): Long? {
    return date?.let {
        Date().time - it.time
    }
}

@Composable
fun ElapsedTime(prefix: String, diff: Long?) {
    val text = remember(diff) {
        if (diff == null) {
            "never"
        } else {
            if (diff < 60000) {
                val seconds = diff / 1000
                "$seconds seconds ago"
            } else {
                val minutes = diff / 60000
                "$minutes minutes ago"
            }
        }
    }
    Text(
        text = "$prefix $text",
        fontSize = 24.sp,
        lineHeight = 24.sp,
    )
}


data class StatsGen(val stats: UnleashStats, val time: Date = Date())
class UnleashStatsObserver {
    private val _latestStats = MutableLiveData<StatsGen>()
    val latestStats: LiveData<StatsGen> = _latestStats

    fun checkForUpdates() {
        // This is a dummy implementation to simulate the stats changing
        val newStats = StatsGen(UnleashStats)
        if (newStats != _latestStats.value) {
            _latestStats.postValue(newStats)
        }
    }
}