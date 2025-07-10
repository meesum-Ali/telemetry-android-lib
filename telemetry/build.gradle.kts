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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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
}

/* ───────── Deps ───────── */
dependencies {
    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.semconv)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.grpc.okhttp)
    implementation(libs.okhttp)
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

android { // Add this block to configure Robolectric
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
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
