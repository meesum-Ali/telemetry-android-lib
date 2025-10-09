package io.github.meesum.telemetry

import android.Manifest
import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.github.meesum.telemetry.collectors.*
import io.github.meesum.telemetry.monitors.AnrMonitor
import io.github.meesum.telemetry.monitors.JankMonitor
import io.github.meesum.telemetry.monitors.SlowOperationMonitor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
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
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Refactored OpenTelemetry wrapper for Android apps.
 * - Modular collector architecture
 * - Dedicated monitors for performance tracking
 * - Clean separation of concerns
 */
private class DefaultTelemetrySpan(val delegate: Span) : TelemetrySpan

object TelemetryManager : TelemetryService {

    private var initialized = false
    private lateinit var tracerProvider: SdkTracerProvider
    private lateinit var meterProvider: SdkMeterProvider
    private lateinit var loggerProvider: SdkLoggerProvider

    private lateinit var tracer: Tracer
    private lateinit var meter: Meter
    private lateinit var logger: Logger
    
    // Counters
    private lateinit var requestCounter: LongCounter
    private lateinit var jankCounter: LongCounter
    private lateinit var eventCounter: LongCounter
    private lateinit var anrCounter: LongCounter
    private lateinit var slowOperationCounter: LongCounter
    private lateinit var gcCounter: LongCounter

    // Common attributes applied to all telemetry
    private var commonAttributes: Attributes = Attributes.empty()

    // State tracking
    private var appStartTimeMs: Long = 0
    private var firstActivityResumedTimeMs: Long = 0
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var activitiesStarted = 0
    private var isAppStartupComplete = AtomicBoolean(false)
    private var fragmentLifecycleRegistered = AtomicBoolean(false)

    // Current screen tracking
    @Volatile
    private var currentScreenName: String = "unknown"

    // Collectors - modular metrics collection
    private val collectors = mutableListOf<MetricsCollector>()

