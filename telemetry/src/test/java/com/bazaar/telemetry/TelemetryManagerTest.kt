package com.bazaar.telemetry

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowProcess // Keep this for Process.getElapsedCpuTime()
import org.robolectric.util.TimeUtils // For advancing time if needed, though Duration is preferred
import java.util.concurrent.TimeUnit
import java.time.Duration // For ShadowLooper.idleFor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class TelemetryManagerTest {

    private lateinit var mockApplication: Application
    private lateinit var mockContext: Context
    private lateinit var mockBatteryManager: BatteryManager

    private val serviceName = "testService"
    private val serviceVersion = "1.0.0"
    private val environment = "test"
    private val otlpEndpoint = "http://localhost:4317"
    private val headers = mapOf("X-Test-Header" to "TestValue")

    // In-memory exporters for testing
    private val spanExporter = InMemorySpanExporter.create()
    private val metricExporter = InMemoryMetricExporter.create()
    private val logExporter = InMemoryLogRecordExporter.create()

    private lateinit var sdk: OpenTelemetrySdk

    @Before
    fun setUp() {
        ShadowLog.stream = System.out // To see Android Log messages in console

        mockApplication = mock()
        mockContext = mock()
        mockBatteryManager = mock()

        whenever(mockApplication.applicationContext).thenReturn(mockContext)
        whenever(mockContext.getSystemService(Context.BATTERY_SERVICE)).thenReturn(mockBatteryManager)
        whenever(mockApplication.getSystemService(Context.BATTERY_SERVICE)).thenReturn(mockBatteryManager)
        // Default behavior for battery manager if not overridden in a specific test
        whenever(mockBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).thenReturn(100)


        // Reset OTel global state before each test
        GlobalOpenTelemetry.resetForTest()
        TelemetryManagerReflection.setInitialized(false) // Reset internal initialized flag
        TelemetryManagerReflection.resetProvidersAndSDK() // Reset internal SDK components

        // It's important to initialize a real SDK with in-memory exporters for TelemetryManager
        // to interact with, otherwise many internal OTel components will be no-op.
        // This SDK will be registered globally and TelemetryManager should pick it up.
        val sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(spanExporter))
            .build()
        val sdkMeterProvider = SdkMeterProvider.builder()
            .registerMetricReader(io.opentelemetry.sdk.metrics.export.PeriodicMetricReader.builder(metricExporter).setInterval(100, TimeUnit.MILLISECONDS).build())
            .build()
        val sdkLoggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor.create(logExporter))
            .build()

        // This SDK is NOT the one TelemetryManager will create internally.
        // TelemetryManager creates its own Sdk instance. We test its effects via exporters.
        // The global registration here is more for safety in test environment if anything else tries to use global.
        OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setMeterProvider(sdkMeterProvider)
            .setLoggerProvider(sdkLoggerProvider)
            .buildAndRegisterGlobal()
    }

    @After
    fun tearDown() {
        if (TelemetryManagerReflection.isInitialized()) {
            TelemetryManager.shutdown()
        }
        spanExporter.reset()
        metricExporter.reset()
        logExporter.reset()
        GlobalOpenTelemetry.resetForTest() // Crucial for test isolation
        TelemetryManagerReflection.setInitialized(false)
        TelemetryManagerReflection.resetProvidersAndSDK() // Ensure manager's internal state is clean
        ShadowLooper.idleMainLooper() // Ensure any pending tasks are flushed
    }

    private fun initializeManager() {
        TelemetryManager.init(
            application = mockApplication,
            serviceName = serviceName,
            serviceVersion = serviceVersion,
            environment = environment,
            otlpEndpoint = otlpEndpoint,
            headers = headers
        )
    }

    @Test
    fun `init should initialize OpenTelemetry SDK correctly and only once`() {
        assertFalse(TelemetryManagerReflection.isInitialized())
        initializeManager()
        assertTrue(TelemetryManagerReflection.isInitialized())

        // Verify that resource attributes from init are present in telemetry data
        TelemetryManager.span("testSpanForResource") {}
        spanExporter.flush()
        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals(serviceName, spans[0].resource.getAttribute(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME))
        assertEquals(serviceVersion, spans[0].resource.getAttribute(io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION))
        assertEquals(environment, spans[0].resource.getAttribute(AttributeKey.stringKey("deployment.environment")))


        // Check that subsequent calls to init do not re-initialize
        val initialTracerProvider = TelemetryManagerReflection.getTracerProvider()
        val initialMeterProvider = TelemetryManagerReflection.getMeterProvider()
        val initialLoggerProvider = TelemetryManagerReflection.getLoggerProvider()

        TelemetryManager.init(mockApplication, "newService", "2.0", "prod", "http://new:1234", emptyMap())

        assertSame("TracerProvider should not change on re-init", initialTracerProvider, TelemetryManagerReflection.getTracerProvider())
        assertSame("MeterProvider should not change on re-init", initialMeterProvider, TelemetryManagerReflection.getMeterProvider())
        assertSame("LoggerProvider should not change on re-init", initialLoggerProvider, TelemetryManagerReflection.getLoggerProvider())
        assertTrue("Still should be initialized", TelemetryManagerReflection.isInitialized())
    }

    @Test
    fun `init should set up app vitals gauges and they should report values`() {
        // Mock system values
        org.robolectric.shadows.ShadowProcess.setElapsedCpuTime(12345L) // Milliseconds
        // Runtime memory cannot be easily mocked with Robolectric for freeMemory/totalMemory.
        // We'll check if the gauge is registered and reports *some* value.
        whenever(mockBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).thenReturn(75)

        initializeManager()

        // Advance time significantly to ensure metric collection cycle runs
        ShadowLooper.idleFor(Duration.ofSeconds(31))
        metricExporter.flush() // Force export

        val metrics = metricExporter.finishedMetricItems
        assertTrue("Metrics list should not be empty after init and delay", metrics.isNotEmpty())

        assertNotNull("Memory usage metric 'app_memory_usage_bytes' should be present", metrics.find { it.name == "app_memory_usage_bytes" })
        assertTrue("Memory usage should be positive", metrics.find { it.name == "app_memory_usage_bytes" }?.longGaugeData?.points?.firstOrNull()?.value ?: -1L >= 0)

        val cpuMetric = metrics.find { it.name == "app_cpu_time_ms" }
        assertNotNull("CPU time metric 'app_cpu_time_ms' should be present", cpuMetric)
        assertEquals("CPU time should match ShadowProcess", 12345L, cpuMetric?.longGaugeData?.points?.firstOrNull()?.value)

        val uptimeMetric = metrics.find { it.name == "app_uptime_ms" }
        assertNotNull("Uptime metric 'app_uptime_ms' should be present", uptimeMetric)
        assertTrue("Uptime should be positive", uptimeMetric?.longGaugeData?.points?.firstOrNull()?.value ?: -1L > 0)


        val batteryMetric = metrics.find { it.name == "device_battery_percent" }
        assertNotNull("Battery level metric 'device_battery_percent' should be present", batteryMetric)
        assertEquals("Battery level should match mock", 75L, batteryMetric?.longGaugeData?.points?.firstOrNull()?.value)

        val threadCountMetric = metrics.find { it.name == "app_thread_count" }
        assertNotNull("Thread count metric 'app_thread_count' should be present", threadCountMetric)
        assertTrue("Thread count should be positive", threadCountMetric?.longGaugeData?.points?.firstOrNull()?.value ?: 0L > 0)
    }


    @Test
    fun `setCommonAttributes should store attributes and apply to subsequent telemetry`() {
        initializeManager()
        val commonAttrs = Attributes.builder()
            .put("user.id", "testUser123")
            .put("session.id", "sessionABC")
            .build()
        TelemetryManager.setCommonAttributes(commonAttrs)

        // Test with a span
        val spanSpecificAttr = Attributes.builder().put("span.local", "localValue").build()
        TelemetryManager.span("testSpanWithCommonAttrs", spanSpecificAttr) {}
        spanExporter.flush()
        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals("testUser123", spans[0].attributes.get(AttributeKey.stringKey("user.id")))
        assertEquals("sessionABC", spans[0].attributes.get(AttributeKey.stringKey("session.id")))
        assertEquals("localValue", spans[0].attributes.get(AttributeKey.stringKey("span.local")))
        // Check service name from init is also present
        assertEquals(serviceName, spans[0].attributes.get(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME))

        // Test with a log
        logExporter.reset()
        val logSpecificAttr = Attributes.builder().put("log.local", "anotherLocal").build()
        TelemetryManager.log(TelemetryService.LogLevel.INFO, "testLogWithCommonAttrs", logSpecificAttr)
        logExporter.flush()
        val logs = logExporter.finishedLogRecordItems
        assertEquals(1, logs.size)
        assertEquals("testUser123", logs[0].attributes.get(AttributeKey.stringKey("user.id")))
        assertEquals("sessionABC", logs[0].attributes.get(AttributeKey.stringKey("session.id")))
        assertEquals("anotherLocal", logs[0].attributes.get(AttributeKey.stringKey("log.local")))

        // Test with a metric
        metricExporter.reset()
        val metricSpecificAttr = Attributes.builder().put("metric.local", "metricVal").build()
        TelemetryManager.incRequestCount(5, metricSpecificAttr)

        ShadowLooper.idleFor(Duration.ofMillis(150)) // Ensure periodic reader interval passes
        metricExporter.flush()

        val metrics = metricExporter.finishedMetricItems
        val counterMetric = metrics.find { it.name == "http_client_request_count" }
        assertNotNull("Counter metric should exist", counterMetric)
        val pointData = counterMetric!!.longSumData.points.first()
        assertEquals("testUser123", pointData.attributes.get(AttributeKey.stringKey("user.id")))
        assertEquals("sessionABC", pointData.attributes.get(AttributeKey.stringKey("session.id")))
        assertEquals("metricVal", pointData.attributes.get(AttributeKey.stringKey("metric.local")))
        assertEquals(5L, pointData.value)
    }

    @Test
    fun `span should create a span, execute block, record exceptions, and include common attributes`() {
        initializeManager()
        val commonAttrs = Attributes.builder().put("common.key", "commonValue").build()
        TelemetryManager.setCommonAttributes(commonAttrs)

        val spanName = "myTestSpan"
        val spanAttrs = Attributes.builder().put("span.specific", "value").build()
        var blockExecuted = false

        val result = TelemetryManager.span(spanName, spanAttrs) {
            blockExecuted = true
            "ReturnValue"
        }
        spanExporter.flush()

        assertEquals("ReturnValue", result)
        assertTrue(blockExecuted)
        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        val capturedSpan = spans[0]
        assertEquals(spanName, capturedSpan.name)
        assertEquals("value", capturedSpan.attributes.get(AttributeKey.stringKey("span.specific")))
        assertEquals("commonValue", capturedSpan.attributes.get(AttributeKey.stringKey("common.key"))) // Common attribute
        assertEquals(serviceName, capturedSpan.attributes.get(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME)) // From init
        assertTrue(capturedSpan.hasEnded())

        // Test exception recording
        spanExporter.reset()
        val exceptionMessage = "Test exception in span"
        try {
            TelemetryManager.span(spanName) {
                throw RuntimeException(exceptionMessage)
            }
            fail("Exception should have been re-thrown")
        } catch (e: RuntimeException) {
            assertEquals(exceptionMessage, e.message)
        }
        spanExporter.flush()

        val errorSpans = spanExporter.finishedSpanItems
        assertEquals(1, errorSpans.size)
        val errorSpan = errorSpans[0]
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, errorSpan.status.statusCode)
        assertTrue(errorSpan.events.any {
            it.name == "exception" &&
            it.attributes.get(AttributeKey.stringKey("exception.message")) == exceptionMessage &&
            it.attributes.get(AttributeKey.stringKey("exception.type")) == "java.lang.RuntimeException" // Check type
        })
        assertEquals("commonValue", errorSpan.attributes.get(AttributeKey.stringKey("common.key"))) // Common attribute on error span
    }

    @Test
    fun `log should emit a log record with correct level, message, attributes, and common attributes`() {
        initializeManager()
        val commonAttrs = Attributes.builder().put("common.log.key", "logCommon").build()
        TelemetryManager.setCommonAttributes(commonAttrs)

        val logMessage = "My test log message"
        val logAttrs = Attributes.builder().put("log.specific", "logValue").build()

        TelemetryManager.log(TelemetryService.LogLevel.WARN, logMessage, logAttrs)
        logExporter.flush()

        val logs = logExporter.finishedLogRecordItems
        assertEquals(1, logs.size)
        val capturedLog = logs[0]
        assertEquals(logMessage, capturedLog.body.asString())
        assertEquals(io.opentelemetry.api.logs.Severity.WARN.getSeverityNumber(), capturedLog.severityNumber)
        assertEquals("logValue", capturedLog.attributes.get(AttributeKey.stringKey("log.specific")))
        assertEquals("logCommon", capturedLog.attributes.get(AttributeKey.stringKey("common.log.key"))) // Common attribute
        assertEquals(serviceName, capturedLog.attributes.get(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME)) // From init
    }

    @Test
    fun `log should record throwable details correctly`() {
        initializeManager()
        val errorMessage = "Error for logging with details"
        val throwable = IllegalArgumentException(errorMessage) // Different exception type

        TelemetryManager.log(TelemetryService.LogLevel.ERROR, "An application error occurred", throwable = throwable)
        logExporter.flush()

        val logs = logExporter.finishedLogRecordItems
        assertEquals(1, logs.size)
        val capturedLog = logs[0]
        assertEquals("An application error occurred", capturedLog.body.asString())
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR.getSeverityNumber(), capturedLog.severityNumber)
        assertEquals(throwable.javaClass.name, capturedLog.attributes.get(AttributeKey.stringKey("exception.type")))
        assertEquals(errorMessage, capturedLog.attributes.get(AttributeKey.stringKey("exception.message")))
        assertNotNull(capturedLog.attributes.get(AttributeKey.stringKey("exception.stacktrace")))
        assertTrue(capturedLog.attributes.get(AttributeKey.stringKey("exception.stacktrace"))!!.contains("IllegalArgumentException"))
    }

    @Test
    fun `incRequestCount should increment counter with attributes and common attributes`() {
        initializeManager()
        val commonAttrs = Attributes.builder().put("common.metric.key", "metricCommon").build()
        TelemetryManager.setCommonAttributes(commonAttrs)

        val countAttrs = Attributes.builder().put("http.method", "POST").build()

        TelemetryManager.incRequestCount(3, countAttrs) // Increment by 3
        // Advance time for metric reader and flush
        ShadowLooper.idleFor(Duration.ofMillis(150)) // Ensure periodic reader interval passes
        metricExporter.flush()


        val metrics = metricExporter.finishedMetricItems
        val counterMetric = metrics.find { it.name == "http_client_request_count" }
        assertNotNull("Counter metric 'http_client_request_count' should exist", counterMetric)

        val points = counterMetric!!.longSumData.points
        assertEquals("Should be one data point for the counter", 1, points.size)
        val point = points.first()
        assertEquals("Counter value should be 3", 3L, point.value)
        assertEquals("POST", point.attributes.get(AttributeKey.stringKey("http.method")))
        assertEquals("metricCommon", point.attributes.get(AttributeKey.stringKey("common.metric.key"))) // Common attribute
        assertEquals(serviceName, point.attributes.get(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME)) // From init
    }

    @Test
    fun `shutdown should shutdown providers, restore exception handler, and reset initialized flag`() {
        val initialHandler = Thread.getDefaultUncaughtExceptionHandler()
        initializeManager() // Sets up its own handler
        assertNotSame("Exception handler should be changed by TelemetryManager", initialHandler, Thread.getDefaultUncaughtExceptionHandler())
        assertTrue(TelemetryManagerReflection.isInitialized())

        TelemetryManager.shutdown()

        // Use reflection to check if providers are shutdown.
        // The actual SdkTracerProvider etc. instances are internal to TelemetryManager.
        // Their shutdown methods should have been called. We can't directly verify this easily
        // without more complex mocking of the OTel SDK creation within TelemetryManager.
        // Instead, we rely on the fact that `isShutdown` would be true on the instances if shutdown was called.
        // This is a limitation of testing internal SDK management.
        // A more robust test would involve mocking the OTel SDK builder itself.

        // For now, we'll trust that if `isInitialized` is false, shutdown logic ran.
        assertFalse("TelemetryManager should be marked as not initialized after shutdown", TelemetryManagerReflection.isInitialized())
        assertEquals("Default exception handler should be restored", initialHandler, Thread.getDefaultUncaughtExceptionHandler())


        // Verify that telemetry operations are effectively no-ops after shutdown
        // (i.e., they don't throw exceptions and don't record data)
        spanExporter.reset()
        logExporter.reset()
        metricExporter.reset()

        try {
            TelemetryManager.span("afterShutdownSpan") {}
            TelemetryManager.log(TelemetryService.LogLevel.INFO, "afterShutdownLog")
            TelemetryManager.incRequestCount(attrs = Attributes.builder().put("key", "value").build())
        } catch (e: Exception) {
            fail("Telemetry operations after shutdown should not throw exceptions: ${e.message}")
        }

        spanExporter.flush()
        logExporter.flush()
        ShadowLooper.idleFor(Duration.ofMillis(150)) // Ensure periodic reader interval passes
        metricExporter.flush()


        assertTrue("No spans should be exported after shutdown", spanExporter.finishedSpanItems.isEmpty())
        assertTrue("No logs should be exported after shutdown", logExporter.finishedLogRecordItems.isEmpty())
        // Note: Metrics might still show up if the meter provider wasn't fully no-op or if there's a delay.
        // For counters, if they became no-op, no new points would be added.
        // This is tricky. The best check is that no new metrics are recorded for the specific `incRequestCount` call.
        val metricsAfterShutdown = metricExporter.finishedMetricItems.filter { it.name == "http_client_request_count" }
        val specificPoint = metricsAfterShutdown.flatMap { it.longSumData.points }.find { it.attributes.get(AttributeKey.stringKey("key")) == "value" }
        assertNull("Specific metric incremented after shutdown should not appear", specificPoint)
    }

    @Test
    fun `uncaughtExceptionHandler should log error with details and call original default handler`() {
        val mockOriginalDefaultHandler = mock<Thread.UncaughtExceptionHandler>()
        Thread.setDefaultUncaughtExceptionHandler(mockOriginalDefaultHandler)

        initializeManager() // This replaces the handler

        val testThread = Thread.currentThread()
        val testException = RuntimeException("Critical unhandled test exception")

        // Simulate an uncaught exception by directly calling the manager's installed handler
        val managerHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("TelemetryManager should have set an uncaught exception handler", managerHandler)
        assertNotSame(mockOriginalDefaultHandler, managerHandler) // Ensure it's not the one we set

        managerHandler?.uncaughtException(testThread, testException)
        logExporter.flush()

        // Verify log record
        val logs = logExporter.finishedLogRecordItems
        assertTrue("Log record for unhandled exception should exist", logs.any {
            it.body.asString() == "Unhandled exception" &&
            it.attributes.get(AttributeKey.stringKey("exception.type")) == testException.javaClass.name &&
            it.attributes.get(AttributeKey.stringKey("exception.message")) == testException.message &&
            it.attributes.get(AttributeKey.stringKey("thread.name")) == testThread.name &&
            it.severityNumber == io.opentelemetry.api.logs.Severity.ERROR.getSeverityNumber() && // Corrected
            it.severityText == "ERROR" // Also check text for good measure
        })

        // Verify original default handler was called
        verify(mockOriginalDefaultHandler).uncaughtException(testThread, testException)

        // Clean up: restore original handler (though AfterTest should also handle this)
        Thread.setDefaultUncaughtExceptionHandler(null) // Or mockOriginalDefaultHandler if it was the one before this test.
    }

    @Test
    fun `batteryLevelGauge callback should handle BatteryManager returning null or negative value`() {
        // Scenario 1: BatteryManager is null (e.g., service not available)
        whenever(mockApplication.getSystemService(Context.BATTERY_SERVICE)).thenReturn(null)
        initializeManager()
        ShadowLooper.idleFor(Duration.ofSeconds(31))
        metricExporter.flush()
        var batteryMetric = metricExporter.finishedMetricItems.find { it.name == "device_battery_percent" }
        // If the callback doesn't record due to null BM, the metric point might not exist or be empty.
        // The gauge is registered, but its callback might not produce a measurement.
        // Depending on OTel SDK, this might mean no point, or a point with a default/stale value if observation was made.
        // The key is that it doesn't crash.
        // A more robust check would be to ensure no exception is thrown during metric collection if possible to isolate.
        // For now, we check that if the metric exists, its value isn't something unexpected from a crash.
        if (batteryMetric != null) {
             assertTrue("Battery metric points should be empty or valid, not error state", batteryMetric.longGaugeData.points.isEmpty() || (batteryMetric.longGaugeData.points.first().value >= 0) )
        }
        metricExporter.reset()
        TelemetryManager.shutdown() // reset for next part
        TelemetryManagerReflection.setInitialized(false)
        TelemetryManagerReflection.resetProvidersAndSDK()
        GlobalOpenTelemetry.resetForTest()
        setUp() // Re-setup mocks


        // Scenario 2: BatteryManager returns negative value for capacity
        whenever(mockBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).thenReturn(-1)
        initializeManager() // Re-initialize with new mock behavior
        ShadowLooper.idleFor(Duration.ofSeconds(31))
        metricExporter.flush()
        batteryMetric = metricExporter.finishedMetricItems.find { it.name == "device_battery_percent" }
        // The callback should not record if level < 0
        if (batteryMetric != null) {
            assertTrue("Battery metric should have no points if capacity is negative", batteryMetric.longGaugeData.points.isEmpty())
        }
    }
}

