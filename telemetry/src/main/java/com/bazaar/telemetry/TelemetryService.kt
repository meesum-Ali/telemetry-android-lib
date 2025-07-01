package com.bazaar.telemetry

import android.app.Application

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

    fun <T> span(
        name: String,
        attrs: Attributes = Attributes.empty(),
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
}

// Exporter config abstractions for minimal user input

data class TelemetryExporterConfig(
    val endpoint: String,
    val headers: Map<String, String> = emptyMap(),
    val type: ExporterType = ExporterType.OTLP_GRPC
) {
    enum class ExporterType { OTLP_GRPC /*, OTLP_HTTP, JAEGER, ZIPKIN, etc. */ }
}
