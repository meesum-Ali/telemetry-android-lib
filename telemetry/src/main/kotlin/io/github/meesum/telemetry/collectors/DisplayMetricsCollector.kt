package io.github.meesum.telemetry.collectors

import android.app.Application
import android.content.Context
import android.os.Build
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge

/**
 * Collects display/screen-related metrics
 */
class DisplayMetricsCollector(
    private val getCommonAttributes: () -> Attributes
) : MetricsCollector {
    
    private var screenWidthGauge: ObservableLongGauge? = null
    private var screenHeightGauge: ObservableLongGauge? = null
    private var screenRefreshRateGauge: ObservableLongGauge? = null
    private var screenOrientationGauge: ObservableLongGauge? = null
    private var screenDensityGauge: ObservableLongGauge? = null
    
    @Suppress("DEPRECATION")
    override fun setup(application: Application, meter: Meter) {
        val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        val display = windowManager?.defaultDisplay
        
        // Screen width
        screenWidthGauge = meter.gaugeBuilder("device.screen.width")
            .setDescription("Screen width in pixels")
            .setUnit("px")
            .ofLongs()
            .buildWithCallback { measurement ->
                val metrics = application.resources.displayMetrics
                measurement.record(metrics.widthPixels.toLong(), getCommonAttributes().toOtelAttributes())
            }
        
        // Screen height
        screenHeightGauge = meter.gaugeBuilder("device.screen.height")
            .setDescription("Screen height in pixels")
            .setUnit("px")
            .ofLongs()
            .buildWithCallback { measurement ->
                val metrics = application.resources.displayMetrics
                measurement.record(metrics.heightPixels.toLong(), getCommonAttributes().toOtelAttributes())
            }
        
        // Refresh rate
        screenRefreshRateGauge = meter.gaugeBuilder("device.screen.refresh_rate")
            .setDescription("Screen refresh rate in Hz")
            .setUnit("Hz")
            .ofLongs()
            .buildWithCallback { measurement ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val mode = display?.mode
                    val refreshRate = mode?.refreshRate?.toLong() ?: 60L
                    measurement.record(refreshRate, getCommonAttributes().toOtelAttributes())
                } else {
                    val refreshRate = display?.refreshRate?.toLong() ?: 60L
                    measurement.record(refreshRate, getCommonAttributes().toOtelAttributes())
                }
            }
        
        // Screen orientation
        screenOrientationGauge = meter.gaugeBuilder("device.screen.orientation")
            .setDescription("Screen orientation (0=portrait, 1=landscape)")
            .ofLongs()
            .buildWithCallback { measurement ->
                val orientation = application.resources.configuration.orientation
                val normalizedOrientation = when (orientation) {
                    android.content.res.Configuration.ORIENTATION_PORTRAIT -> 0L
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE -> 1L
                    else -> -1L
                }
                measurement.record(normalizedOrientation, getCommonAttributes().toOtelAttributes())
            }
        
        // Screen density
        screenDensityGauge = meter.gaugeBuilder("device.screen.density")
            .setDescription("Screen density in DPI")
            .ofLongs()
            .buildWithCallback { measurement ->
                val metrics = application.resources.displayMetrics
                measurement.record(metrics.densityDpi.toLong(), getCommonAttributes().toOtelAttributes())
            }
    }
    
    override fun shutdown() {
        screenWidthGauge?.close()
        screenHeightGauge?.close()
        screenRefreshRateGauge?.close()
        screenOrientationGauge?.close()
        screenDensityGauge?.close()
    }
}

