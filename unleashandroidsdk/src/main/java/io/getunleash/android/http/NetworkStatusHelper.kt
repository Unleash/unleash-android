package io.getunleash.android.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkStatusHelper(val context: Context) {
    companion object {
        private const val TAG = "NetworkState"
    }

    private val networkCallbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    fun registerNetworkListener(networkCallback: ConnectivityManager.NetworkCallback) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        if (connectivityManager !is ConnectivityManager) {
            Log.i(TAG, "Failed to get ConnectivityManager")
            return
        }
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        networkCallbacks += networkCallback
    }

    fun close () {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        if (connectivityManager !is ConnectivityManager) {
            Log.i(TAG, "Failed to get ConnectivityManager")
            return
        }
        networkCallbacks.forEach {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            return networkInfo.isConnected
        }
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    fun isAvailable(): Boolean {
        return !isAirplaneModeOn(context) && isNetworkAvailable(context)
    }
}
