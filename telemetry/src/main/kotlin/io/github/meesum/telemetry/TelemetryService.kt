package io.github.meesum.telemetry

import android.app.Application

/**
 * Opaque handle for a span, does not expose OpenTelemetry internals.
 */
interface TelemetrySpan

interface TelemetryService {

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    fun init(
        application: Application,
        serviceName: String,
        serviceVersion: String,
        environment: String,
        otlpEndpoint: String,
        headers: Map<String, String> = emptyMap(),
        traceExporterConfig: TelemetryExporterConfig? = null,
        metricExporterConfig: TelemetryExporterConfig? = null,
        logExporterConfig: TelemetryExporterConfig? = null,
        autoInstrumentActivities: Boolean = false
    )

    fun setCommonAttributes(attrs: Attributes)

    fun shutdown()

    /**
     * Start a span manually. Optionally specify a parent span.
     * You must call endSpan(span) when done.
     */
    fun startSpan(
        name: String,
        attrs: Attributes = Attributes.empty(),
        parent: TelemetrySpan? = null
    ): TelemetrySpan

    /**
     * End a span started with startSpan.
     */
    fun endSpan(span: TelemetrySpan)

    /**
     * Make a span current for the duration of the block (context propagation).
     */
    fun <T> withSpan(span: TelemetrySpan, block: () -> T): T

    /**
     * Block-based span, optionally with parent. Calls end automatically.
     */
    fun <T> span(
        name: String,
        attrs: Attributes = Attributes.empty(),
        parent: TelemetrySpan? = null,
        block: () -> T
    ): T

    fun log(
        level: LogLevel,
        message: String,
        attrs: Attributes = Attributes.empty(),
        throwable: Throwable? = null
    )

    fun incRequestCount(
        amount: Long = 1,
        attrs: Attributes = Attributes.empty()
    )

    fun incEventCount(
        name: String,
        amount: Long = 1,
        attrs: Attributes = Attributes.empty()
    )
}

// Exporter config abstractions for minimal user input

data class TelemetryExporterConfig(
    val endpoint: String,
    val headers: Map<String, String> = emptyMap(),
    val type: ExporterType = ExporterType.OTLP_GRPC
) {
    enum class ExporterType { OTLP_GRPC /*, OTLP_HTTP, JAEGER, ZIPKIN, etc. */ }
}
