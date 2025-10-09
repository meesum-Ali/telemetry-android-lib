package io.github.meesum.telemetry.monitors

import android.util.Log
import android.view.Choreographer
import io.github.meesum.telemetry.Attributes
import io.github.meesum.telemetry.TelemetryService
import io.opentelemetry.api.metrics.LongCounter

/**
 * Monitors for slow operations on the main thread
 */
class SlowOperationMonitor(
    private val slowOperationCounter: LongCounter,
    private val slowOperationThresholdMs: Long,
    private val getCommonAttributes: () -> Attributes,
    private val getCurrentScreenName: () -> String,
    private val logFunction: (TelemetryService.LogLevel, String, Attributes) -> Unit
) {
    private var slowOpCallback: Choreographer.FrameCallback? = null
    
    fun start() {
        slowOpCallback = object : Choreographer.FrameCallback {
            private var lastTime: Long = 0

            override fun doFrame(frameTimeNanos: Long) {
                if (lastTime > 0) {
                    val duration = (frameTimeNanos - lastTime) / 1_000_000 // Convert to ms
                    if (duration > slowOperationThresholdMs) {
                        // Slow operation detected
                        val attrs = Attributes.builder()
                            .putAll(getCommonAttributes())
                            .put("operation.duration.ms", duration)
                            .put("screen.name", getCurrentScreenName())
                            .put("threshold.ms", slowOperationThresholdMs)
                            .build()

                        slowOperationCounter.add(1, attrs.toOtelAttributes())
                        
                        logFunction(
                            TelemetryService.LogLevel.WARN,
                            "Slow operation detected on main thread: ${duration}ms",
                            attrs
                        )
                    }
                }
                lastTime = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        Choreographer.getInstance().postFrameCallback(slowOpCallback!!)
    }
    
    fun stop() {
        slowOpCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        slowOpCallback = null
    }
}