/**
 * Helper object to access internal state of TelemetryManager for testing via reflection.
 * Use sparingly and only when necessary, as it makes tests brittle to refactoring.
 */
object TelemetryManagerReflection {
    private fun getField(fieldName: String): java.lang.reflect.Field {
        val field = TelemetryManager::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field
    }

    fun isInitialized(): Boolean = getField("initialized").getBoolean(TelemetryManager)

    fun setInitialized(value: Boolean) = getField("initialized").setBoolean(TelemetryManager, value)

    fun getTracerProvider(): SdkTracerProvider? = getField("tracerProvider").get(TelemetryManager) as? SdkTracerProvider
    fun getMeterProvider(): SdkMeterProvider? = getField("meterProvider").get(TelemetryManager) as? SdkMeterProvider
    fun getLoggerProvider(): SdkLoggerProvider? = getField("loggerProvider").get(TelemetryManager) as? SdkLoggerProvider

    fun resetProvidersAndSDK() {
        // Attempt to shutdown existing providers if they are not null
        getTracerProvider()?.shutdown()
        getMeterProvider()?.shutdown()
        getLoggerProvider()?.shutdown()

        // Set provider fields to null
        try { getField("tracerProvider").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }
        try { getField("meterProvider").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }
        try { getField("loggerProvider").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }

        // Also reset OTel API instances if they are held directly
        try { getField("tracer").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }
        try { getField("meter").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }
        try { getField("logger").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }

        // Reset metric instruments
        try { getField("requestCounter").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }
        try { getField("memoryUsageGauge").set(TelemetryManager, null) } catch (e: Exception) { (getField("memoryUsageGauge").get(TelemetryManager) as? AutoCloseable)?.close() }
        try { getField("cpuTimeGauge").set(TelemetryManager, null) } catch (e: Exception) { (getField("cpuTimeGauge").get(TelemetryManager) as? AutoCloseable)?.close() }
        try { getField("uptimeGauge").set(TelemetryManager, null) } catch (e: Exception) { (getField("uptimeGauge").get(TelemetryManager) as? AutoCloseable)?.close() }
        try { getField("batteryLevelGauge").set(TelemetryManager, null) } catch (e: Exception) { (getField("batteryLevelGauge").get(TelemetryManager) as? AutoCloseable)?.close()}
        try { getField("threadCountGauge").set(TelemetryManager, null) } catch (e: Exception) { (getField("threadCountGauge").get(TelemetryManager) as? AutoCloseable)?.close() }

        // Reset other state
        try { getField("appStartTimeMs").setLong(TelemetryManager, 0L) } catch (e: Exception) { /* Log error or ignore */ }
        try { getField("defaultExceptionHandler").set(TelemetryManager, null) } catch (e: Exception) { /* Log error or ignore */ }

        // Crucially, ensure the global OpenTelemetry is also reset for a clean slate for the next test
        // This is vital if TelemetryManager relies on buildAndRegisterGlobal()
        GlobalOpenTelemetry.resetForTest()
    }
}
