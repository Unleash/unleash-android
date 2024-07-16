package io.getunleash.android.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo

import io.getunleash.android.BaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
class NetworkStatusHelperTest : BaseTest() {

    @Test
    fun `when connectivity service is not available assumes network is available`() {
        val networkStatusHelper = NetworkStatusHelper(mock(Context::class.java))
        assertThat(networkStatusHelper.isAvailable()).isTrue()
    }

    @Test
    fun `when api version is 21 check active network info`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        val activeNetwork = mock(NetworkInfo::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        `when`(connectivityManager.activeNetworkInfo).thenReturn(activeNetwork)
        `when`(activeNetwork.isConnected).thenReturn(true)
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isTrue()
        verify(activeNetwork).isConnected
    }

    @Test
    @Config(sdk = [23])
    fun `when api version is 23 check active network info`() {
        val context = mock(Context::class.java)
        val connectivityManager = mock(ConnectivityManager::class.java)
        val activeNetwork = mock(Network::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        `when`(connectivityManager.activeNetwork).thenReturn(activeNetwork)
        val networkCapabilities = mock(NetworkCapabilities::class.java)
        `when`(connectivityManager.getNetworkCapabilities(activeNetwork)).thenReturn(networkCapabilities)
        `when`(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        val networkStatusHelper = NetworkStatusHelper(context)
        assertThat(networkStatusHelper.isAvailable()).isTrue()
        verify(networkCapabilities, times(2)).hasCapability(anyInt())
    }
}