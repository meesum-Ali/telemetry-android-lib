plugins {
    id("com.android.library")

    /* inherits Kotlin 2.0.0 from settings.gradle/pluginManagement */
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka") // For KDoc/Javadoc generation
    id("maven-publish")
    signing // For GPG signing artifacts
    id("io.github.gradle-nexus.publish-plugin") // For simplifying Sonatype publishing
    `java-library` // To help with sourcesJar and javadocJar
}

/* ───────── Android ───────── */
android {
    namespace  = "io.github.meesum.telemetry" // Changed to reflect new groupId
    compileSdk = 34

    defaultConfig { minSdk = 21 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar() // This will be configured via Dokka
        }
    }
}

// Task to generate Javadoc using Dokka for the release build and package it as a JAR
val dokkaJavadoc by tasks.registering(org.jetbrains.dokka.gradle.DokkaTask::class) {
    moduleName.set(project.name)
    outputDirectory.set(file("$buildDir/dokkaJavadoc"))
    dokkaSourceSets {
        named("main") {
            // Configure source set specifics if needed
            // Dokka should pick up sources from the Android plugin by default for the 'main' source set
            // Ensure this captures sources for the 'release' variant.
            // May need to explicitly point to android.sourceSets.main.java.srcDirs
        }
    }
}

val androidJavadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.flatMap { it.outputDirectory })
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

// Placeholder values -
// TODO: It's highly recommended to move these to gradle.properties or define them centrally
// especially groupId and version.
// Changed groupId to reflect "meesum"
val libraryGroupId = "io.github.meesum.telemetry" // TODO: Replace with your actual groupId registered with Sonatype (e.g., com.meesum.telemetry if you own meesum.com)
val libraryArtifactId = "telemetry" // ArtifactId remains "telemetry" under the new group
val libraryVersion = "1.0.0" // TODO: Replace with your actual version (e.g., read from gradle.properties)

val libraryName = "Meesum Telemetry SDK" // TODO: Replace with your library's name
val libraryDescription = "An Android SDK for collecting and exporting telemetry data." // TODO: Replace with your library's description
val siteUrl = "https://github.com/your-org/your-repo" // TODO: Replace with your project's website or SCM URL
val gitUrl = "https://github.com/your-org/your-repo.git" // TODO: Replace with your Git repository URL

val licenseName = "The Apache License, Version 2.0" // TODO: Replace with your license
val licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"

val developerId = "your-username" // TODO: Replace with your developer ID (e.g., GitHub username)
val developerName = "Your Name or Company" // TODO: Replace
val developerEmail = "you@example.com" // TODO: Replace

publishing {
    publications {
        create<MavenPublication>("release") {
            // Use the task that generates Dokka Javadoc for Android
            artifact(tasks.named("androidJavadocJar")) {
                classifier = "javadoc"
            }
            // Sources jar should be included by withSourcesJar() in android.publishing block

            afterEvaluate { from(components["release"]) } // publish the AAR from the release build variant

            groupId = libraryGroupId
            artifactId = libraryArtifactId
            version = libraryVersion

            pom {
                name.set(libraryName)
                description.set(libraryDescription)
                url.set(siteUrl)

                licenses {
                    license {
                        name.set(licenseName)
                        url.set(licenseUrl)
                    }
                }
                developers {
                    developer {
                        id.set(developerId)
                        name.set(developerName)
                        email.set(developerEmail)
                    }
                }
                scm {
                    connection.set("scm:git:$gitUrl")
                    developerConnection.set("scm:git:ssh:${gitUrl.substringAfter("https://")}") // ssh variant
                    url.set(siteUrl)
                }
            }
        }
    }
}

// Configure signing of publications
// Credentials for signing will be typically stored in ~/.gradle/gradle.properties
// e.g., signing.keyId, signing.password, signing.secretKeyRingFile
signing {
    sign(publishing.publications["release"])
}

// Configure Nexus Publishing (Sonatype OSSRH)
// Credentials for Sonatype will be typically stored in ~/.gradle/gradle.properties
// e.g., sonatypeUsername, sonatypePassword
nexusPublishing {
    repositories {
        sonatype { // Name of the repository configuration
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/")) // Sonatype v2 URL
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")) // Sonatype v2 snapshot URL
            // username and password will be taken from gradle properties: sonatypeUsername, sonatypePassword
        }
    }
}
