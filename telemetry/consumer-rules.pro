# Keep all public API classes and methods
-keep class io.github.meesum.telemetry.** { *; }

# Keep OpenTelemetry classes that are used in the public API
-keep class io.opentelemetry.api.** { *; }
-keep class io.opentelemetry.sdk.** { *; }
-keep class io.opentelemetry.semconv.** { *; }
-keep class io.opentelemetry.exporter.** { *; }

# Keep Kotlin serialization classes
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** {
    *;
}

# Keep OkHttp classes used by the interceptor
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep gRPC classes
-keep class io.grpc.** { *; }
-keep interface io.grpc.** { *; }

# Keep AndroidX classes used
-keep class androidx.fragment.** { *; }
-keep class androidx.core.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep reflection-based serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Keep serialization metadata
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep OpenTelemetry resource attributes
-keep class io.opentelemetry.semconv.ServiceAttributes { *; }

# Keep all enum classes used in the API
-keep enum io.github.meesum.telemetry.TelemetryService$LogLevel { *; }
-keep enum io.github.meesum.telemetry.TelemetryExporterConfig$ExporterType { *; }

# Keep data classes
-keep class io.github.meesum.telemetry.TelemetryExporterConfig {
    *;
}

# Keep all methods that might be called via reflection
-keepclassmembers class * {
    @kotlinx.serialization.* *;
}

# Keep OpenTelemetry context propagation
-keep class io.opentelemetry.context.** { *; }

# Keep all classes that implement interfaces from the library
-keep class * implements io.github.meesum.telemetry.TelemetryService {
    *;
}

# Keep all classes that extend library classes
-keep class * extends io.github.meesum.telemetry.** {
    *;
}
