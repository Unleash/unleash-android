package io.getunleash.android

import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest= Config.NONE, sdk = [21])
abstract class BaseTest {

    @Before
    fun setup() {
        ShadowLog.stream = System.out
    }
}

