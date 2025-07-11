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
- 🎯 **Custom Event Tracking**: Track business events and user interactions with structured attributes
- 🎨 **UI Performance Monitoring**: Frame time metrics and jank detection with screen-level granularity

## Tracing Usage Examples

### Block-based Span (Simple)
```kotlin
TelemetryManager.span("user_login") {
    // Your login logic here
    TelemetryManager.log(TelemetryManager.LogLevel.DEBUG, "User logged in")
}
```

### Manual Parent/Child Spans
```kotlin
val parent = TelemetryManager.startSpan("parentOp")
TelemetryManager.withSpan(parent) {
    val child = TelemetryManager.startSpan("childOp", parent = parent)
    // ... do work ...
    TelemetryManager.endSpan(child)
}
TelemetryManager.endSpan(parent)
```

### Block-based Span with Parent
```kotlin
val parent = TelemetryManager.startSpan("parentOp")
TelemetryManager.span("childOp", parent = parent) {
    // ... do work ...
}
TelemetryManager.endSpan(parent)
```

---

## Installation

The library will be available via GitHub Packages.

> **⚠️ GitHub Packages always requires credentials to consume packages, even if the repository and package are public.**
> You must provide a GitHub username and a Personal Access Token (PAT) with at least `read:packages` scope.

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
            url = uri("https://maven.pkg.github.com/meesum-ali/telemetry-android-lib")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "YOUR_GITHUB_USERNAME"
                password = System.getenv("GITHUB_TOKEN") ?: "YOUR_PERSONAL_ACCESS_TOKEN"
            }
        }
    }
}
```

**2. Add the library to your app's `build.gradle(.kts)`:**

```kotlin
dependencies {
    implementation("io.github.meesum.telemetry:telemetry-release:1.0.0") // or telemetry-debug
}
```

> **Note:** If you do not provide credentials, dependency resolution from GitHub Packages will fail, even for public packages. See [GitHub Docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-maven-registry#authenticating-to-github-packages) for more details.

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

// Track custom events
TelemetryManager.incEventCount(
    name = "user_action",
    attrs = Attributes.builder()
        .put("action_type", "button_click")
        .put("screen", "home")
        .build()
)
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

---

### Network Tracing with Other HTTP Clients

> **Note:** Android does not provide a universal way to intercept all network requests at the app level. Only OkHttp is supported automatically. For other HTTP clients, use manual tracing as shown below.

#### Manual Network Tracing (Any HTTP Client)

You can trace any network request, regardless of the HTTP client, using the raw TelemetryManager APIs:

```kotlin
val span = TelemetryManager.startSpan("HTTP GET $url")
try {
    // Perform your network request here (e.g., HttpURLConnection, Ktor, etc.)
    val response = ... // your network call
    // Optionally record status code or other attributes
    // TelemetryManager.addAttribute(span, "http.status_code", response.code)
} catch (e: Exception) {
    TelemetryManager.log(
        TelemetryService.LogLevel.ERROR,
        "Network request failed",
        throwable = e
    )
    throw e
} finally {
    TelemetryManager.endSpan(span)
}
```

#### Retrofit

Retrofit uses OkHttp under the hood, so just add the interceptor to your OkHttpClient:

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(TelemetryManager.createOkHttpInterceptor())
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .client(okHttpClient)
    .build()
```

#### HttpURLConnection (Manual)

For apps using `HttpURLConnection`, use manual tracing:

```kotlin
val url = URL("https://api.example.com/data")
val span = TelemetryManager.startSpan("HTTP GET ${url.path}")
try {
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    val responseCode = connection.responseCode
    // Optionally record status code
    // TelemetryManager.addAttribute(span, "http.status_code", responseCode)
    // ... read response ...
} catch (e: Exception) {
    TelemetryManager.log(
        TelemetryService.LogLevel.ERROR,
        "HttpURLConnection failed",
        throwable = e
    )
    throw e
} finally {
    TelemetryManager.endSpan(span)
}
```

#### Ktor Client (Manual)

For Ktor, you can use manual tracing in your request pipeline:

```kotlin
val span = TelemetryManager.startSpan("HTTP GET $url")
try {
    val response = client.get(url)
    // Optionally record status code
    // TelemetryManager.addAttribute(span, "http.status_code", response.status.value)
} catch (e: Exception) {
    TelemetryManager.log(
        TelemetryService.LogLevel.ERROR,
        "Ktor request failed",
        throwable = e
    )
    throw e
} finally {
    TelemetryManager.endSpan(span)
}
```

#### Volley (Manual)

Volley does not support interceptors, so use manual tracing:

```kotlin
val span = TelemetryManager.startSpan("HTTP GET $url")
val request = StringRequest(Request.Method.GET, url,
    Response.Listener { response ->
        // Handle response
        TelemetryManager.endSpan(span)
    },
    Response.ErrorListener { error ->
        TelemetryManager.log(
            TelemetryService.LogLevel.ERROR,
            "Volley request failed",
            throwable = error
        )
        TelemetryManager.endSpan(span)
    }
)
requestQueue.add(request)
```

#### Adding Custom Attributes

You can add custom attributes to your spans for more detailed telemetry:

```kotlin
val attrs = Attributes.builder()
    .put("http.method", "GET")
    .put("http.url", url)
    .build()
val span = TelemetryManager.startSpan("HTTP GET $url", attrs)
```

---

For any other HTTP client, follow the manual tracing pattern above. Always end the span in both success and error cases, and optionally record additional attributes (status code, URL, etc.) for richer telemetry.

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

### Custom Event Tracking

Track custom application events with structured attributes:

```kotlin
// Track user interactions
TelemetryManager.incEventCount(
    name = "user_login",
    attrs = Attributes.builder()
        .put("method", "email")
        .put("success", true)
        .put("duration_ms", 1250)
        .build()
)

// Track business events
TelemetryManager.incEventCount(
    name = "purchase_completed",
    attrs = Attributes.builder()
        .put("product_id", "ABC123")
        .put("amount", 29.99)
        .put("currency", "USD")
        .build()
)
```

Custom event attributes are automatically serialized as JSON and included in the `app_event_count` metric with:
- `event.name`: The event name you specified
- `event.attrs`: JSON string containing all custom attributes
- All common attributes (user/session/app info)

This makes it easy to track and analyze user behavior, business metrics, and custom application events in your observability backend.

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
- [x] Custom event tracking with structured attributes
- [x] UI performance monitoring with screen-level granularity
- [x] Jank detection and metrics
- [ ] Automatic fragment instrumentation
- [ ] User-defined custom metrics API
- [ ] Performance profiling integration
