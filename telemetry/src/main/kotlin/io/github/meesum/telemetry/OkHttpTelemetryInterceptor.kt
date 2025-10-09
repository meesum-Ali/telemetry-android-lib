package io.github.meesum.telemetry

import io.opentelemetry.api.metrics.LongCounter
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
    private val getCommonAttributes: () -> Attributes
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
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
            
            // Record response attributes
            span.setAttribute("http.status_code", response.code.toLong())
            span.setAttribute("http.response.content_length", response.body?.contentLength() ?: -1L)
            
            // Increment request counter with metadata
            val attrs = Attributes.builder()
                .putAll(getCommonAttributes())
                .put("http.method", request.method)
                .put("http.status_code", response.code.toLong())
                .put("http.host", request.url.host)
                .build()
            requestCounter.add(1, attrs.toOtelAttributes())
            
            return response
        } catch (e: Exception) {
            // Record exception
            span.recordException(e)
            span.setAttribute("error", true)
            
            // Increment request counter for failed requests
            val attrs = Attributes.builder()
                .putAll(getCommonAttributes())
                .put("http.method", request.method)
                .put("http.host", request.url.host)
                .put("error", true)
                .put("error.type", e.javaClass.simpleName)
                .build()
            requestCounter.add(1, attrs.toOtelAttributes())
            
            throw e
        } finally {
            scope.close()
            span.end()
        }
    }
}

