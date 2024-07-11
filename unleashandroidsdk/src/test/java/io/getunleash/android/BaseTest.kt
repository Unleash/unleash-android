package io.getunleash.android

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest= Config.NONE, sdk = [21], shadows = [ShadowLog::class])
abstract class BaseTest