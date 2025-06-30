package com.bazaar.telemetry

import android.app.Application
import android.os.Build
import android.util.Log
import android.os.Process
import android.content.Context
import android.os.BatteryManager
import androidx.annotation.RequiresApi
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.metrics.ObservableLongGauge
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

    private var memoryUsageGauge: ObservableLongGauge? = null
    private var cpuTimeGauge: ObservableLongGauge? = null
    private var uptimeGauge: ObservableLongGauge? = null
    private var batteryLevelGauge: ObservableLongGauge? = null
    private var threadCountGauge: ObservableLongGauge? = null
    private var appStartTimeMs: Long = 0
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

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
                if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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

        // Install crash handler
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log(
                level = TelemetryService.LogLevel.ERROR,
                message = "Unhandled exception",
                attrs = Attributes.builder().put("thread.name", t.name).build(),
                throwable = e
            )
            defaultExceptionHandler?.uncaughtException(t, e)
        }

        Log.i("TelemetryManager", "OpenTelemetry initialized. Service: $serviceName, Version: $serviceVersion, Env: $environment")
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
        memoryUsageGauge?.close()
        cpuTimeGauge?.close()
        uptimeGauge?.close()
        batteryLevelGauge?.close()
        threadCountGauge?.close()
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
            record.setAttribute(AttributeKey.stringKey("exception.message"), throwable.message ?: "")
            record.setAttribute(AttributeKey.stringKey("exception.stacktrace"), Log.getStackTraceString(throwable))
        }
        when (level) {
            TelemetryService.LogLevel.DEBUG -> record.setSeverity(io.opentelemetry.api.logs.Severity.DEBUG)
            TelemetryService.LogLevel.INFO -> record.setSeverity(io.opentelemetry.api.logs.Severity.INFO)
            TelemetryService.LogLevel.WARN -> record.setSeverity(io.opentelemetry.api.logs.Severity.WARN)
            TelemetryService.LogLevel.ERROR -> record.setSeverity(io.opentelemetry.api.logs.Severity.ERROR)
        }
        record.emit()
    }

    override fun incRequestCount(
        amount: Long,
        attrs: Attributes
    ) {
        val mergedAttrs = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val otelAttrs = mergedAttrs.toOtelAttributes()
        Log.d("TelemetryManager", "incRequestCount called. Amount: $amount, Attributes: $otelAttrs, Common Attributes: ${commonAttributes.toOtelAttributes()}")
        requestCounter.add(amount, otelAttrs)
    }
}