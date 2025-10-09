package io.github.meesum.telemetry.collectors

import android.app.Application
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects performance metrics (frame time, jank counts)
 * Note: Frame callback and jank detection are handled by JankMonitor
 */
class PerformanceMetricsCollector(
    private val getCommonAttributes: () -> Attributes,
    private val getCurrentScreenName: () -> String,
    private val getLastFrameTimeNanos: () -> Long,
    private val getJankCountMild: () -> Long,
    private val getJankCountModerate: () -> Long,
    private val getJankCountSevere: () -> Long
) : MetricsCollector {
    
    private var frameTimeGauge: ObservableLongGauge? = null
    private var jankCountMildGauge: ObservableLongGauge? = null
    private var jankCountModerateGauge: ObservableLongGauge? = null
    private var jankCountSevereGauge: ObservableLongGauge? = null
    
    override fun setup(application: Application, meter: Meter) {
        // Frame time gauge
        frameTimeGauge = meter.gaugeBuilder("ui.frame.time")
            .setDescription("Frame render time in nanoseconds")
            .setUnit("ns")
            .ofLongs()
            .buildWithCallback { measurement ->
                val frameTime = getLastFrameTimeNanos()
                if (frameTime > 0) {
                    val attrs = Attributes.builder()
                        .putAll(getCommonAttributes())
                        .put("screen.name", getCurrentScreenName())
                        .build()
                    measurement.record(frameTime, attrs.toOtelAttributes())
                }
            }
        
        // Jank count gauges by severity
        jankCountMildGauge = meter.gaugeBuilder("ui.jank.count.mild")
            .setDescription("Total count of mild jank events (16-33ms, dropped 1 frame)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .put("jank.severity", "mild")
                    .build()
                measurement.record(getJankCountMild(), attrs.toOtelAttributes())
            }
        
        jankCountModerateGauge = meter.gaugeBuilder("ui.jank.count.moderate")
            .setDescription("Total count of moderate jank events (33-50ms, dropped 2-3 frames)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .put("jank.severity", "moderate")
                    .build()
                measurement.record(getJankCountModerate(), attrs.toOtelAttributes())
            }
        
        jankCountSevereGauge = meter.gaugeBuilder("ui.jank.count.severe")
            .setDescription("Total count of severe jank events (>50ms, dropped 3+ frames)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .put("jank.severity", "severe")
                    .build()
                measurement.record(getJankCountSevere(), attrs.toOtelAttributes())
            }
    }
    
    override fun shutdown() {
        frameTimeGauge?.close()
        jankCountMildGauge?.close()
        jankCountModerateGauge?.close()
        jankCountSevereGauge?.close()
    }
}

