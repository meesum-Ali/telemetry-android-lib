package io.github.meesum.telemetry.collectors

import android.app.Application
import android.os.Process
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects CPU-related metrics
 */
class CpuMetricsCollector(
    private val getCommonAttributes: () -> Attributes,
    private val getCurrentScreenName: () -> String
) : MetricsCollector {
    
    private var cpuTimeGauge: ObservableLongGauge? = null
    private var cpuUsagePercentGauge: ObservableLongGauge? = null
    
    private val lastCpuTime = AtomicLong(0)
    private val lastCpuMeasurementTime = AtomicLong(0)
    
    override fun setup(application: Application, meter: Meter) {
        // CPU time
        cpuTimeGauge = meter.gaugeBuilder("app_cpu_time_ms")
            .ofLongs()
            .setDescription("CPU time used by the app in ms")
            .setUnit("ms")
            .buildWithCallback { measurement ->
                val attrs = Attributes.builder()
                    .putAll(getCommonAttributes())
                    .put("screen.name", getCurrentScreenName())
                    .build()
                measurement.record(Process.getElapsedCpuTime(), attrs.toOtelAttributes())
            }
        
        // CPU usage percentage
        cpuUsagePercentGauge = meter.gaugeBuilder("app_cpu_usage_percent")
            .ofLongs()
            .setDescription("CPU usage as percentage (0-100)")
            .setUnit("1")
            .buildWithCallback { measurement ->
                val currentCpuTime = Process.getElapsedCpuTime()
                val currentTime = System.currentTimeMillis()
                
                val lastCpu = lastCpuTime.get()
                val lastTime = lastCpuMeasurementTime.get()
                
                if (lastCpu > 0 && lastTime > 0) {
                    val cpuDelta = currentCpuTime - lastCpu
                    val timeDelta = currentTime - lastTime
                    
                    if (timeDelta > 0) {
                        val cpuPercent = ((cpuDelta.toDouble() / timeDelta.toDouble()) * 100).toLong()
                        val attrs = Attributes.builder()
                            .putAll(getCommonAttributes())
                            .put("screen.name", getCurrentScreenName())
                            .put("cpu.time.delta.ms", cpuDelta)
                            .put("time.delta.ms", timeDelta)
                            .build()
                        measurement.record(cpuPercent, attrs.toOtelAttributes())
                    }
                }
                
                lastCpuTime.set(currentCpuTime)
                lastCpuMeasurementTime.set(currentTime)
            }
    }
    
    override fun shutdown() {
        cpuTimeGauge?.close()
        cpuUsagePercentGauge?.close()
    }
}

