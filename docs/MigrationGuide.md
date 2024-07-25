# Migration Guide from unleash-android-proxy-sdk

This guide provides detailed steps for migrating your project from the Unleash Android Proxy SDK version to the newer Unleash Android SDK.

We will focus on the [previous sample application](https://github.com/Unleash/unleash-android-proxy-sdk/tree/main/samples/android) and specifically highlighting the changes from this pull request: https://github.com/Unleash/unleash-android-proxy-sdk/pull/83

## Benefits of Migrating

This version of the Unleash Android SDK introduces several improvements, including:
- No need for SLF4J dependency and the usage of the native Android logging system.
- Respecting the Android lifecycle to stop polling and sending metrics in the background.
- The new SDK respects the minimum Android API level 21, but we recommend API level 23.
- Monitoring network connectivity to avoid unnecessary polling (requires API level 23 or above).

## Overview

The new SDK introduces several changes and improvements, including API modifications and a new initialization process.

## Step-by-Step Migration

### 1. Update Gradle Dependency

First, update the dependency in your `build.gradle` file:

```gradle
dependencies {
    // Remove the old SDK
    implementation 'io.getunleash:unleash-android-proxy-sdk:0.5.0'

    // Add the new SDK
    implementation 'io.getunleash:unleash-android:$version'
}
```

### 2. Update the initialization code
We won't cover all the details here as most of the configuration can be set using the builders fluent methods. However, the main difference is that the new SDK requires an `Application` context to be passed to the `Unleash` constructor. This is necessary to monitor the network connectivity and respect the Android lifecycle (usually it can be injected if using hilt with `@ApplicationContext context`).
 
#### Unleash context initialization
The main differences are:
1. The application name is no longer configurable through the context, as it is constant throughout the application's lifetime. The `appName` should be set using the `UnleashConfig` builder.
2. The instance ID is no longer configurable. The SDK will generate a unique instance ID for each instance.
3. Update the import statements to use the new SDK classes.

```kotlin
val unleashContext = UnleashContext.newBuilder()
    // .appName("unleash-android") // remove this line
    // .instanceId("main-activity-unleash-demo-${Random.nextLong()}") // remove this line
    .userId("unleash_demo_user")
    .sessionId(Random.nextLong().toString())
    .build()
```

#### Unleash configuration
The main differences are:
1. Metrics are enabled by default.
2. App name is now a mandatory parameter to the builder.
3. Instance id is no longer configurable.
4. The polling mode is now a polling strategy with a fluent API.
5. The metrics interval is now part of the metrics strategy with a fluent API.

**Old SDK**
```kotlin
UnleashConfig.newBuilder()
    .appName("unleash-android")
    .instanceId("unleash-android-${Random.nextLong()}")
    .enableMetrics()
    .clientKey("xyz")
    .proxyUrl("https://eu.app.unleash-hosted.com/demo/api/frontend")
    .pollingMode(
        PollingModes.autoPoll(
            autoPollIntervalSeconds = 15
        ) {

        }
    )
    .metricsInterval(5000)
    .build()
```

**New SDK**
```kotlin
UnleashConfig.newBuilder("unleash-android")
    .clientKey("xyz")
    .proxyUrl("https://eu.app.unleash-hosted.com/demo/api/frontend")
    .pollingStrategy.interval(15000)
    .metricsStrategy.interval(5000)
    .build()
```

#### Creating the Unleash instance
The previous SDK used a builder to construct the Unleash instance while the new SDK relies on constructor parameters. There are also other meaningful changes:

1. The new SDK does not start automatically. You need to call `unleash.start()` to start the polling and metrics collection.
2. The new SDK accepts event listeners at the constructor level or as parameters when calling `unleash.start()` (you can also edit your config object setting `delayedInitialization` to false).
3. The interface `UnleashClientSpec` is now `Unleash`.

```kotlin
UnleashClient.newBuilder()
    .unleashConfig(unleashConfig)
    .cache(InMemoryToggleCache()) // This was optional in the previous SDK and in the new one so we are ignoring it
    .unleashContext(unleashContext)
    .build()
```

**New SDK**
_Note:_ Android context is now required to be passed to the Unleash constructor and you will usually want it to be bound to the application context.

```kotlin
val unleash = DefaultUnleash(
            androidContext = context,
            unleashConfig = unleashConfig,
            unleashContext = unleashContext
        )
unleash.start()
```

#### Updating class references
Most of the classes have been moved to `io.getunleash.android` package. Update the import statements in your classes.
