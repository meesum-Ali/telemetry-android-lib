package io.github.meesum.telemetry.collectors

import android.app.Application
import android.util.Log
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects garbage collection metrics
 */
class GcMetricsCollector(
    private val getCommonAttributes: () -> Attributes
) : MetricsCollector {
    
    private var gcCountGauge: ObservableLongGauge? = null
    private val lastGcCount = AtomicLong(0)
    
    override fun setup(application: Application, meter: Meter) {
        gcCountGauge = meter.gaugeBuilder("app.gc.invocations")
            .setDescription("Number of garbage collection invocations")
            .ofLongs()
            .buildWithCallback { measurement ->
                try {
                    val gcCount = estimateGcCount()
                    measurement.record(gcCount, getCommonAttributes().toOtelAttributes())
                } catch (e: Exception) {
                    Log.e("GcMetrics", "Failed to get GC count", e)
                }
            }
    }
    
    /**
     * Estimate GC count based on memory patterns (heuristic approach)
     * Note: Android doesn't expose GC count directly
     */
    private fun estimateGcCount(): Long {
        val runtime = Runtime.getRuntime()
        val currentMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        // Simple heuristic: increment counter when memory usage drops significantly
        val memoryUsagePercent = (currentMemory * 100) / maxMemory
        if (memoryUsagePercent < 50) {
            lastGcCount.incrementAndGet()
        }
        
        return lastGcCount.get()
    }
    
    override fun shutdown() {
        gcCountGauge?.close()
    }
}

