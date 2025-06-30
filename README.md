
# Telemetry Android Library

A lightweight Android library that provides a simple wrapper around OpenTelemetry for instrumenting Android applications with distributed tracing, metrics, and logging. Seamlessly integrates with Grafana's LGTM (Loki, Grafana, Tempo, Mimir) stack.

## Features

- 📊 **Tracing**: Distributed tracing with OpenTelemetry
- 📈 **Metrics**: Built-in metrics collection
- 📝 **Structured Logging**: Log collection with different severity levels
- 📊 **App Vitals**: Memory, CPU, battery, thread count and uptime exported automatically
- 🛑 **Crash Reporting**: Uncaught exceptions are logged
- 🚀 **Easy Integration**: Simple setup and initialization
- 🔌 **Grafana LGTM Ready**: Pre-configured for Grafana's observability stack
- 🛡️ **Production Ready**: Built with performance and reliability in mind

## Installation

Add the library to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.yourusername:telemetry-android:1.0.0'
}
```

Or build from source:

```bash
./gradlew :telemetry:assembleRelease
```

## Quick Start

1. Initialize the library in your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        TelemetryManager.init(
            application = this,
            serviceName = "MyAndroidApp",
            serviceVersion = BuildConfig.VERSION_NAME,
            environment = if (BuildConfig.DEBUG) "debug" else "production",
            otlpEndpoint = "http://your-otel-collector:4317",
            headers = mapOf(
                "authorization" to "Bearer your-auth-token" // optional
            )
        )
    }
}
```

2. Start using telemetry in your app:

```kotlin
// Log messages
TelemetryManager.log(TelemetryManager.LogLevel.INFO, "App started")

// Create spans
TelemetryManager.span("user_login") {
    // Your login logic here
    TelemetryManager.log(TelemetryManager.LogLevel.DEBUG, "User logged in")
}

// Increment a counter
TelemetryManager.incRequestCount()
```

## Advanced Usage

### Configuration Options

You can customize the telemetry collection by providing additional headers and setting common attributes:

```kotlin
// In your Application class
TelemetryManager.init(
    application = this,
    serviceName = "MyAndroidApp",
    serviceVersion = BuildConfig.VERSION_NAME,
    environment = if (BuildConfig.DEBUG) "debug" else "production",
    otlpEndpoint = "http://your-otel-collector:4317",
    headers = mapOf(
        "authorization" to "Bearer your-auth-token",
        "x-custom-header" to "custom-value"
    )
)

// Set common attributes that will be included in all telemetry data
TelemetryManager.setCommonAttributes(
    Attributes.builder()
        .put("app.version.code", BuildConfig.VERSION_CODE.toString())
        .put("app.build.type", if (BuildConfig.DEBUG) "debug" else "release")
        .put("device.model", Build.MODEL)
        .put("os.version", Build.VERSION.RELEASE)
        .build()
)
```

### Custom Spans

```kotlin
TelemetryManager.span("complex_operation") { span ->
    try {
        // Your operation
        span.setAttribute("operation.status", "success")
    } catch (e: Exception) {
        span.recordException(e)
        span.setAttribute("operation.status", "failed")
        throw e
    }
}
```

### Logging

```kotlin
// Different log levels
TelemetryManager.log(TelemetryManager.LogLevel.DEBUG, "Debug message")
TelemetryManager.log(TelemetryManager.LogLevel.INFO, "Info message")
TelemetryManager.log(TelemetryManager.LogLevel.WARN, "Warning message")
TelemetryManager.log(TelemetryManager.LogLevel.ERROR, "Error message")

// With exception
TelemetryManager.log(
    level = TelemetryManager.LogLevel.ERROR,
    message = "Operation failed",
    throwable = exception
)
```

## Local Development

1. Clone the repository
2. Open in Android Studio or your preferred IDE
3. Build the project:
   ```bash
   ./gradlew build
   ```

## Testing

Run the test suite with:

```bash
./gradlew test
```

## Contributing

Contributions are welcome! Please read our [contributing guidelines](CONTRIBUTING.md) before submitting pull requests.

## License

```
MIT License

Copyright (c) 2025 Your Name

Permission is hereby granted...
```

## Support

For support, please open an issue in the GitHub repository.

## Roadmap

- [ ] Add more built-in metrics
- [ ] Support for custom exporters
- [ ] Automatic activity/fragment instrumentation
- [ ] Network request monitoring
