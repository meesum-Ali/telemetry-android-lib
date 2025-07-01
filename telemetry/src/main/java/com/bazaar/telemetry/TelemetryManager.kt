package com.bazaar.telemetry

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.util.Log
import android.view.Choreographer
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.bazaar.telemetry.TelemetryService.LogLevel
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.metrics.ObservableLongGauge
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Enhanced OpenTelemetry wrapper for Android apps.
 * - Guards against double-init
 * - Adds global resource attributes (service.name, service.version, environment)
 * - Exposes API to add per-user/session attributes
 * - Supports graceful shutdown/flush
 */
object TelemetryManager : TelemetryService {

    private var initialized = false
    private lateinit var tracerProvider: SdkTracerProvider
    private lateinit var meterProvider: SdkMeterProvider
    private lateinit var loggerProvider: SdkLoggerProvider

    private lateinit var tracer: Tracer
    private lateinit var meter: io.opentelemetry.api.metrics.Meter
    private lateinit var logger: io.opentelemetry.api.logs.Logger
    private lateinit var requestCounter: io.opentelemetry.api.metrics.LongCounter

    // Attributes to apply globally to every span/log/metric
    private var commonAttributes: Attributes = Attributes.empty()

    // Gauges for metrics
    private var memoryUsageGauge: ObservableLongGauge? = null
    private var cpuTimeGauge: ObservableLongGauge? = null
    private var uptimeGauge: ObservableLongGauge? = null
    private var batteryLevelGauge: ObservableLongGauge? = null
    private var threadCountGauge: ObservableLongGauge? = null
    private var networkUsageGauge: ObservableLongGauge? = null
    private var storageUsageGauge: ObservableLongGauge? = null
    private var frameTimeGauge: ObservableLongGauge? = null
    private var activeNetworkGauge: ObservableLongGauge? = null
    private var appStateGauge: ObservableLongGauge? = null
    private var screenDensityGauge: ObservableLongGauge? = null

    // State tracking
    private var appStartTimeMs: Long = 0
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var activitiesStarted = 0
    private var lastFrameTimeNanos = AtomicLong(0)
    private var frameTimeCallback: Choreographer.FrameCallback? = null

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun init(
        application: Application,
        serviceName: String,
        serviceVersion: String,
        environment: String,
        otlpEndpoint: String,
        headers: Map<String, String>
    ) {
        if (initialized) return
        initialized = true
        appStartTimeMs = System.currentTimeMillis()

        // Build resource with global service attributes
        val resource = Resource.getDefault().merge(
            Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, serviceName)
                .put(ServiceAttributes.SERVICE_VERSION, serviceVersion)
                .put(AttributeKey.stringKey("deployment.environment"), environment)
                .build()
        )

        // Create exporters
        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .apply { headers.forEach { addHeader(it.key, it.value) } }
            .build()
        val logExporter = OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(otlpEndpoint)
            .apply { headers.forEach { addHeader(it.key, it.value) } }
            .build()
        val metricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint(otlpEndpoint)
            .apply { headers.forEach { addHeader(it.key, it.value) } }
            .build()

