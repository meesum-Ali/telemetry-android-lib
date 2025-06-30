import java.io.FileInputStream
import java.util.Properties

// Load GitHub credentials from github.properties file
val githubProperties = Properties().apply {
    val githubPropertiesFile = rootProject.file("github.properties")
    if (githubPropertiesFile.exists()) {
        load(FileInputStream(githubPropertiesFile))
    }
}

// Function to get GitHub property with fallback to environment variables
fun getGithubProperty(key: String): String? {
    // Handle the case where 'gpr.usr' is used instead of 'gpr.user'
    val actualKey = if (key == "gpr.user") {
        githubProperties.getProperty("gpr.usr")?.let { return@getGithubProperty it }
        key
    } else {
        key
    }

    return githubProperties.getProperty(actualKey) ?:
           System.getenv(actualKey) ?:
           System.getenv(actualKey.uppercase())
}

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("maven-publish")
}

// Load version from version.properties file
val versionProps = Properties().apply {
    val versionPropsFile = rootProject.file("version.properties")
    if (versionPropsFile.exists()) {
        load(FileInputStream(versionPropsFile))
    }
}

val libraryVersion = versionProps["VERSION_NAME"]?.toString() ?: "1.0.0"

group = "io.github.meesum"
version = libraryVersion

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
    publications {
        create<MavenPublication>("release") {
            // Set artifact details
            groupId = "io.github.meesum"
            artifactId = "telemetry"
            version = libraryVersion

            // Publish the AAR
            afterEvaluate {
                from(components["release"])
            }

            // Configure POM for Maven Central requirements
            pom {
                name.set("Meesum Telemetry SDK")
                description.set("""
                    |# Telemetry Android Library
                    |
                    |A lightweight and efficient Android library for collecting, processing, and exporting telemetry data.
                    |
                    |## Features
                    |- Collect various types of telemetry data
                    |- Process and filter telemetry events
                    |- Export data to multiple destinations
                    |- Highly configurable and extensible
                    |
                    |## Requirements
                    |- Android 5.0 (API level 21) and above
                    |
                    |## Installation
                    |```gradle
                    |implementation 'io.github.meesum:telemetry:1.0.0'
                    |```
                    |
                    |## Documentation
                    |For more information, please visit the [GitHub repository](https://github.com/meesum-ali/telemetry-android-lib).
                """.trimMargin())
                url.set("https://github.com/meesum-ali/telemetry-android-lib")

                // License information
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("meesum-ali")
                        name.set("Meesum Ali")
                        email.set("meesumdex@gmail.com") // Update with your email
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/meesum-ali/telemetry-android-lib.git")
                    developerConnection.set("scm:git:ssh://github.com/meesum-ali/telemetry-android-lib.git")
                    url.set("https://github.com/meesum-ali/telemetry-android-lib")
                }
            }
        }
    }

    repositories {
        // Local Maven repository is configured by default
        mavenLocal()

        // GitHub Packages repository
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/meesum-ali/telemetry-android-lib")
            credentials {
                username = getGithubProperty("gpr.user") ?: getGithubProperty("github.actor")
                password = getGithubProperty("gpr.key") ?: getGithubProperty("github.token")
            }
        }
    }
}

// Task to publish to local Maven repository (~/.m2/repository)
tasks.register("publishLocal") {
    group = "publishing"
    description = "Publishes the release build to the local Maven repository"
    dependsOn("publishReleasePublicationToMavenLocal")
}

// Task to publish to GitHub Packages
tasks.register("publishGitHub") {
    group = "publishing"
    description = "Publishes the release build to GitHub Packages"

    val githubUser = getGithubProperty("gpr.user") ?: getGithubProperty("github.actor")
    val githubToken = getGithubProperty("gpr.key") ?: getGithubProperty("github.token")

    if (githubUser != null && githubToken != null) {
        dependsOn("publishReleasePublicationToGitHubPackagesRepository")
    } else {
        doFirst {
            throw GradleException("""
                GitHub Packages publishing failed: Missing GitHub credentials.
                Please ensure you have a github.properties file with:
                - gpr.user or github.actor
                - gpr.key or github.token
                
                Or set the corresponding environment variables.
            """.trimIndent())
        }
    }
}

// Task to publish to both local Maven and GitHub Packages
tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes the release build to both local Maven and GitHub Packages"
    dependsOn("publishLocal", "publishGitHub")
}

// Ensure we wait for the Android tasks to be ready
project.afterEvaluate {
    tasks.withType<AbstractPublishToMaven> {
        dependsOn("bundleReleaseAar")
    }
}
