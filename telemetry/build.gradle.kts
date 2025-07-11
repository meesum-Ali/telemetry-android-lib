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

apply(from = "publish.gradle")

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

// Task to publish to local Maven repository (~/.m2/repository)
tasks.register("publishLocal") {
    group = "publishing"
    description = "Publishes the release build to the local Maven repository"
    dependsOn("publishReleasePublicationToMavenLocal")
}

// Task to publish to GitHub Packages
tasks.register("publishGitHub") {
    group = "publishing"
    description = "Publishes the release and debug builds to GitHub Packages (via publish.gradle)"

    // These tasks are created by publish.gradle (Groovy)
    val releaseTask = tasks.findByName("publishReleasePublicationToGitHubPackagesRepository")
    val debugTask = tasks.findByName("publishDebugPublicationToGitHubPackagesRepository")

    doFirst {
        if (releaseTask == null && debugTask == null) {
            throw GradleException("No publish tasks found. Ensure publish.gradle is applied and publication tasks are available.")
        }
    }
    if (releaseTask != null) dependsOn(releaseTask)
    if (debugTask != null) dependsOn(debugTask)
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
