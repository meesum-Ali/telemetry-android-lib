package io.github.meesum.telemetry.collectors

import android.app.Application
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Collects memory-related metrics
 */
class MemoryMetricsCollector(
    private val getCommonAttributes: () -> Attributes,
    private val getCurrentScreenName: () -> String
) : MetricsCollector {
    
    private var memoryUsageGauge: ObservableLongGauge? = null
    private var memoryMaxGauge: ObservableLongGauge? = null
    private var memoryHeapSizeGauge: ObservableLongGauge? = null
    private var memoryPressureGauge: ObservableLongGauge? = null
    
    override fun setup(application: Application, meter: Meter) {
        // Memory usage
        memoryUsageGauge = meter.gaugeBuilder("app_memory_usage_bytes")
            .ofLongs()
            .setDescription("Memory used by the app in bytes")
            .setUnit("By")
            .buildWithCallback { measurement ->
                val runtime = Runtime.getRuntime()
                val used = runtime.totalMemory() - runtime.freeMemory()
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .build()
                measurement.record(used, attrs.toOtelAttributes())
            }
        
        // Max memory
        memoryMaxGauge = meter.gaugeBuilder("app_memory_max_bytes")
            .ofLongs()
            .setDescription("Maximum memory available to the app in bytes")
            .setUnit("By")
            .buildWithCallback { measurement ->
                val runtime = Runtime.getRuntime()
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .build()
                measurement.record(runtime.maxMemory(), attrs.toOtelAttributes())
            }
        
        // Heap size
        memoryHeapSizeGauge = meter.gaugeBuilder("app_memory_heap_size_bytes")
            .ofLongs()
            .setDescription("Current heap size allocated by the app in bytes")
            .setUnit("By")
            .buildWithCallback { measurement ->
                val runtime = Runtime.getRuntime()
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .build()
                measurement.record(runtime.totalMemory(), attrs.toOtelAttributes())
            }
        
        // Memory pressure
        memoryPressureGauge = meter.gaugeBuilder("app_memory_pressure_percent")
            .ofLongs()
            .setDescription("Memory pressure as percentage of max memory (0-100)")
            .setUnit("1")
            .buildWithCallback { measurement ->
                val runtime = Runtime.getRuntime()
                val used = runtime.totalMemory() - runtime.freeMemory()
                val max = runtime.maxMemory()
                val pressure = ((used.toDouble() / max.toDouble()) * 100).toLong()
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .put("memory.used.bytes", used)
                    .put("memory.max.bytes", max)
                    .build()
                measurement.record(pressure, attrs.toOtelAttributes())
            }
    }
    
    override fun shutdown() {
        memoryUsageGauge?.close()
        memoryMaxGauge?.close()
        memoryHeapSizeGauge?.close()
        memoryPressureGauge?.close()
    }
}

