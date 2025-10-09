# Telemetry Android Library

A lightweight Android library that provides a simple wrapper around OpenTelemetry for instrumenting Android applications with distributed tracing, metrics, and logging. Seamlessly integrates with Grafana's LGTM (Loki, Grafana, Tempo, Mimir) stack.

## Features

- 📊 **Tracing**: Distributed tracing with OpenTelemetry
- 📈 **Metrics**: Built-in metrics collection
- 📝 **Structured Logging**: Log collection with different severity levels
- 📊 **App Vitals**: Enhanced memory metrics (usage, heap, pressure), CPU usage percentage, battery, thread count and uptime exported automatically with screen context
- 🛑 **Crash Reporting**: Uncaught exceptions are logged
- 🚀 **Easy Integration**: Simple setup and initialization
- 🔌 **Grafana LGTM Ready**: Pre-configured for Grafana's observability stack
- 🛡️ **Production Ready**: Built with performance and reliability in mind
- 🧩 **Custom Exporters**: Plug in your own OpenTelemetry exporters
- ⚡ **Automatic Activity Instrumentation**: Optionally trace Activity lifecycle automatically
- 🧩 **Fragment Lifecycle Tracing**: Full Fragment lifecycle instrumentation
- 🌐 **Network Request Monitoring**: OkHttp interceptor for automatic HTTP tracing/metrics
- 🎯 **Custom Event Tracking**: Track business events and user interactions with structured attributes
- 🎨 **UI Performance Monitoring**: Frame time metrics and jank detection with screen-level granularity
- 🚨 **ANR Detection**: Automatic detection of Application Not Responding events
- 🐢 **Slow Operation Detection**: Identify operations blocking the main thread
- ⏱️ **Startup Time Tracking**: Monitor app cold and warm start times
- 🗑️ **GC Monitoring**: Track garbage collection patterns
- 🔄 **Coroutine Tracing**: Context propagation for Kotlin coroutines

## Architecture

The library follows a **modular collector architecture** for clean separation of concerns, maintainability, and scalability.

### Modular Design

```
telemetry/
├── collectors/                    # Metrics Collectors
│   ├── MetricsCollector.kt       # Base interface
│   ├── BatteryMetricsCollector.kt
│   ├── MemoryMetricsCollector.kt
│   ├── CpuMetricsCollector.kt
│   ├── StorageMetricsCollector.kt
│   ├── DisplayMetricsCollector.kt
│   ├── SystemResourceMetricsCollector.kt
│   ├── NetworkMetricsCollector.kt
│   ├── PerformanceMetricsCollector.kt
│   ├── AppStateMetricsCollector.kt
│   └── GcMetricsCollector.kt
│
├── monitors/                      # Active Monitoring
│   ├── JankMonitor.kt            # Frame & jank detection
│   ├── AnrMonitor.kt             # ANR detection
│   └── SlowOperationMonitor.kt   # Slow operation detection
│
├── TelemetryManager.kt           # Core orchestrator (~680 lines)
├── TelemetryService.kt           # Public API interface
├── TelemetryConfig.kt            # Configuration data class
├── Attributes.kt                 # Type-safe attributes
└── FlowTracingExtensions.kt      # Coroutine Flow tracing
```

### Benefits

- **✅ Separation of Concerns**: Each collector handles one metric category
- **✅ Testability**: Test collectors independently
- **✅ Scalability**: Add new metrics by creating new collectors
- **✅ Maintainability**: Small, focused files (50-150 lines each)
- **✅ Configurability**: Easy to enable/disable collectors

### Collectors Overview

| Collector | Metrics | Description |
|-----------|---------|-------------|
| **BatteryMetricsCollector** | Battery level, temperature, voltage, health, charging status | Complete battery health monitoring |
| **MemoryMetricsCollector** | Usage, max, heap size, pressure % | Memory metrics with screen context |
| **CpuMetricsCollector** | CPU time, usage % | CPU monitoring with screen context |
| **StorageMetricsCollector** | Used/available storage, cache/data size | Storage utilization tracking |
| **DisplayMetricsCollector** | Screen width/height, refresh rate, orientation, density | Display characteristics |
| **SystemResourceMetricsCollector** | Total/available RAM, CPU cores, thermal state, thread count | System-wide resources |
| **NetworkMetricsCollector** | Bytes transferred, active network type | Network usage tracking |
| **PerformanceMetricsCollector** | Frame time, jank counts by severity | UI performance metrics |
| **AppStateMetricsCollector** | Uptime, startup time, foreground/background state | App lifecycle metrics |
| **GcMetricsCollector** | GC invocations | Garbage collection patterns |

