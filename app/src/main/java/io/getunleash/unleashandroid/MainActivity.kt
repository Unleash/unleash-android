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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.getunleash.android.Unleash
import io.getunleash.android.events.UnleashEventListener

class MainActivity : ComponentActivity() {
    private var isEnabledViewModel: IsEnabledViewModel = IsEnabledViewModel(initialFlagValue)
    private var initialized = false
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val unleash = (application as TestApplication).unleash
        Log.i("MAIN","MainActivity.onCreate")
        enableEdgeToEdge()
        Log.i("MAIN","MainActivity.onCreate | lifecycle ${lifecycle.currentState}")


        setContent {
            unleashAndroidTheme {
                var userId by remember { mutableStateOf(initialUserId) }
                val flag by isEnabledViewModel.flagName.observeAsState()
                if (!initialized) {
                    unleash.setContext(unleash.getContext().copy(userId = userId))
                    isEnabledViewModel.initialize(unleash)
                }
                unleash.getContext()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)) {
                        Greeting(
                            name = "Unleash",
                            modifier = Modifier.padding(innerPadding),
                            isEnabledViewModel = isEnabledViewModel
                        )

                        OutlinedTextField(
                            value = userId,
                            onValueChange = { newText ->
                                userId = newText
                                unleash.setContext(unleash.getContext().copy(userId = newText))
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
    }
}



class IsEnabledViewModel(initialFlag: String): ViewModel() {
    private var unleash: Unleash? = null
    private val _flagName = MutableLiveData(initialFlag)
    fun getFlagName():String = _flagName.value ?: ""
    fun setFlagName(newFlagName: String) {
        _flagName.postValue(newFlagName)
        _isEnabled.postValue(unleash?.isEnabled(newFlagName) ?: false)
    }

    val flagName: LiveData<String>
        get() = _flagName

    private val _isEnabled = MutableLiveData(unleash?.isEnabled(initialFlag) ?: false)

    val isEnabled: LiveData<Boolean>
        get() = _isEnabled

    fun initialize(unleash: Unleash) {
        val listener: UnleashEventListener = object: UnleashEventListener {
            override fun onRefresh() {
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
            }
        }
        unleash.addUnleashEventListener(listener)
        this.unleash = unleash
    }
}


@Composable
fun Greeting(name: String, isEnabledViewModel: IsEnabledViewModel, modifier: Modifier = Modifier) {
    val isEnabled by isEnabledViewModel.isEnabled.observeAsState(false)
    val text = "Hello $name"

    Text(
        text = "$text, toggle ${isEnabledViewModel.getFlagName()} is ${if (isEnabled) "enabled" else "disabled"}",
        fontSize = 44.sp,
        lineHeight = 44.sp,
        color = if (isEnabled) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
        modifier = modifier
    )
}