    // Monitors - active monitoring
    private var jankMonitor: JankMonitor? = null
    private var anrMonitor: AnrMonitor? = null
    private var slowOperationMonitor: SlowOperationMonitor? = null

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
        autoInstrumentActivities: Boolean,
        enableAnrDetection: Boolean,
        anrThresholdMs: Long,
        enableSlowOperationDetection: Boolean,
        slowOperationThresholdMs: Long,
        enableFragmentTracing: Boolean,
        enableGcMonitoring: Boolean
    ) {
        if (initialized) return
        initialized = true
        appStartTimeMs = System.currentTimeMillis()

        // Build resource
        val resource = Resource.getDefault().merge(
            Resource.builder()
                .put(ServiceAttributes.SERVICE_NAME, serviceName)
                .put(ServiceAttributes.SERVICE_VERSION, serviceVersion)
                .put(AttributeKey.stringKey("deployment.environment"), environment)
                .build()
        )

        // Helper functions to build exporters
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

        // Setup OpenTelemetry SDK
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

        // Setup counters
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

        anrCounter = meter.counterBuilder("app.anr.count")
            .setDescription("Number of ANR (Application Not Responding) events detected")
            .setUnit("1")
            .build()

        slowOperationCounter = meter.counterBuilder("app.slow_operation.count")
            .setDescription("Number of slow operations detected on main thread")
            .setUnit("1")
            .build()

        gcCounter = meter.counterBuilder("app.gc.count")
            .setDescription("Number of garbage collection events")
            .setUnit("1")
            .build()

        // Initialize collectors
        initCollectors(application)

        // Initialize monitors
        initMonitors(
            enableAnrDetection,
            anrThresholdMs,
            enableSlowOperationDetection,
            slowOperationThresholdMs,
            enableGcMonitoring
        )

        // Setup activity lifecycle tracking
        setupActivityLifecycleTracking(
            application,
            autoInstrumentActivities,
            enableFragmentTracing
        )

        // Install crash handler
        setupCrashHandler()

        // Log app metadata
        logAppMetadata(application)

        Log.i(
            "TelemetryManager",
            "OpenTelemetry initialized. Service: $serviceName, Version: $serviceVersion, Env: $environment"
        )
        Log.i("TelemetryManager", "Metrics OTLP endpoint: $otlpEndpoint, Export interval: 30s")
        Log.i("TelemetryManager", "Collectors: ${collectors.size}, Monitors: ${listOfNotNull(jankMonitor, anrMonitor, slowOperationMonitor).size}")
    }

    private fun initCollectors(application: Application) {
        // Add all collectors
        collectors.add(BatteryMetricsCollector(::getCommonAttributes))
        collectors.add(MemoryMetricsCollector(::getCommonAttributes, ::getCurrentScreenName))
        collectors.add(CpuMetricsCollector(::getCommonAttributes, ::getCurrentScreenName))
        collectors.add(StorageMetricsCollector(::getCommonAttributes))
        collectors.add(DisplayMetricsCollector(::getCommonAttributes))
        collectors.add(SystemResourceMetricsCollector(::getCommonAttributes))
        collectors.add(NetworkMetricsCollector(::getCommonAttributes))
        collectors.add(AppStateMetricsCollector(
            ::getCommonAttributes,
            { appStartTimeMs },
            { firstActivityResumedTimeMs },
            { isAppStartupComplete.get() },
            { activitiesStarted }
        ))
        collectors.add(GcMetricsCollector(::getCommonAttributes))

        // Setup all collectors
        collectors.forEach { it.setup(application, meter) }
        
        Log.d("TelemetryManager", "Initialized ${collectors.size} metrics collectors")
    }

    private fun initMonitors(
        enableAnrDetection: Boolean,
        anrThresholdMs: Long,
        enableSlowOperationDetection: Boolean,
        slowOperationThresholdMs: Long,
        enableGcMonitoring: Boolean
    ) {
        // Initialize JankMonitor
        jankMonitor = JankMonitor(
            jankCounter,
            ::getCommonAttributes,
            ::getCurrentScreenName
        ).also { 
            it.start()
            Log.d("TelemetryManager", "JankMonitor started")
        }

        // Add PerformanceMetricsCollector (depends on JankMonitor)
        jankMonitor?.let { jMonitor ->
            collectors.add(PerformanceMetricsCollector(
                ::getCommonAttributes,
                ::getCurrentScreenName,
                jMonitor::getLastFrameTimeNanos,
                jMonitor::getJankCountMild,
                jMonitor::getJankCountModerate,
                jMonitor::getJankCountSevere
            ))
        }

        // Initialize ANR Monitor
        if (enableAnrDetection) {
            anrMonitor = AnrMonitor(
                anrCounter,
                anrThresholdMs,
                ::getCommonAttributes,
                ::getCurrentScreenName,
                ::log
            ).also { 
                it.start()
                Log.d("TelemetryManager", "ANR Monitor started (threshold: ${anrThresholdMs}ms)")
            }
        }

        // Initialize Slow Operation Monitor
        if (enableSlowOperationDetection) {
            slowOperationMonitor = SlowOperationMonitor(
                slowOperationCounter,
                slowOperationThresholdMs,
                ::getCommonAttributes,
                ::getCurrentScreenName,
                ::log
            ).also { 
                it.start()
                Log.d("TelemetryManager", "Slow Operation Monitor started (threshold: ${slowOperationThresholdMs}ms)")
            }
        }
    }

    private fun setupActivityLifecycleTracking(
        application: Application,
        autoInstrumentActivities: Boolean,
        enableFragmentTracing: Boolean
    ) {
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            private var currentSpan: Span? = null

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Register fragment lifecycle for screen tracking and tracing if possible
                if (activity is FragmentActivity && !fragmentLifecycleRegistered.get()) {
                    fragmentLifecycleRegistered.set(true)
                    if (enableFragmentTracing) {
                        setupFragmentTracing(activity)
                    } else {
                        // Just track screen names without full tracing
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
            }

            override fun onActivityStarted(activity: Activity) {
                activitiesStarted++
                if (autoInstrumentActivities) {
                    currentSpan =
                        tracer.spanBuilder("ActivityStarted:${activity.javaClass.simpleName}")
                            .startSpan()
                    currentSpan?.makeCurrent()
                }
            }

            override fun onActivityResumed(activity: Activity) {
                currentScreenName = activity.javaClass.simpleName
                // Mark app startup as complete on first activity resume
                if (!isAppStartupComplete.getAndSet(true)) {
                    firstActivityResumedTimeMs = System.currentTimeMillis()
                    val startupTime = firstActivityResumedTimeMs - appStartTimeMs
                    log(
                        TelemetryService.LogLevel.INFO,
                        "App startup completed",
                        Attributes.builder()
                            .put("startup.time.ms", startupTime)
                            .put("startup.type", "cold")
                            .build()
                    )
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            
            override fun onActivityStopped(activity: Activity) {
                activitiesStarted--
                if (autoInstrumentActivities) {
                    currentSpan?.end()
                    currentSpan = null
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun setupFragmentTracing(activity: FragmentActivity) {
        val fragmentSpans = mutableMapOf<Fragment, Span>()
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                    currentScreenName = fragment.javaClass.simpleName
                    val span = tracer.spanBuilder("Fragment:${fragment.javaClass.simpleName}")
                        .setAllAttributes(commonAttributes.toOtelAttributes())
                        .startSpan()
                    fragmentSpans[fragment] = span
                    span.makeCurrent()
                }

                override fun onFragmentPaused(fm: FragmentManager, fragment: Fragment) {
                    fragmentSpans.remove(fragment)?.end()
                }
            }, true
        )
    }

    private fun setupCrashHandler() {
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
    }

    private fun logAppMetadata(application: Application) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(
                    application.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, 0)
            }

            log(
                TelemetryService.LogLevel.INFO,
                "App metadata collected",
                Attributes.builder()
                    .put("app.install.time", packageInfo.firstInstallTime)
                    .put("app.update.time", packageInfo.lastUpdateTime)
                    .put("app.version.code",
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        }
                    )
                    .put("app.version.name", packageInfo.versionName ?: "unknown")
                    .put("app.package.name", application.packageName)
                    .build()
            )
        } catch (e: Exception) {
            Log.e("TelemetryManager", "Failed to get app metadata", e)
        }
    }

    private fun getCommonAttributes(): Attributes = commonAttributes
    private fun getCurrentScreenName(): String = currentScreenName

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

        // Shutdown all collectors
        collectors.forEach { it.shutdown() }
        collectors.clear()
        Log.d("TelemetryManager", "All collectors shut down")

        // Shutdown all monitors
        jankMonitor?.stop()
        anrMonitor?.stop()
        slowOperationMonitor?.stop()
        Log.d("TelemetryManager", "All monitors stopped")

        // Shutdown OpenTelemetry providers
        Log.d("TelemetryManager", "Shutting down TracerProvider.")
        tracerProvider.shutdown()
        Log.d("TelemetryManager", "Shutting down MeterProvider.")
        meterProvider.shutdown()
        Log.d("TelemetryManager", "Shutting down LoggerProvider.")
        loggerProvider.shutdown()
        Log.i("TelemetryManager", "TelemetryManager shutdown complete.")
    }

    // ==================== Span API ====================

    override fun startSpan(
        name: String,
        attrs: Attributes,
        parent: TelemetrySpan?
    ): TelemetrySpan {
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val otelAttrs = mergedAttrs.toOtelAttributes()
        val builder = tracer.spanBuilder(name).setAllAttributes(otelAttrs)
        if (parent is DefaultTelemetrySpan && parent.delegate != Span.getInvalid()) {
            builder.setParent(io.opentelemetry.context.Context.current().with(parent.delegate))
        }
        return DefaultTelemetrySpan(builder.startSpan())
    }

    override fun endSpan(span: TelemetrySpan) {
        if (span is DefaultTelemetrySpan) {
            span.delegate.end()
        }
    }

    override fun <T> withSpan(span: TelemetrySpan, block: () -> T): T {
        if (span is DefaultTelemetrySpan) {
            val scope = span.delegate.makeCurrent()
            try {
                return block()
            } finally {
                scope.close()
            }
        } else {
            return block()
        }
    }

    override fun <T> span(
        name: String,
        attrs: Attributes,
        parent: TelemetrySpan?,
        block: () -> T
    ): T {
        val sp = startSpan(name, attrs, parent)
        return try {
            withSpan(sp) { block() }
        } finally {
            endSpan(sp)
        }
    }

    // ==================== Logging API ====================

    override fun log(
        level: TelemetryService.LogLevel,
        message: String,
        attrs: Attributes,
        throwable: Throwable?
    ) {
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val builder = logger.logRecordBuilder()
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setSeverity(
                when (level) {
                    TelemetryService.LogLevel.DEBUG -> Severity.DEBUG
                    TelemetryService.LogLevel.INFO -> Severity.INFO
                    TelemetryService.LogLevel.WARN -> Severity.WARN
                    TelemetryService.LogLevel.ERROR -> Severity.ERROR
                }
            )
            .setBody(message)
            .setAllAttributes(mergedAttrs.toOtelAttributes())

        throwable?.let {
            builder.setAllAttributes(
                io.opentelemetry.api.common.Attributes.builder()
                    .putAll(mergedAttrs.toOtelAttributes())
                    .put("exception.type", it.javaClass.name)
                    .put("exception.message", it.message ?: "")
                    .put("exception.stacktrace", it.stackTraceToString())
                    .build()
            )
        }
        builder.emit()
    }

    // ==================== Metrics API ====================

    override fun incRequestCount(amount: Long, attrs: Attributes) {
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        requestCounter.add(amount, mergedAttrs.toOtelAttributes())
    }

    override fun incEventCount(name: String, amount: Long, attrs: Attributes) {
        val mergedAttrs = Attributes.builder()
            .putAll(commonAttributes)
            .putAll(attrs)
            .put("event.name", name)
            .build()
        eventCounter.add(amount, mergedAttrs.toOtelAttributes())
    }

    // ==================== Network Monitoring ====================

    /**
     * Returns an OkHttp Interceptor for automatic network request tracing and metrics.
     * 
     * Usage:
     * ```kotlin
     * val client = OkHttpClient.Builder()
     *     .addInterceptor(TelemetryManager.createOkHttpInterceptor())
     *     .build()
     * ```
     * 
     * The interceptor automatically:
     * - Creates spans for each HTTP request with method, URL, status code
     * - Records exceptions for failed requests
     * - Increments the http_client_request_count metric with labels
     * - Propagates OpenTelemetry context across network boundaries
     */
    @JvmStatic
    fun createOkHttpInterceptor(): Interceptor {
        if (!initialized) {
            throw IllegalStateException("TelemetryManager must be initialized before creating interceptor")
        }
        return OkHttpTelemetryInterceptor(
            tracer = tracer,
            requestCounter = requestCounter,
            getCommonAttributes = ::getCommonAttributes
        )
    }
}
