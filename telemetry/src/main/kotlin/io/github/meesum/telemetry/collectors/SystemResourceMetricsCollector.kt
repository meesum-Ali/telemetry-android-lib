package io.github.meesum.telemetry.collectors

import android.app.Application
import android.content.Context
import android.os.Build
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Collects system resource metrics (RAM, CPU cores, thermal state, threads)
 */
class SystemResourceMetricsCollector(
    private val getCommonAttributes: () -> Attributes
) : MetricsCollector {
    
    private var totalRamGauge: ObservableLongGauge? = null
    private var availableRamGauge: ObservableLongGauge? = null
    private var cpuCoresGauge: ObservableLongGauge? = null
    private var threadCountGauge: ObservableLongGauge? = null
    private var deviceThermalGauge: ObservableLongGauge? = null
    
    override fun setup(application: Application, meter: Meter) {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        
        // Total device RAM
        totalRamGauge = meter.gaugeBuilder("device.ram.total")
            .setDescription("Total device RAM in bytes")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                activityManager?.let { am ->
                    val memoryInfo = android.app.ActivityManager.MemoryInfo()
                    am.getMemoryInfo(memoryInfo)
                    measurement.record(memoryInfo.totalMem, getCommonAttributes().toOtelAttributes())
                }
            }
        
        // Available device RAM
        availableRamGauge = meter.gaugeBuilder("device.ram.available")
            .setDescription("Available device RAM in bytes")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                activityManager?.let { am ->
                    val memoryInfo = android.app.ActivityManager.MemoryInfo()
                    am.getMemoryInfo(memoryInfo)
                    measurement.record(memoryInfo.availMem, getCommonAttributes().toOtelAttributes())
                }
            }
        
        // CPU cores
        cpuCoresGauge = meter.gaugeBuilder("device.cpu.cores")
            .setDescription("Number of CPU cores")
            .ofLongs()
            .buildWithCallback { measurement ->
                val cores = Runtime.getRuntime().availableProcessors()
                measurement.record(cores.toLong(), getCommonAttributes().toOtelAttributes())
            }
        
        // Thread count
        threadCountGauge = meter.gaugeBuilder("app_thread_count")
            .ofLongs()
            .setDescription("Number of active threads in the app")
            .setUnit("1")
            .buildWithCallback { measurement ->
                measurement.record(
                    Thread.activeCount().toLong(),
                    getCommonAttributes().toOtelAttributes()
                )
            }
        
        // Device thermal state (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = application.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            deviceThermalGauge = meter.gaugeBuilder("device.thermal.state")
                .setDescription("Device thermal state (0=none, 1=light, 2=moderate, 3=severe, 4=critical)")
                .ofLongs()
                .buildWithCallback { measurement ->
                    powerManager?.let { pm ->
                        @Suppress("NewApi")
                        val thermalStatus = pm.currentThermalStatus
                        measurement.record(thermalStatus.toLong(), getCommonAttributes().toOtelAttributes())
                    }
                }
        }
    }
    
    override fun shutdown() {
        totalRamGauge?.close()
        availableRamGauge?.close()
        cpuCoresGauge?.close()
        threadCountGauge?.close()
        deviceThermalGauge?.close()
    }
}

