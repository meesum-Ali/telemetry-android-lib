package io.github.meesum.telemetry.collectors

import android.app.Application
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Collects app state metrics (uptime, startup time, foreground/background)
 */
class AppStateMetricsCollector(
    private val getCommonAttributes: () -> Attributes,
    private val getAppStartTimeMs: () -> Long,
    private val getFirstActivityResumedTimeMs: () -> Long,
    private val isAppStartupComplete: () -> Boolean,
    private val getActivitiesStarted: () -> Int
) : MetricsCollector {
    
    private var uptimeGauge: ObservableLongGauge? = null
    private var startupTimeGauge: ObservableLongGauge? = null
    private var appStateGauge: ObservableLongGauge? = null
    
    override fun setup(application: Application, meter: Meter) {
        // App uptime
        uptimeGauge = meter.gaugeBuilder("app_uptime_ms")
            .ofLongs()
            .setDescription("App uptime in ms")
            .setUnit("ms")
            .buildWithCallback { measurement ->
                measurement.record(
                    System.currentTimeMillis() - getAppStartTimeMs(),
                    getCommonAttributes().toOtelAttributes()
                )
            }
        
        // Startup time
        startupTimeGauge = meter.gaugeBuilder("app.startup.time")
            .setDescription("App startup time in milliseconds")
            .setUnit("ms")
            .ofLongs()
            .buildWithCallback { measurement ->
                if (isAppStartupComplete() && getFirstActivityResumedTimeMs() > 0) {
                    measurement.record(
                        getFirstActivityResumedTimeMs() - getAppStartTimeMs(),
                        getCommonAttributes().toOtelAttributes()
                    )
                }
            }
        
        // App state (foreground/background)
        appStateGauge = meter.gaugeBuilder("app.state")
            .setDescription("App state (0=background, 1=foreground)")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(
                    if (getActivitiesStarted() > 0) 1L else 0L,
                    getCommonAttributes().toOtelAttributes()
                )
            }
    }
    
    override fun shutdown() {
        uptimeGauge?.close()
        startupTimeGauge?.close()
        appStateGauge?.close()
    }
}

