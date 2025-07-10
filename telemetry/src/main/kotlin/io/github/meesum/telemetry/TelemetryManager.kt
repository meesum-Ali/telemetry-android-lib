package io.github.meesum.telemetry

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.util.Log
import android.view.Choreographer
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.ServiceAttributes
import okhttp3.Interceptor
import okhttp3.Response
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
    private lateinit var meter: Meter
    private lateinit var logger: Logger
    private lateinit var requestCounter: LongCounter
    private lateinit var jankCounter: LongCounter
    private lateinit var eventCounter: LongCounter

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

    // Track the current screen (Activity/Fragment/Compose)
    @Volatile
    private var currentScreenName: String = "unknown"

    /**
     * Public API for Compose or manual screen tracking
     */
    @JvmStatic
    fun setCurrentScreen(screenName: String) {
        currentScreenName = screenName
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun init(
        application: Application,
        serviceName: String,
        serviceVersion: String,
        environment: String,
        otlpEndpoint: String,
        headers: Map<String, String>,
        traceExporterConfig: TelemetryExporterConfig?,
        metricExporterConfig: TelemetryExporterConfig?,
        logExporterConfig: TelemetryExporterConfig?,
        autoInstrumentActivities: Boolean
    ) {
        if (initialized) return
        initialized = true
        appStartTimeMs = System.currentTimeMillis()

        val resource = Resource.getDefault().merge(
            Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, serviceName)
                .put(ServiceAttributes.SERVICE_VERSION, serviceVersion)
                .put(AttributeKey.stringKey("deployment.environment"), environment)
                .build()
        )

        // Helper to build exporter from config
        fun buildSpanExporter(cfg: TelemetryExporterConfig?): SpanExporter {
            val c = cfg ?: TelemetryExporterConfig(otlpEndpoint, headers)
            return when (c.type) {
                TelemetryExporterConfig.ExporterType.OTLP_GRPC -> OtlpGrpcSpanExporter.builder()
                    .setEndpoint(c.endpoint)
                    .apply { c.headers.forEach { addHeader(it.key, it.value) } }
                    .build()
            }
        }

        fun buildMetricExporter(cfg: TelemetryExporterConfig?): MetricExporter {
            val c = cfg ?: TelemetryExporterConfig(otlpEndpoint, headers)
            return when (c.type) {
                TelemetryExporterConfig.ExporterType.OTLP_GRPC -> OtlpGrpcMetricExporter.builder()
                    .setEndpoint(c.endpoint)
                    .apply { c.headers.forEach { addHeader(it.key, it.value) } }
                    .build()
            }
        }

        fun buildLogExporter(cfg: TelemetryExporterConfig?): LogRecordExporter {
            val c = cfg ?: TelemetryExporterConfig(otlpEndpoint, headers)
            return when (c.type) {
                TelemetryExporterConfig.ExporterType.OTLP_GRPC -> OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(c.endpoint)
                    .apply { c.headers.forEach { addHeader(it.key, it.value) } }
                    .build()
            }
        }

        val spanExporter = buildSpanExporter(traceExporterConfig)
        val metricExporterFinal = buildMetricExporter(metricExporterConfig)
        val logExporterFinal = buildLogExporter(logExporterConfig)

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
                PeriodicMetricReader.builder(metricExporterFinal)
                    .setInterval(Duration.ofSeconds(30))
                    .build()
            )
            .setResource(resource)
            .build()

        loggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(logExporterFinal)
                    .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                    .build()
            )
            .setResource(resource)
            .build()

        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .buildAndRegisterGlobal()

        tracer = sdk.getTracer("telemetry-android")
        meter = sdk.getMeter("telemetry-android")
        logger = sdk.logsBridge.get("telemetry-android")

        requestCounter = meter.counterBuilder("http_client_request_count")
            .setDescription("Number of HTTP requests issued by the app")
            .setUnit("1")
            .build()

        jankCounter = meter.counterBuilder("ui.jank.count")
            .setDescription("Number of jank frames detected")
            .setUnit("1")
            .build()

        eventCounter = meter.counterBuilder("app_event_count")
            .setDescription("Number of events")
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
                measurement.record(used, commonAttributes.toOtelAttributes())
            }

        cpuTimeGauge = meter.gaugeBuilder("app_cpu_time_ms")
            .ofLongs()
            .setDescription("CPU time used by the app in ms")
            .setUnit("ms")
            .buildWithCallback { measurement ->
                measurement.record(Process.getElapsedCpuTime(), commonAttributes.toOtelAttributes())
            }

        uptimeGauge = meter.gaugeBuilder("app_uptime_ms")
            .ofLongs()
            .setDescription("App uptime in ms")
            .setUnit("ms")
            .buildWithCallback { measurement ->
                measurement.record(
                    System.currentTimeMillis() - appStartTimeMs,
                    commonAttributes.toOtelAttributes()
                )
            }

        batteryLevelGauge = meter.gaugeBuilder("device_battery_percent")
            .ofLongs()
            .setDescription("Device battery level percentage")
            .setUnit("1")
            .buildWithCallback { measurement ->
                val bm = application.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                if (bm != null) {
                    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (level >= 0) measurement.record(
                        level.toLong(),
                        commonAttributes.toOtelAttributes()
                    )
                }
            }

        threadCountGauge = meter.gaugeBuilder("app_thread_count")
            .ofLongs()
            .setDescription("Number of active threads in the app")
            .setUnit("1")
            .buildWithCallback { measurement ->
                measurement.record(
                    Thread.activeCount().toLong(),
                    commonAttributes.toOtelAttributes()
                )
            }

        // Setup additional metrics
        setupNetworkMonitoring(context = application)
        setupStorageMonitoring(application)
        setupFrameMonitoring(application)
        setupAppLifecycleMonitoring(application)

        // Auto-instrument activities if requested
        if (autoInstrumentActivities) {
            application.registerActivityLifecycleCallbacks(object :
                Application.ActivityLifecycleCallbacks {
                private var currentSpan: Span? = null
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    // Register fragment lifecycle for screen tracking if possible
                    if (activity is FragmentActivity) {
                        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                            object : FragmentManager.FragmentLifecycleCallbacks() {
                                override fun onFragmentResumed(
                                    fm: FragmentManager,
                                    fragment: Fragment
                                ) {
                                    currentScreenName = fragment.javaClass.simpleName
                                }
                            }, true
                        )
                    }
                }

                override fun onActivityStarted(activity: Activity) {
                    currentSpan =
                        tracer.spanBuilder("ActivityStarted:${activity.javaClass.simpleName}")
                            .startSpan()
                    currentSpan?.makeCurrent()
                }

                override fun onActivityResumed(activity: Activity) {
                    currentScreenName = activity.javaClass.simpleName
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {
                    currentSpan?.end()
                    currentSpan = null
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }

        // Install crash handler
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log(
                level = TelemetryService.LogLevel.ERROR,
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
        level: TelemetryService.LogLevel,
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
            TelemetryService.LogLevel.DEBUG -> record.setSeverity(Severity.DEBUG)
            TelemetryService.LogLevel.INFO -> record.setSeverity(Severity.INFO)
            TelemetryService.LogLevel.WARN -> record.setSeverity(Severity.WARN)
            TelemetryService.LogLevel.ERROR -> record.setSeverity(Severity.ERROR)
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

    override fun incEventCount(
        name: String,
        amount: Long,
        attrs: Attributes
    ) {

        val mergedAttrs = Attributes.builder()
            .putAll(commonAttributes)
            .putAll(attrs)
            .put("event.name", name)
            .build()

        val otelAttrs = mergedAttrs.toOtelAttributes()

        Log.d(
            "TelemetryManager",
            "incEventCount called. Name: $name, Amount: $amount, Attributes: $otelAttrs, Common Attributes: ${commonAttributes.toOtelAttributes()}"
        )

        eventCounter.add(amount, otelAttrs)

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
                    measurement.record(bytes, commonAttributes.toOtelAttributes())
                }
            }

        // Active network type
        activeNetworkGauge = meter.gaugeBuilder("network.active")
            .setDescription("Active network type (0=none, 1=mobile, 2=wifi, 3=ethernet, 4=other)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val networkType = getActiveNetworkType(context)
                measurement.record(networkType.toLong(), commonAttributes.toOtelAttributes())
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
                    measurement.record(usedSpace, commonAttributes.toOtelAttributes())
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
                    val attrs = Attributes.builder()
                        .putAll(commonAttributes)
                        .put("screen.name", currentScreenName)
                        .build()
                    measurement.record(frameTime, attrs.toOtelAttributes())
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
                        val attrs = Attributes.builder()
                            .putAll(commonAttributes)
                            .put("screen.name", currentScreenName)
                            .put("frame.time.ns", frameTime)
                            .put("frame.time.ms", frameTime / 1_000_000)
                            .build()
                        jankCounter.add(1, attrs.toOtelAttributes())
                        // (No log to reduce clutter)
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
                measurement.record(
                    if (activitiesStarted > 0) 1L else 0L,
                    commonAttributes.toOtelAttributes()
                )
            }

        // Screen density
        screenDensityGauge = meter.gaugeBuilder("device.screen.density")
            .setDescription("Screen density in DPI")
            .ofLongs()
            .buildWithCallback { measurement ->
                val metrics = application.resources.displayMetrics
                measurement.record(metrics.densityDpi.toLong(), commonAttributes.toOtelAttributes())
            }

        // Register activity lifecycle callbacks
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?
            ) {
            }

            override fun onActivityStarted(activity: Activity) {
                if (activitiesStarted == 0) {
                    // App came to foreground
                    log(TelemetryService.LogLevel.INFO, "App came to foreground")
                }
                activitiesStarted++
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                activitiesStarted--
                if (activitiesStarted == 0) {
                    // App went to background
                    log(TelemetryService.LogLevel.INFO, "App went to background")
                }
            }

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle
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
            log(TelemetryService.LogLevel.ERROR, "Failed to get network type", throwable = e)
            0
        }
    }

    private fun getNetworkBytesTransferred(): Long {
        return try {
            TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        } catch (e: Exception) {
            log(
                TelemetryService.LogLevel.ERROR,
                "Failed to get network bytes transferred",
                throwable = e
            )
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
            log(TelemetryService.LogLevel.ERROR, "Failed to get storage usage", throwable = e)
            -1
        }
    }

    /**
     * OkHttp Interceptor for automatic network request tracing and metrics.
     */
    class OkHttpTelemetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val span = tracer.spanBuilder("HTTP ${request.method} ${request.url.encodedPath}")
                .setAttribute("http.method", request.method)
                .setAttribute("http.url", request.url.toString())
                .startSpan()
            val scope = span.makeCurrent()
            try {
                val response = chain.proceed(request)
                span.setAttribute("http.status_code", response.code.toLong())
                return response
            } catch (e: Exception) {
                span.recordException(e)
                throw e
            } finally {
                scope.close()
                span.end()
            }
        }
    }

    /**
     * Returns an OkHttp Interceptor for automatic network request tracing/metrics.
     */
    @JvmStatic
    fun createOkHttpInterceptor(): Interceptor = OkHttpTelemetryInterceptor()
}
