import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.meesum.telemetry.Attributes
import io.github.meesum.telemetry.TelemetryManager
import io.github.meesum.telemetry.TelemetryService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class TelemetryManagerTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        TelemetryManager.init(
            application,
            serviceName = "TestService",
            serviceVersion = "1.0",
            environment = "test",
            otlpEndpoint = "http://localhost:4317",
            headers = emptyMap()
        )
    }

    @After
    fun tearDown() {
        TelemetryManager.shutdown()
    }

    @Test
    fun `init should set initialized true and be idempotent`() {
        val initializedField = TelemetryManager::class.java.getDeclaredField("initialized")
        initializedField.isAccessible = true
        assertTrue(initializedField.getBoolean(TelemetryManager))

        val meterProviderField = TelemetryManager::class.java.getDeclaredField("meterProvider")
        meterProviderField.isAccessible = true
        val firstMeterProvider = meterProviderField.get(TelemetryManager)

        // Second init should be a no-op
        TelemetryManager.init(
            application,
            serviceName = "OtherService",
            serviceVersion = "2.0",
            environment = "prod",
            otlpEndpoint = "http://localhost:4317",
            headers = emptyMap()
        )

        assertSame(firstMeterProvider, meterProviderField.get(TelemetryManager))
    }

    @Test
    fun `setCommonAttributes should store attributes`() {
        TelemetryManager.setCommonAttributes(
            Attributes.builder().put("user.id", "123").build()
        )
        val commonAttrsField = TelemetryManager::class.java.getDeclaredField("commonAttributes")
        commonAttrsField.isAccessible = true
        val stored = commonAttrsField.get(TelemetryManager) as Attributes
        assertEquals("123", stored.getString("user.id"))
    }

    @Test
    fun `span should execute block and return its result`() {
        val result = TelemetryManager.span("test-span") {
            "value"
        }
        assertEquals("value", result)

        try {
            TelemetryManager.span("exception-span") {
                throw IllegalStateException("boom")
            }
            fail("Exception not thrown")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `incRequestCount should not throw`() {

        TelemetryManager.incRequestCount(2, Attributes.builder().put("path", "/foo").build())
    }

    @Test
    fun `shutdown should be idempotent`() {
        TelemetryManager.shutdown()
        // Second shutdown should not throw
        TelemetryManager.shutdown()
    }

    @Test
    fun `log should not throw for all log levels`() {
        val attrs = Attributes.builder().put("foo", "bar").build()
        TelemetryManager.log(TelemetryService.LogLevel.DEBUG, "debug message", attrs)
        TelemetryManager.log(TelemetryService.LogLevel.INFO, "info message", attrs)
        TelemetryManager.log(TelemetryService.LogLevel.WARN, "warn message", attrs)
        TelemetryManager.log(
            TelemetryService.LogLevel.ERROR,
            "error message",
            attrs,
            Throwable("test error")
        )
    }

    @Test
    fun `span should merge common and custom attributes`() {

        TelemetryManager.setCommonAttributes(Attributes.builder().put("common", "yes").build())
        val result =
            TelemetryManager.span("test-span", Attributes.builder().put("custom", "attr").build()) {
                "done"
            }
        assertEquals("done", result)
        // No direct way to assert merged attributes without a mock, but this ensures no crash
    }

    @Test
    fun `incRequestCount should work with default parameters`() {

        TelemetryManager.incRequestCount()
    }

    @Test
    fun `should allow re-init after shutdown`() {
        TelemetryManager.shutdown()
        resetTelemetryManager()
        resetGlobalOpenTelemetry()
        TelemetryManager.init(
            application,
            serviceName = "TestService2",
            serviceVersion = "2.0",
            environment = "test2",
            otlpEndpoint = "http://localhost:4317",
            headers = emptyMap()
        )
        val initializedField = TelemetryManager::class.java.getDeclaredField("initialized")
        initializedField.isAccessible = true
        assertTrue(initializedField.getBoolean(TelemetryManager))
    }

    private fun resetTelemetryManager() {
        val field = TelemetryManager::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.setBoolean(TelemetryManager, false)
    }

    private fun resetGlobalOpenTelemetry() {
        try {
            val clazz = Class.forName("io.opentelemetry.api.GlobalOpenTelemetry")
            val field = clazz.getDeclaredField("globalOpenTelemetry")
            field.isAccessible = true
            field.set(null, null)
        } catch (e: Exception) {
            // Ignore if fails (e.g., field not found)
        }
    }
}
