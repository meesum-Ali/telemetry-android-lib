plugins {
    id("com.android.library")

    /* inherits Kotlin 2.0.0 from settings.gradle/pluginManagement */
    kotlin("android")
    kotlin("plugin.serialization")
    id("maven-publish")
}

/* ───────── Android ───────── */
android {
    namespace  = "com.bazaar.telemetry"
    compileSdk = 34

    defaultConfig { minSdk = 21 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

/* ───────── Deps ───────── */
dependencies {
    // keep every OTEL lib on the same version via the BOM
    api(platform("io.opentelemetry:opentelemetry-bom:1.49.0"))
    api("io.opentelemetry:opentelemetry-api")

    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")

    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.34.0")

    // single gRPC exporter → supplies Span/Metric/Log exporters
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("androidx.core:core-ktx:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("io.grpc:grpc-okhttp:1.63.0")
}

publishing {
    publications.create<MavenPublication>("release") {
        afterEvaluate { from(components["release"]) }   // publish the AAR
        groupId    = "com.bazaar.telemetry"
        artifactId = "telemetry"
        version    = "1.0.0"
    }
}
