package io.github.meesum.telemetry.collectors

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Collects battery-related metrics
 */
class BatteryMetricsCollector(
    private val getCommonAttributes: () -> Attributes
) : MetricsCollector {
    
    private var batteryLevelGauge: ObservableLongGauge? = null
    private var batteryTempGauge: ObservableLongGauge? = null
    private var batteryVoltageGauge: ObservableLongGauge? = null
    private var batteryHealthGauge: ObservableLongGauge? = null
    private var batteryChargingGauge: ObservableLongGauge? = null
    
    override fun setup(application: Application, meter: Meter) {
        // Battery level
        batteryLevelGauge = meter.gaugeBuilder("device_battery_percent")
            .ofLongs()
            .setDescription("Device battery level percentage")
            .setUnit("1")
            .buildWithCallback { measurement ->
                val bm = application.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                if (bm != null) {
                    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (level >= 0) {
                        measurement.record(level.toLong(), getCommonAttributes().toOtelAttributes())
                    }
                }
            }
        
        // Battery temperature
        batteryTempGauge = meter.gaugeBuilder("device.battery.temperature")
            .setDescription("Battery temperature in degrees Celsius * 10")
            .ofLongs()
            .buildWithCallback { measurement ->
                val batteryStatus = application.registerReceiver(null,
                    IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                batteryStatus?.let {
                    val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                    if (temp > 0) {
                        measurement.record(temp.toLong(), getCommonAttributes().toOtelAttributes())
                    }
                }
            }
        
        // Battery voltage
        batteryVoltageGauge = meter.gaugeBuilder("device.battery.voltage")
            .setDescription("Battery voltage in millivolts")
            .setUnit("mV")
            .ofLongs()
            .buildWithCallback { measurement ->
                val batteryStatus = application.registerReceiver(null,
                    IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                batteryStatus?.let {
                    val voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    if (voltage > 0) {
                        measurement.record(voltage.toLong(), getCommonAttributes().toOtelAttributes())
                    }
                }
            }
        
        // Battery health
        batteryHealthGauge = meter.gaugeBuilder("device.battery.health")
            .setDescription("Battery health status (2=good, 3=overheat, 4=dead, 5=overvoltage, 6=failure, 7=cold)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val batteryStatus = application.registerReceiver(null,
                    IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                batteryStatus?.let {
                    val health = it.getIntExtra(BatteryManager.EXTRA_HEALTH,
                        BatteryManager.BATTERY_HEALTH_UNKNOWN)
                    measurement.record(health.toLong(), getCommonAttributes().toOtelAttributes())
                }
            }
        
        // Charging status
        batteryChargingGauge = meter.gaugeBuilder("device.battery.charging_status")
            .setDescription("Charging status (1=charging, 2=discharging, 4=full)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val batteryStatus = application.registerReceiver(null,
                    IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                batteryStatus?.let {
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN)
                    measurement.record(status.toLong(), getCommonAttributes().toOtelAttributes())
                }
            }
    }
    
    override fun shutdown() {
        batteryLevelGauge?.close()
        batteryTempGauge?.close()
        batteryVoltageGauge?.close()
        batteryHealthGauge?.close()
        batteryChargingGauge?.close()
    }
}

