package io.github.meesum.telemetry

/**
 * Configuration for telemetry initialization
 */
data class TelemetryConfig(
    // Core configuration
    val serviceName: String,
    val serviceVersion: String,
    val environment: String,
    val otlpEndpoint: String,
    val headers: Map<String, String> = emptyMap(),
    
    // Activity instrumentation
    val autoInstrumentActivities: Boolean = false,
    val enableFragmentTracing: Boolean = true,
    
    // Monitors configuration
    val enableAnrDetection: Boolean = true,
    val anrThresholdMs: Long = 5000,
    val enableSlowOperationDetection: Boolean = true,
    val slowOperationThresholdMs: Long = 100,
    val enableJankMonitoring: Boolean = true,
    val enableGcMonitoring: Boolean = true,
    
    // Collectors configuration
    val enableBatteryMetrics: Boolean = true,
    val enableMemoryMetrics: Boolean = true,
    val enableCpuMetrics: Boolean = true,
    val enableStorageMetrics: Boolean = true,
    val enableDisplayMetrics: Boolean = true,
    val enableSystemResourceMetrics: Boolean = true,
    val enableNetworkMetrics: Boolean = true,
    val enablePerformanceMetrics: Boolean = true,
    val enableAppStateMetrics: Boolean = true,
    
    // Exporters
    val traceExporterConfig: TelemetryExporterConfig? = null,
    val metricExporterConfig: TelemetryExporterConfig? = null,
    val logExporterConfig: TelemetryExporterConfig? = null
)


