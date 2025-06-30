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
        headers: Map<String, String> = emptyMap()
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
        attrs: Attributes = Attributes.empty()
    )

    fun incRequestCount(
        amount: Long = 1,
        attrs: Attributes = Attributes.empty()
    )
}
