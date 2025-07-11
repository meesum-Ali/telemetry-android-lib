package io.github.meesum.telemetry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Extension to trace a Flow with a Telemetry span.
 * Starts a span when collection begins, ends it on completion or error.
 * Optionally, you can add more fine-grained tracing inside the flow if needed.
 */
fun <T> Flow<T>.trace(
    name: String,
    attrs: Attributes = Attributes.empty(),
    parent: TelemetrySpan? = null
): Flow<T> = flow {
    val span = TelemetryManager.startSpan(name, attrs, parent)
    try {
        emitAll(
            this@trace
                .onStart { TelemetryManager.withSpan(span) { } }
                .onCompletion { TelemetryManager.endSpan(span) }
                .catch { e ->
                    // Optionally record exception
                    TelemetryManager.endSpan(span)
                    throw e
                }
        )
    } finally {
        // Defensive: ensure span ends if flow is cancelled
        TelemetryManager.endSpan(span)
    }
} 