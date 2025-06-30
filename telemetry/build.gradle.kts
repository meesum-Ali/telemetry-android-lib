plugins {
    id("com.android.library")

    /* inherits Kotlin 2.0.0 from settings.gradle/pluginManagement */
    kotlin("android")
    kotlin("plugin.serialization")
    id("maven-publish")
}

/* ───────── Android ───────── */
android {
    namespace  = "io.github.meesum.telemetry" // Renamed namespace
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
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.49.0"))
    implementation("io.opentelemetry:opentelemetry-api")

    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")

    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.34.0")

    // single gRPC exporter → supplies Span/Metric/Log exporters
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("androidx.core:core-ktx:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("io.grpc:grpc-okhttp:1.63.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0") // For Kotlin-specific syntax
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.49.0") // For testing OpenTelemetry
}

publishing {
    publications.create<MavenPublication>("release") {
        afterEvaluate { from(components["release"]) }   // publish the AAR
        groupId    = "io.github.meesum.telemetry" // Renamed groupId
        artifactId = "telemetry" // ArtifactId can remain 'telemetry' or be 'meesum-telemetry'
        version    = "1.0.0" // TODO: Consider moving version to gradle.properties

        // It's good practice to add POM details even for GitHub Packages / Maven Local
        // This was part of the previous plan, but still relevant.
        pom {
            name.set("Meesum Telemetry SDK") // Renamed
            description.set("An Android SDK for collecting and exporting telemetry data.") // TODO: Update description if needed
            // TODO: Add url, licenses, developers, scm for a more complete POM
            // For example:
            // url.set("https://github.com/meesum/telemetry-android-lib") // Replace with actual URL
            // licenses {
            //     license {
            //         name.set("The Apache License, Version 2.0")
            //         url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            //     }
            // }
            // developers {
            //     developer {
            //         id.set("meesum") // Replace with your GitHub username or org ID
            //         name.set("Meesum") // Replace with your name or org name
            //         email.set("meesum@example.com") // Replace with your email
            //     }
            // }
            // scm {
            //     connection.set("scm:git:git://github.com/meesum/telemetry-android-lib.git") // Replace
            //     developerConnection.set("scm:git:ssh://github.com/meesum/telemetry-android-lib.git") // Replace
            //     url.set("https://github.com/meesum/telemetry-android-lib") // Replace
            // }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME_OR_ORG/YOUR_REPOSITORY_NAME") // TODO: Replace with your GitHub username/org and repository name
            credentials {
                // Credentials are username/password based.
                // It's recommended to use environment variables for these, especially GITHUB_TOKEN for the password.
                // For local publishing, you might set them in ~/.gradle/gradle.properties
                // username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                // password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String?
                //
                // For GitHub Actions, GITHUB_ACTOR and GITHUB_TOKEN are automatically available.
                // Ensure your GITHUB_TOKEN has `write:packages` scope.
                // If you have them in gradle.properties:
                // username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                // password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                // For this setup, we'll assume environment variables or that the user will uncomment and configure as needed.
                // Example using environment variables (preferred for CI like GitHub Actions):
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
