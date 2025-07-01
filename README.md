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
- 🧩 **Custom Exporters**: Plug in your own OpenTelemetry exporters
- ⚡ **Automatic Activity Instrumentation**: Optionally trace Activity lifecycle automatically
- 🌐 **Network Request Monitoring**: OkHttp interceptor for automatic HTTP tracing/metrics

## Installation

The library will be available via GitHub Packages.

**1. Configure GitHub Packages repository in your `settings.gradle(.kts)` or root `build.gradle(.kts)`:**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME_OR_ORG/YOUR_REPOSITORY_NAME") // TODO: Replace with the correct OWNER/REPO
            // You might need credentials if the package is private or your organization requires it
            // credentials {
            //     username = System.getenv("GITHUB_ACTOR")
            //     password = System.getenv("GITHUB_TOKEN")
            // }
        }
    }
}
```

**2. Add the library to your app's `build.gradle(.kts)`:**

```kotlin
dependencies {
    // TODO: Replace with the correct groupId, artifactId and version after publishing
    implementation("io.github.meesum.telemetry:telemetry:1.0.0")
}
```

Alternatively, you can build from source or use a local build.

### Building from Source

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

### Custom Exporters (Minimal Config)

You can provide your own exporter configuration with just endpoint, headers, and type (no OpenTelemetry types needed):

```kotlin
TelemetryManager.init(
    application = this,
    serviceName = "MyAndroidApp",
    serviceVersion = BuildConfig.VERSION_NAME,
    environment = if (BuildConfig.DEBUG) "debug" else "production",
    otlpEndpoint = "http://your-otel-collector:4317",
    traceExporterConfig = TelemetryExporterConfig(
        endpoint = "http://custom-trace-endpoint:4317",
        headers = mapOf("authorization" to "Bearer ...")
    ),
    // metricExporterConfig and logExporterConfig are similar, or can be omitted for defaults
)
```

### Automatic Activity Instrumentation

Enable automatic tracing of Activity lifecycle events:

```kotlin
TelemetryManager.init(
    application = this,
    serviceName = "MyAndroidApp",
    serviceVersion = BuildConfig.VERSION_NAME,
    environment = if (BuildConfig.DEBUG) "debug" else "production",
    otlpEndpoint = "http://your-otel-collector:4317",
    autoInstrumentActivities = true
)
```

### Network Request Monitoring (OkHttp)

Add the provided interceptor to your OkHttp client for automatic HTTP tracing and metrics:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(TelemetryManager.createOkHttpInterceptor())
    .build()
```

### Frame Metrics with Screen Name

Frame time metrics now include a `screen.name` label, so you can identify which Activity, Fragment, or Compose screen is being measured.

- For Activities: The screen name is set to the Activity's class name automatically.
- For Fragments: The screen name is set to the Fragment's class name when it is resumed.
- For Jetpack Compose or custom navigation: Call `TelemetryManager.setCurrentScreen("YourScreenName")` whenever the screen changes.

**Example for Compose:**
```kotlin
// In your Composable or navigation handler
LaunchedEffect(currentScreen) {
    TelemetryManager.setCurrentScreen(currentScreen)
}
```

This makes it easy to filter and analyze frame performance by screen in your observability backend.

### Jank Metrics

Jank events (frames taking longer than 16.67ms) are now recorded as a metric (`ui.jank.count`) instead of logs, reducing log clutter and making analysis easier.

- The metric includes:
  - `screen.name`: The current Activity, Fragment, or Compose screen
  - All common attributes (e.g., user/session/app info)
  - `frame.time.ns` and `frame.time.ms`: The duration of the jank frame

You can now easily chart and alert on jank rates by screen and any attribute in your observability backend (e.g., Grafana, Prometheus).

## Local Development & Publishing

### Building the Library

1.  Clone the repository.
2.  Open in Android Studio or your preferred IDE.
3.  Build the project:
    ```bash
    ./gradlew :telemetry:build
    ```

### Publishing to Maven Local

This is useful for testing the package locally in other projects on your machine.

1.  Ensure the `groupId`, `artifactId`, and `version` in `telemetry/build.gradle.kts` are set as desired.
2.  Run the publish task:
    ```bash
    ./gradlew :telemetry:publishToMavenLocal
    ```
3.  In your other project, make sure `mavenLocal()` is listed as a repository in `settings.gradle(.kts)` or `build.gradle(.kts)`:
    ```kotlin
    // settings.gradle.kts
    dependencyResolutionManagement {
        repositories {
            mavenLocal()
            // ... other repositories
        }
    }
    ```
4.  Then, add the dependency with the same `groupId`, `artifactId`, and `version` you published.

### Publishing to GitHub Packages

This makes the package available via GitHub's package registry.

1.  **Prerequisites:**
    *   Ensure you have a GitHub Personal Access Token (PAT) with `write:packages` scope.
    *   Set `GITHUB_ACTOR` (your GitHub username) and `GITHUB_TOKEN` (your PAT) as environment variables. For GitHub Actions, these are typically provided automatically.
    *   In `telemetry/build.gradle.kts`, update the GitHub Packages repository URL with your GitHub username/organization and the repository name:
        ```kotlin
        publishing {
            repositories {
                maven {
                    name = "GitHubPackages"
                    // TODO: Replace YOUR_GITHUB_USERNAME_OR_ORG and YOUR_REPOSITORY_NAME
                    url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME_OR_ORG/YOUR_REPOSITORY_NAME")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
        ```
    *   Fill in the POM details (name, description, URL, license, developers, SCM) in `telemetry/build.gradle.kts` for the `release` publication.

2.  **Publish:**
    Run the following command:
    ```bash
    ./gradlew :telemetry:publishReleasePublicationToGitHubPackagesRepository
    ```
    (The task name might simplify to `./gradlew :telemetry:publish` if no other remote repositories are configured for the publication).

3.  **Consuming from GitHub Packages:**
    Follow the "Installation" section at the beginning of this README, ensuring the repository URL in the consumer's `settings.gradle(.kts)` points to your GitHub Packages repository.

## Testing

Run the test suite with:

```bash
./gradlew test
```

## Testing with a Local LGTM Stack

If you want to test your telemetry integration with a local LGTM (Loki, Grafana, Tempo, Mimir) stack, you can use the [otel-stack](https://github.com/meesum-Ali/otel-stack) repository:

1. Clone the repo:
   ```bash
   git clone https://github.com/meesum-Ali/otel-stack.git
   cd otel-stack
   ```
2. Start the stack:
   ```bash
   docker-compose up -d
   ```
   This will start the OTel Collector, Tempo, Loki, Prometheus, and Grafana.

   - Grafana: http://localhost:3000
   - OTel Collector: http://localhost:4317
   - Tempo: http://localhost:3200
   - Prometheus: http://localhost:9090
   - Loki: http://localhost:3100

3. Point your TelemetryManager `otlpEndpoint` to `http://localhost:4317`.
4. View traces/logs/metrics in Grafana at http://localhost:3000 (default login: admin/admin).

For more details, see the [otel-stack README](https://github.com/meesum-Ali/otel-stack).

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

- [x] Add more built-in metrics
- [x] Support for custom exporters (minimal config, no OpenTelemetry types in API)
- [x] Automatic activity/fragment instrumentation (Activity supported)
- [x] Network request monitoring (OkHttp supported)
- [ ] Automatic fragment instrumentation
- [ ] User-defined custom metrics API