        // Build providers
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(spanExporter)
                    .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                    .build()
            )
            .setResource(resource)
            .build()

        meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(metricExporter)
                    .setInterval(Duration.ofSeconds(30))
                    .build()
            )
            .setResource(resource)
            .build()

        loggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(logExporter)
                    .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                    .build()
            )
            .setResource(resource)
            .build()

        // Register global SDK
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .buildAndRegisterGlobal()

        tracer = sdk.getTracer("telemetry-android")
        meter = sdk.getMeter("telemetry-android")
        logger = sdk.logsBridge.get("telemetry-android")

        // Example counter
        requestCounter = meter.counterBuilder("http_client_request_count")
            .setDescription("Number of HTTP requests issued by the app")
            .setUnit("1")
            .build()

        // Setup gauges for app vitals
        memoryUsageGauge = meter.gaugeBuilder("app_memory_usage_bytes")
            .ofLongs()
            .setDescription("Memory used by the app in bytes")
            .setUnit("By")
            .buildWithCallback { measurement ->
                val runtime = Runtime.getRuntime()
                val used = runtime.totalMemory() - runtime.freeMemory()
                measurement.record(used)
            }

        cpuTimeGauge = meter.gaugeBuilder("app_cpu_time_ms")
            .ofLongs()
            .setDescription("CPU time used by the app in ms")
            .setUnit("ms")
            .buildWithCallback { measurement ->
                measurement.record(Process.getElapsedCpuTime())
            }

        uptimeGauge = meter.gaugeBuilder("app_uptime_ms")
            .ofLongs()
            .setDescription("App uptime in ms")
            .setUnit("ms")
            .buildWithCallback { measurement ->
                measurement.record(System.currentTimeMillis() - appStartTimeMs)
            }

        batteryLevelGauge = meter.gaugeBuilder("device_battery_percent")
            .ofLongs()
            .setDescription("Device battery level percentage")
            .setUnit("1")
            .buildWithCallback { measurement ->
                val bm = application.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                if (bm != null) {
                    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (level >= 0) measurement.record(level.toLong())
                }
            }

        threadCountGauge = meter.gaugeBuilder("app_thread_count")
            .ofLongs()
            .setDescription("Number of active threads in the app")
            .setUnit("1")
            .buildWithCallback { measurement ->
                measurement.record(Thread.activeCount().toLong())
            }

        // Setup additional metrics
        setupNetworkMonitoring(application)
        setupStorageMonitoring(application)
        setupFrameMonitoring(application)
        setupAppLifecycleMonitoring(application)

        // Install crash handler
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log(
                level = LogLevel.ERROR,
                message = "Unhandled exception",
                attrs = Attributes.builder()
                    .put("thread.name", t.name)
                    .put("thread.priority", t.priority)
                    .put("thread.id", t.id)
                    .build(),
                throwable = e
            )
            defaultExceptionHandler?.uncaughtException(t, e)
        }

        Log.i(
            "TelemetryManager",
            "OpenTelemetry initialized. Service: $serviceName, Version: $serviceVersion, Env: $environment"
        )
        Log.i("TelemetryManager", "Metrics OTLP endpoint: $otlpEndpoint, Export interval: 30s")
        Log.i("TelemetryManager", "Traces/Logs OTLP endpoint: $otlpEndpoint")
    }

    /**
     * Set additional common attributes (e.g. user.id, session.id)
     */
    override fun setCommonAttributes(attrs: Attributes) {
        commonAttributes = attrs
        Log.d("TelemetryManager", "Setting common attributes: ${attrs.toOtelAttributes()}")
    }

    /**
     * Force flush and shutdown all SDK providers
     */
    override fun shutdown() {
        Log.i("TelemetryManager", "Shutting down TelemetryManager...")
        Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)

        // Close all gauges
        memoryUsageGauge?.close()
        cpuTimeGauge?.close()
        uptimeGauge?.close()
        batteryLevelGauge?.close()
        threadCountGauge?.close()
        networkUsageGauge?.close()
        storageUsageGauge?.close()
        frameTimeGauge?.close()
        activeNetworkGauge?.close()
        appStateGauge?.close()
        screenDensityGauge?.close()

        // Remove frame callback
        frameTimeCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }

        Log.d("TelemetryManager", "Shutting down TracerProvider.")
        tracerProvider.shutdown()
        Log.d("TelemetryManager", "Shutting down MeterProvider.")
        meterProvider.shutdown()
        Log.d("TelemetryManager", "Shutting down LoggerProvider.")
        loggerProvider.shutdown()
        Log.i("TelemetryManager", "TelemetryManager shutdown complete.")

    }

    override fun <T> span(
        name: String,
        attrs: Attributes,
        block: () -> T
    ): T {
        // merge common + call-specific
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val otelAttrs = mergedAttrs.toOtelAttributes()
        val span = tracer.spanBuilder(name)
            .setAllAttributes(otelAttrs)
            .startSpan()
        val scope: Scope = span.makeCurrent()
        return try {
            block()
        } catch (t: Throwable) {
            span.recordException(t)
            throw t
        } finally {
            scope.close()
            span.end()
        }
    }

    override fun log(
        level: LogLevel,
        message: String,
        attrs: Attributes,
        throwable: Throwable?
    ) {
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val otelAttrs = mergedAttrs.toOtelAttributes()
        val record: LogRecordBuilder = logger.logRecordBuilder()
            .setBody(message)
            .setAllAttributes(otelAttrs)
        if (throwable != null) {
            record.setAttribute(AttributeKey.stringKey("exception.type"), throwable.javaClass.name)
            record.setAttribute(
                AttributeKey.stringKey("exception.message"),
                throwable.message ?: ""
            )
            record.setAttribute(
                AttributeKey.stringKey("exception.stacktrace"),
                Log.getStackTraceString(throwable)
            )
        }
        when (level) {
            LogLevel.DEBUG -> record.setSeverity(io.opentelemetry.api.logs.Severity.DEBUG)
            LogLevel.INFO -> record.setSeverity(io.opentelemetry.api.logs.Severity.INFO)
            LogLevel.WARN -> record.setSeverity(io.opentelemetry.api.logs.Severity.WARN)
            LogLevel.ERROR -> record.setSeverity(io.opentelemetry.api.logs.Severity.ERROR)
        }
        record.emit()
    }

    override fun incRequestCount(
        amount: Long,
        attrs: Attributes
    ) {
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val otelAttrs = mergedAttrs.toOtelAttributes()
        Log.d(
            "TelemetryManager",
            "incRequestCount called. Amount: $amount, Attributes: $otelAttrs, Common Attributes: ${commonAttributes.toOtelAttributes()}"
        )
        requestCounter.add(amount, otelAttrs)
    }

    // Region: Private helper methods for metrics collection

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun setupNetworkMonitoring(context: Context) {
        // Network usage (bytes transferred)
        networkUsageGauge = meter.gaugeBuilder("network.bytes.transferred")
            .setDescription("Network bytes transferred (sent + received)")
            .setUnit("bytes")
            .ofLongs()
            .buildWithCallback { measurement ->
                val bytes = getNetworkBytesTransferred()
                if (bytes >= 0) {
                    measurement.record(bytes)
                }
            }

        // Active network type
        activeNetworkGauge = meter.gaugeBuilder("network.active")
            .setDescription("Active network type (0=none, 1=mobile, 2=wifi, 3=ethernet, 4=other)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val networkType = getActiveNetworkType(context)
                measurement.record(networkType.toLong())
            }
    }

    private fun setupStorageMonitoring(context: Context) {
        storageUsageGauge = meter.gaugeBuilder("storage.used.bytes")
            .setDescription("Used storage space in bytes")
            .setUnit("bytes")
            .ofLongs()
            .buildWithCallback { measurement ->
                val usedSpace = getUsedStorageSpace(context)
                if (usedSpace >= 0) {
                    measurement.record(usedSpace)
                }
            }
    }

    private fun setupFrameMonitoring(context: Context) {
        // Frame time monitoring using Choreographer
        frameTimeGauge = meter.gaugeBuilder("ui.frame.time")
            .setDescription("Frame render time in nanoseconds")
            .setUnit("ns")
            .ofLongs()
            .buildWithCallback { measurement ->
                val frameTime = lastFrameTimeNanos.get()
                if (frameTime > 0) {
                    measurement.record(frameTime)
                }
            }
        // Set up frame callback to measure frame times
        frameTimeCallback = object : Choreographer.FrameCallback {
            private var lastFrameTimeNanos: Long = 0

            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTimeNanos > 0) {
                    val frameTime = frameTimeNanos - lastFrameTimeNanos
                    this@TelemetryManager.lastFrameTimeNanos.set(frameTime)

                    // Detect jank (frames taking longer than 16.67ms for 60fps)
                    if (frameTime > 16_666_667) {
                        log(
                            level = LogLevel.WARN,
                            message = "Jank detected: frame took ${frameTime / 1_000_000}ms",
                            attrs = Attributes.builder()
                                .put("frame.time.ns", frameTime)
                                .put("frame.time.ms", frameTime / 1_000_000)
                                .build()
                        )
                    }
                }
                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        // Start frame time monitoring
        Choreographer.getInstance().postFrameCallback(frameTimeCallback!!)
    }

    private fun setupAppLifecycleMonitoring(application: Application) {
        // App state (foreground/background)
        appStateGauge = meter.gaugeBuilder("app.state")
            .setDescription("App state (0=background, 1=foreground)")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(if (activitiesStarted > 0) 1L else 0L)
            }

        // Screen density
        screenDensityGauge = meter.gaugeBuilder("device.screen.density")
            .setDescription("Screen density in DPI")
            .ofLongs()
            .buildWithCallback { measurement ->
                val metrics = application.resources.displayMetrics
                measurement.record(metrics.densityDpi.toLong())
            }

        // Register activity lifecycle callbacks
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: android.os.Bundle?
            ) {
            }

            override fun onActivityStarted(activity: Activity) {
                if (activitiesStarted == 0) {
                    // App came to foreground
                    log(LogLevel.INFO, "App came to foreground")
                }
                activitiesStarted++
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                activitiesStarted--
                if (activitiesStarted == 0) {
                    // App went to background
                    log(LogLevel.INFO, "App went to background")
                }
            }

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: android.os.Bundle
            ) {
            }

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_NETWORK_STATE])
    private fun getActiveNetworkType(context: Context): Int {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork ?: return 0
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return 0

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 2
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 3
                else -> 4 // Other
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to get network type", throwable = e)
            0
        }
    }

    private fun getNetworkBytesTransferred(): Long {
        return try {
            TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to get network bytes transferred", throwable = e)
            -1
        }
    }

    private fun getUsedStorageSpace(context: Context): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            (totalBlocks - availableBlocks) * blockSize
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to get storage usage", throwable = e)
            -1
        }
    }
}
