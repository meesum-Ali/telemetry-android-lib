import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.bazaar.telemetry.Attributes
import com.bazaar.telemetry.TelemetryManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class TelemetryManagerTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        resetTelemetryManager()
    }

    @After
    fun tearDown() {
        TelemetryManager.shutdown()
        resetTelemetryManager()
    }

    @Test
    fun `init should set initialized true and be idempotent`() {
        TelemetryManager.init(
            application,
            serviceName = "TestService",
            serviceVersion = "1.0",
            environment = "test",
            otlpEndpoint = "http://localhost:4317",
            headers = emptyMap()
        )

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
        TelemetryManager.init(
            application,
            serviceName = "TestService",
            serviceVersion = "1.0",
            environment = "test",
            otlpEndpoint = "http://localhost:4317",
            headers = emptyMap()
        )
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
        TelemetryManager.init(
            application,
            serviceName = "TestService",
            serviceVersion = "1.0",
            environment = "test",
            otlpEndpoint = "http://localhost:4317",
            headers = emptyMap()
        )
        TelemetryManager.incRequestCount(2, Attributes.builder().put("path", "/foo").build())
    }

    private fun resetTelemetryManager() {
        val field = TelemetryManager::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.setBoolean(TelemetryManager, false)
    }
}
