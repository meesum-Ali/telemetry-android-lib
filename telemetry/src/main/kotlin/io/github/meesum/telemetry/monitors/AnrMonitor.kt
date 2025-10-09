package io.github.meesum.telemetry.monitors

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.meesum.telemetry.Attributes
import io.github.meesum.telemetry.TelemetryService
import io.opentelemetry.api.metrics.LongCounter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitors the main thread for ANR (Application Not Responding) events
 */
class AnrMonitor(
    private val anrCounter: LongCounter,
    private val anrThresholdMs: Long,
    private val getCommonAttributes: () -> Attributes,
    private val getCurrentScreenName: () -> String,
    private val logFunction: (TelemetryService.LogLevel, String, Attributes) -> Unit
) {
    private var anrWatchdog: ScheduledExecutorService? = null
    private val lastMainThreadUpdate = AtomicLong(System.currentTimeMillis())
    
    fun start() {
        anrWatchdog = Executors.newSingleThreadScheduledExecutor()
        
        // Post to main thread periodically to update timestamp
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                lastMainThreadUpdate.set(System.currentTimeMillis())
                Handler(Looper.getMainLooper()).postDelayed(this, 500)
            }
        })

        // Check from background thread if main thread is responding
        anrWatchdog?.scheduleWithFixedDelay({
            try {
                val now = System.currentTimeMillis()
                val lastUpdate = lastMainThreadUpdate.get()
                val timeSinceUpdate = now - lastUpdate

                if (timeSinceUpdate > anrThresholdMs) {
                    // Potential ANR detected
                    val attrs = Attributes.builder()
                        .putAll(getCommonAttributes())
                        .put("anr.duration.ms", timeSinceUpdate)
                        .put("screen.name", getCurrentScreenName())
                        .put("thread.main.blocked", true)
                        .build()

                    anrCounter.add(1, attrs.toOtelAttributes())
                    
                    logFunction(
                        TelemetryService.LogLevel.ERROR,
                        "Potential ANR detected - main thread not responding",
                        attrs
                    )

                    // Reset to avoid duplicate reports
                    lastMainThreadUpdate.set(now)
                }
            } catch (e: Exception) {
                Log.e("AnrMonitor", "Error in ANR detection", e)
            }
        }, anrThresholdMs, 1000, TimeUnit.MILLISECONDS)
    }
    
    fun stop() {
        anrWatchdog?.shutdownNow()
        anrWatchdog = null
    }
}