### Monitors Overview

| Monitor | Purpose | Configuration |
|---------|---------|---------------|
| **JankMonitor** | Detects UI jank (mild, moderate, severe) | Always enabled |
| **AnrMonitor** | Detects ANR events | `enableAnrDetection`, `anrThresholdMs` |
| **SlowOperationMonitor** | Detects slow main thread operations | `enableSlowOperationDetection`, `slowOperationThresholdMs` |

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

### Advanced Auto-Instrumentation Features

The library now includes several advanced auto-instrumentation capabilities that can be configured during initialization:

```kotlin
TelemetryManager.init(
    application = this,
    serviceName = "MyAndroidApp",
    serviceVersion = BuildConfig.VERSION_NAME,
    environment = if (BuildConfig.DEBUG) "debug" else "production",
    otlpEndpoint = "http://your-otel-collector:4317",
    autoInstrumentActivities = true,
    
    // ANR Detection - detects when main thread is blocked
    enableAnrDetection = true,
    anrThresholdMs = 5000, // Report ANR if main thread blocked > 5 seconds
    
    // Slow Operation Detection - detects operations blocking the main thread
    enableSlowOperationDetection = true,
    slowOperationThresholdMs = 100, // Report if operation takes > 100ms
    
    // Fragment Lifecycle Tracing - full fragment lifecycle instrumentation
    enableFragmentTracing = true,
    
    // GC Monitoring - track garbage collection patterns
    enableGcMonitoring = true
)
```

#### ANR (Application Not Responding) Detection

The library automatically monitors the main thread and detects when it becomes unresponsive. When an ANR is detected:
- A metric (`app.anr.count`) is incremented
- An error log is created with details about the ANR duration and current screen
- Attributes include: `anr.duration.ms`, `screen.name`, `thread.main.blocked`

#### Slow Operation Detection

Detects operations that block the main thread beyond a configurable threshold:
- Metric: `app.slow_operation.count`
- Attributes: `operation.duration.ms`, `screen.name`, `operation.threshold.ms`
- Helps identify performance bottlenecks in your app

#### Fragment Lifecycle Tracing

When enabled, the library automatically traces all Fragment lifecycle events:
- Creates spans for: `created`, `started`, `resumed`, `paused`, `stopped`, `destroyed`
- Attributes include: `fragment.name`, `lifecycle.event`
- Screen name is automatically updated when fragments are resumed

#### Startup Time Tracking

The library automatically tracks app startup time:
- Measures time from application initialization to first activity resume
- Metric: `app.startup.time` (in milliseconds)
- Logs startup completion with `startup.time.ms` and `startup.type` attributes

#### GC (Garbage Collection) Monitoring

Tracks garbage collection patterns:
- Metric: `app.gc.invocations` (heuristic-based count)
- Counter: `app.gc.count` incremented for each detected GC event
- Helps identify memory pressure issues

### Network Request Monitoring (OkHttp)

Add the provided interceptor to your OkHttp client for automatic HTTP tracing and metrics:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(TelemetryManager.createOkHttpInterceptor())
    .build()
```

**The interceptor automatically:**
- ✅ Creates spans for each HTTP request with method, URL, host, scheme
- ✅ Records HTTP status codes and response content length
- ✅ Tracks request metrics with labels (method, status code, host)
- ✅ Records exceptions for failed requests with error details
- ✅ Propagates OpenTelemetry context across network boundaries
- ✅ Increments `http_client_request_count` metric with rich metadata

**Captured Attributes:**
- `http.method` - Request method (GET, POST, etc.)
- `http.url` - Full request URL
- `http.host` - Target host
- `http.scheme` - URL scheme (http/https)
- `http.status_code` - Response status code
- `http.response.content_length` - Response body size
- `error` - Boolean flag for failed requests
- `error.type` - Exception type for failures

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

### Enhanced Jank Metrics

Jank events are now intelligently categorized by severity and recorded with comprehensive metrics:

**Jank Severity Levels:**
- **Mild** (16-33ms): 1 dropped frame, still acceptable for most apps
- **Moderate** (33-50ms): 2-3 dropped frames, noticeable lag
- **Severe** (>50ms): 3+ dropped frames, significant jank (logged as warning)

**Metrics:**
- `ui.jank.count` - Counter incremented on each jank event
- `ui.jank.count.mild` - Total mild jank events
- `ui.jank.count.moderate` - Total moderate jank events
- `ui.jank.count.severe` - Total severe jank events

**Attributes included:**
- `screen.name`: Current Activity, Fragment, or Compose screen
- `jank.severity`: mild, moderate, or severe
- `frame.time.ms`: Frame render time in milliseconds
- `frames.dropped`: Number of frames dropped
- `fps`: Calculated frames per second during jank
- All common attributes (user/session/app info)

**Example Queries:**
```promql
# Severe jank rate by screen
rate(ui_jank_count_total{jank_severity="severe"}[5m]) by (screen_name)

