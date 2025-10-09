package io.github.meesum.telemetry.monitors

import android.util.Log
import android.view.Choreographer
import io.github.meesum.telemetry.Attributes
import io.opentelemetry.api.metrics.LongCounter
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitors frame rendering and detects jank events with severity levels
 */
class JankMonitor(
    private val jankCounter: LongCounter,
    private val getCommonAttributes: () -> Attributes,
    private val getCurrentScreenName: () -> String
) {
    private val lastFrameTimeNanos = AtomicLong(0)
    private val jankCountMild = AtomicLong(0)
    private val jankCountModerate = AtomicLong(0)
    private val jankCountSevere = AtomicLong(0)
    
    private var frameTimeCallback: Choreographer.FrameCallback? = null
    
    fun start() {
        frameTimeCallback = object : Choreographer.FrameCallback {
            private var lastFrameTime: Long = 0

            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTime > 0) {
                    val frameTime = frameTimeNanos - lastFrameTime
                    lastFrameTimeNanos.set(frameTime)

                    val frameTimeMs = frameTime / 1_000_000
                    
                    // Detect jank with severity levels
                    val jankSeverity = when {
                        frameTime > 16_666_667 && frameTime <= 33_333_333 -> {
                            jankCountMild.incrementAndGet()
                            "mild"
                        }
                        frameTime > 33_333_333 && frameTime <= 50_000_000 -> {
                            jankCountModerate.incrementAndGet()
                            "moderate"
                        }
                        frameTime > 50_000_000 -> {
                            jankCountSevere.incrementAndGet()
                            "severe"
                        }
                        else -> null
                    }

                    if (jankSeverity != null) {
                        val droppedFrames = (frameTimeMs / 16.67).toLong()
                        val fps = if (frameTimeMs > 0) (1000.0 / frameTimeMs).toLong() else 0
                        
                        val attrs = Attributes.builder()
                            .putAll(getCommonAttributes())
                            .put("screen.name", getCurrentScreenName())
                            .put("frame.time.ns", frameTime)
                            .put("frame.time.ms", frameTimeMs)
                            .put("jank.severity", jankSeverity)
                            .put("frames.dropped", droppedFrames)
                            .put("fps", fps)
                            .build()
                        jankCounter.add(1, attrs.toOtelAttributes())
                        
                        // Log severe jank only
                        if (jankSeverity == "severe") {
                            Log.w(
                                "JankMonitor",
                                "Severe jank detected on ${getCurrentScreenName()}: ${frameTimeMs}ms (dropped $droppedFrames frames, ${fps}fps)"
                            )
                        }
                    }
                }
                lastFrameTime = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        Choreographer.getInstance().postFrameCallback(frameTimeCallback!!)
    }
    
    fun stop() {
        frameTimeCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameTimeCallback = null
    }
    
    fun getLastFrameTimeNanos(): Long = lastFrameTimeNanos.get()
    fun getJankCountMild(): Long = jankCountMild.get()
    fun getJankCountModerate(): Long = jankCountModerate.get()
    fun getJankCountSevere(): Long = jankCountSevere.get()
}

