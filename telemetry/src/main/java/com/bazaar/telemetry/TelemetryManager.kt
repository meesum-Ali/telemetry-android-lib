package com.bazaar.telemetry

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
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

/**
 * Enhanced OpenTelemetry wrapper for Android apps.
 * - Guards against double-init
 * - Adds global resource attributes (service.name, service.version, environment)
 * - Exposes API to add per-user/session attributes
 * - Supports graceful shutdown/flush
 */
object TelemetryManager {

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    private var initialized = false
    private lateinit var tracerProvider: SdkTracerProvider
    private lateinit var meterProvider: SdkMeterProvider
    private lateinit var loggerProvider: SdkLoggerProvider

    private lateinit var tracer: Tracer
    private lateinit var meter: Meter
    private lateinit var logger: Logger
    private lateinit var requestCounter: LongCounter

    // Attributes to apply globally to every span/log/metric
    private var commonAttributes: Attributes = Attributes.empty()

    @RequiresApi(Build.VERSION_CODES.O)
    fun init(
        application: Application,
        serviceName: String,
        serviceVersion: String,
        environment: String,
        otlpEndpoint: String,
        headers: Map<String, String> = emptyMap()
    ) {
        if (initialized) return
        initialized = true

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

        Log.i("TelemetryManager", "OpenTelemetry initialized → $otlpEndpoint")
    }

    /**
     * Set additional common attributes (e.g. user.id, session.id)
     */
    fun setCommonAttributes(attrs: Attributes) {
        commonAttributes = attrs
    }

    /**
     * Force flush and shutdown all SDK providers
     */
    fun shutdown() {
        tracerProvider.shutdown()
        meterProvider.shutdown()
        loggerProvider.shutdown()
    }

    @JvmStatic
    fun <T> span(
        name: String,
        attrs: Attributes = Attributes.empty(),
        block: () -> T
    ): T {
        // merge common + call-specific
        val merged = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val span = tracer.spanBuilder(name)
            .setAllAttributes(merged)
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

    fun log(
        level: LogLevel,
        message: String,
        attrs: Attributes = Attributes.empty()
    ) {
        val merged = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        val record: LogRecordBuilder = logger.logRecordBuilder()
            .setBody(message)
            .setAllAttributes(merged)
        when (level) {
            LogLevel.DEBUG -> record.setSeverity(io.opentelemetry.api.logs.Severity.DEBUG)
            LogLevel.INFO -> record.setSeverity(io.opentelemetry.api.logs.Severity.INFO)
            LogLevel.WARN -> record.setSeverity(io.opentelemetry.api.logs.Severity.WARN)
            LogLevel.ERROR -> record.setSeverity(io.opentelemetry.api.logs.Severity.ERROR)
        }
        record.emit()
    }

    fun incRequestCount(
        amount: Long = 1,
        attrs: Attributes = Attributes.empty()
    ) {
        val merged = Attributes.builder().putAll(commonAttributes).putAll(attrs).build()
        requestCounter.add(amount, merged)
    }
}