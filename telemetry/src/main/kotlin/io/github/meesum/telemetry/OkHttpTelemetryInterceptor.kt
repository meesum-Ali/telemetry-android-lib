package io.github.meesum.telemetry

import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.trace.Tracer
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that automatically traces HTTP requests and records metrics.
 *
 * Usage:
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(TelemetryManager.createOkHttpInterceptor())
 *     .build()
 * ```
 */
class OkHttpTelemetryInterceptor internal constructor(
    private val tracer: Tracer,
    private val requestCounter: LongCounter,
    private val httpLatencyHistogram: LongHistogram,
    private val getCommonAttributes: () -> Attributes
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // Create span for the HTTP request
        val span = tracer.spanBuilder("HTTP ${request.method} ${request.url.encodedPath}")
            .setAttribute("http.method", request.method)
            .setAttribute("http.url", request.url.toString())
            .setAttribute("http.host", request.url.host)
            .setAttribute("http.scheme", request.url.scheme)
            .startSpan()

        val scope = span.makeCurrent()

        try {
            // Proceed with the request
            val response = chain.proceed(request)
            val durationMs = System.currentTimeMillis() - startTime

            // Record response attributes
            span.setAttribute("http.status_code", response.code.toLong())
            span.setAttribute("http.response.content_length", response.body?.contentLength() ?: -1L)
            span.setAttribute("http.duration_ms", durationMs)

            // Record latency histogram
            val attrs = Attributes.builder()
                .putAll(getCommonAttributes())
                .put("http.method", request.method)
                .put("http.endpoint", request.url.encodedPath)
                .put("http.status_code", response.code)
                .build()
            httpLatencyHistogram.record(durationMs, attrs.toOtelAttributes())

            // Increment request counter with metadata
            requestCounter.add(1, attrs.toOtelAttributes())

            return response
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime

            // Record exception
            span.recordException(e)
            span.setAttribute("error", true)
            span.setAttribute("http.duration_ms", durationMs)

            // Record failed request latency
            val attrs = Attributes.builder()
                .putAll(getCommonAttributes())
                .put("http.method", request.method)
                .put("http.endpoint", request.url.encodedPath)
                .put("http.error", e.javaClass.simpleName)
                .build()
            httpLatencyHistogram.record(durationMs, attrs.toOtelAttributes())

            // Increment request counter for failed requests
            val failedAttrs = Attributes.builder()
                .putAll(getCommonAttributes())
                .put("http.method", request.method)
                .put("http.host", request.url.host)
                .put("error", true)
                .put("error.type", e.javaClass.simpleName)
                .build()
            requestCounter.add(1, failedAttrs.toOtelAttributes())

            throw e
        } finally {
            scope.close()
            span.end()
        }
    }
}

