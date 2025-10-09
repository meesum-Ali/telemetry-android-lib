package io.github.meesum.telemetry.collectors

import android.app.Application
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Base interface for all metrics collectors.
 * Each collector is responsible for setting up and managing its own metrics.
 */
interface MetricsCollector {
    /**
     * Setup metrics collection
     */
    fun setup(application: Application, meter: Meter)
    
    /**
     * Cleanup and close all gauges
     */
    fun shutdown()
}