# Total jank count by severity
sum(ui_jank_count_total) by (jank_severity)

# Screens with most jank
topk(5, sum(rate(ui_jank_count_total[5m])) by (screen_name))
```

### Enhanced Memory Metrics

Memory monitoring now includes comprehensive metrics with screen context:

**Metrics:**
- `app_memory_usage_bytes` - Current memory usage
- `app_memory_max_bytes` - Maximum memory available to app
- `app_memory_heap_size_bytes` - Current heap size allocated
- `app_memory_pressure_percent` - Memory pressure (0-100%)

**All metrics include:**
- `screen.name`: Current screen for context
- Additional attributes: `memory.used.bytes`, `memory.max.bytes`

**Example Queries:**
```promql
# Memory pressure by screen
app_memory_pressure_percent by (screen_name)

# Memory usage trend
rate(app_memory_usage_bytes[5m])

# Screens with highest memory pressure
topk(5, avg(app_memory_pressure_percent) by (screen_name))
```

### Enhanced CPU Metrics

CPU monitoring now includes both absolute time and usage percentage:

**Metrics:**
- `app_cpu_time_ms` - Total CPU time consumed (milliseconds)
- `app_cpu_usage_percent` - CPU usage percentage (0-100%)

**Attributes included:**
- `screen.name`: Current screen
- `cpu.time.delta.ms`: CPU time delta since last measurement
- `time.delta.ms`: Wall clock time delta

**Example Queries:**
```promql
# CPU usage by screen
avg(app_cpu_usage_percent) by (screen_name)

# CPU spikes detection
delta(app_cpu_time_ms[1m]) > 1000

# Top CPU consuming screens
topk(5, rate(app_cpu_time_ms[5m]) by (screen_name))
```

### Coroutine Tracing

The library provides seamless tracing support for Kotlin coroutines with automatic context propagation:

```kotlin
import io.github.meesum.telemetry.traceCoroutine
import io.github.meesum.telemetry.traced

// Trace a suspending function
suspend fun fetchUserData(userId: String): User = traceCoroutine(
    name = "fetchUserData",
    attrs = Attributes.builder()
        .put("user.id", userId)
        .build()
) {
    // Your async operation here
    apiClient.getUser(userId)
}

// Use within a CoroutineScope with automatic parent span detection
suspend fun loadProfile() = coroutineScope {
    traced("loadProfile") {
        val user = traced("fetchUser") {
            // Child span automatically linked to parent
            fetchUserData("123")
        }
        
        val posts = traced("fetchPosts") {
            // Another child span
            fetchUserPosts(user.id)
        }
        
        Profile(user, posts)
    }
}
```

Benefits:
- Automatic span creation for suspending functions
- Context propagation across coroutine boundaries
- Parent-child span relationships preserved
- Exception tracking in coroutines
- Works with structured concurrency

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
- [x] Automatic activity instrumentation
- [x] Automatic fragment instrumentation with full lifecycle tracing
- [x] Network request monitoring (OkHttp supported)
- [x] Custom event tracking with structured attributes
- [x] UI performance monitoring with screen-level granularity
- [x] Jank detection and metrics
- [x] ANR (Application Not Responding) detection
- [x] Slow operation detection on main thread
- [x] App startup time tracking
- [x] GC (Garbage Collection) monitoring
- [x] Coroutine tracing with context propagation
- [ ] User-defined custom metrics API
- [ ] Performance profiling integration
- [ ] Database operation instrumentation (Room/SQLite)
- [ ] Image loading instrumentation (Glide/Coil/Picasso)
- [ ] WorkManager instrumentation
